package com.alexbarylski.jasperreporter.services;

import com.alexbarylski.jasperreporter.models.RenderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;
import net.sf.jasperreports.export.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.*;

public class RenderService {

    private static final Logger log = LoggerFactory.getLogger(RenderService.class);

    private static final Set<String> SUPPORTED_FORMATS =
            Set.of("pdf", "xlsx", "csv", "html", "ods", "docx", "rtf", "xml");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf",  "application/pdf",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "csv",  "text/csv",
            "html", "text/html",
            "ods",  "application/vnd.oasis.opendocument.spreadsheet",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "rtf",  "application/rtf",
            "xml",  "application/xml"
    );

    private final CacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RenderService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Validate, fill, and export a compiled .jasper report.
     *
     * @param request render parameters
     * @return rendered binary output
     * @throws Exception on validation or rendering failure
     */
    public byte[] render(RenderRequest request) throws Exception {
        validateRequest(request);

        Path reportFile = CompileService.resolveAndValidate(request.getReport());

        if (!Files.exists(reportFile)) {
            throw new IOException("Report file not found: " + request.getReport());
        }

        String format = request.getFormat().toLowerCase(Locale.ROOT);

        if (request.isDryRun()) {
            log.info("Dry-run: validation passed for report={}, format={}", request.getReport(), format);
            return new byte[0];
        }

        // Load from cache or disk
        JasperReport jasperReport = cacheService.get(request.getReport());
        if (jasperReport == null) {
            log.debug("Cache miss for '{}', loading from disk", request.getReport());
            jasperReport = (JasperReport) JRLoader.loadObject(reportFile.toFile());
        }

        // Build fill parameters
        Map<String, Object> params = buildParameters(request, reportFile);

        // Fill report
        JasperPrint jasperPrint = fillReport(jasperReport, request, params);

        // Export
        return export(jasperPrint, format);
    }

    /** Return the MIME content-type for a given format string (lower-case). */
    public String contentTypeFor(String format) {
        return CONTENT_TYPES.getOrDefault(format.toLowerCase(Locale.ROOT), "application/octet-stream");
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void validateRequest(RenderRequest request) throws IOException {
        if (request.getReport() == null || request.getReport().isBlank()) {
            throw new IOException("'report' field is required");
        }
        if (!request.getReport().endsWith(".jasper")) {
            throw new IOException("Report must have a .jasper extension");
        }
        if (request.getFormat() == null || request.getFormat().isBlank()) {
            throw new IOException("'format' field is required");
        }
        String fmt = request.getFormat().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(fmt)) {
            throw new IOException("Unsupported format '" + request.getFormat()
                    + "'. Supported: " + SUPPORTED_FORMATS);
        }
    }

    private Map<String, Object> buildParameters(RenderRequest request, Path reportFile) {
        Map<String, Object> params = new HashMap<>();

        // Set SUBREPORT_DIR to the parent directory of the main report so that
        // relative subreport references work without additional configuration.
        String subreportDir = reportFile.getParent().toAbsolutePath() + File.separator;
        params.put("SUBREPORT_DIR", subreportDir);

        if (request.getParams() != null) {
            params.putAll(request.getParams());
        }
        return params;
    }

    private JasperPrint fillReport(JasperReport jasperReport,
                                   RenderRequest request,
                                   Map<String, Object> params) throws Exception {
        RenderRequest.Datasource ds = request.getDatasource();

        if (ds == null || "none".equalsIgnoreCase(ds.getType())) {
            return JasperFillManager.fillReport(jasperReport, params, new JREmptyDataSource());
        }

        switch (ds.getType().toLowerCase(Locale.ROOT)) {
            case "json" -> {
                JRDataSource dataSource = buildJsonDataSource(ds.getData());
                return JasperFillManager.fillReport(jasperReport, params, dataSource);
            }
            case "jdbc" -> {
                Connection conn = buildJdbcConnection(ds);
                try {
                    return JasperFillManager.fillReport(jasperReport, params, conn);
                } finally {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                }
            }
            case "mixed" -> {
                // Mixed: use JDBC connection as the primary datasource while passing
                // any JSON data through a reserved parameter for subreports.
                if (ds.getData() != null) {
                    String jsonStr = objectMapper.writeValueAsString(ds.getData());
                    params.put("JSON_DATA", jsonStr);
                }
                Connection conn = buildJdbcConnection(ds);
                try {
                    return JasperFillManager.fillReport(jasperReport, params, conn);
                } finally {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                }
            }
            default -> throw new IOException("Unknown datasource type: " + ds.getType());
        }
    }

    private JRDataSource buildJsonDataSource(Object data) throws Exception {
        if (data == null) {
            return new JREmptyDataSource();
        }
        if (data instanceof List<?> list) {
            // Fast path: array of objects → JRMapCollectionDataSource
            @SuppressWarnings("unchecked")
            List<Map<String, ?>> maps = (List<Map<String, ?>>) list;
            return new JRMapCollectionDataSource(maps);
        }
        // General path: serialize to JSON and use JsonDataSource
        String jsonStr = objectMapper.writeValueAsString(data);
        return new JsonDataSource(new ByteArrayInputStream(jsonStr.getBytes(StandardCharsets.UTF_8)));
    }

    private Connection buildJdbcConnection(RenderRequest.Datasource ds) throws Exception {
        RenderRequest.JdbcConfig jdbc = ds.getJdbc();
        if (jdbc == null || jdbc.getUrl() == null || jdbc.getUrl().isBlank()) {
            throw new IOException("JDBC config (url) is required for datasource type jdbc/mixed");
        }
        if (jdbc.getDriver() != null && !jdbc.getDriver().isBlank()) {
            Class.forName(jdbc.getDriver());
        }
        return DriverManager.getConnection(jdbc.getUrl(), jdbc.getUsername(), jdbc.getPassword());
    }

    private byte[] export(JasperPrint jasperPrint, String format) throws JRException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        switch (format) {
            case "pdf" -> {
                JRPdfExporter exporter = new JRPdfExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
                exporter.exportReport();
            }
            case "xlsx" -> {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
                exporter.exportReport();
            }
            case "csv" -> {
                JRCsvExporter exporter = new JRCsvExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(out));
                exporter.exportReport();
            }
            case "html" -> {
                HtmlExporter exporter = new HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleHtmlExporterOutput(out));
                exporter.exportReport();
            }
            case "ods" -> {
                JROdsExporter exporter = new JROdsExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
                exporter.exportReport();
            }
            case "docx" -> {
                JRDocxExporter exporter = new JRDocxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
                exporter.exportReport();
            }
            case "rtf" -> {
                JRRtfExporter exporter = new JRRtfExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(out));
                exporter.exportReport();
            }
            case "xml" -> {
                JRXmlExporter exporter = new JRXmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleXmlExporterOutput(out));
                exporter.exportReport();
            }
            default -> throw new JRException("Unsupported export format: " + format);
        }
        return out.toByteArray();
    }
}
