package com.alexbarylski.jasperreporter.services;

import com.alexbarylski.jasperreporter.models.CompileRequest;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CompileService {

    private static final Logger log = LoggerFactory.getLogger(CompileService.class);
    private static final String REPORTS_DIR = CacheService.REPORTS_DIR;

    /**
     * Validate and optionally compile a JRXML source into a .jasper target.
     *
     * @param request compile parameters
     * @throws Exception on validation or compilation failure
     */
    public void compile(CompileRequest request) throws Exception {
        validateRequest(request);

        Path sourceFile = resolveAndValidate(request.getSource());
        Path targetFile = resolveAndValidate(request.getTarget());

        // Validate source exists
        if (!Files.exists(sourceFile)) {
            throw new IOException("Source file not found: " + request.getSource());
        }
        if (!request.getSource().endsWith(".jrxml")) {
            throw new IOException("Source must have a .jrxml extension");
        }

        // Validate target extension
        if (!request.getTarget().endsWith(".jasper")) {
            throw new IOException("Target must have a .jasper extension");
        }

        // Validate target parent directory exists
        Path targetParent = targetFile.getParent();
        if (targetParent != null && !Files.exists(targetParent)) {
            throw new IOException("Target directory does not exist: " + targetParent);
        }

        // Check force flag
        if (!request.isForce() && Files.exists(targetFile)) {
            throw new IOException(
                    "Target file already exists: " + request.getTarget() + ". Set force=true to overwrite.");
        }

        if (request.isDryRun()) {
            log.info("Dry-run: validation passed for source={}", request.getSource());
            return;
        }

        log.info("Compiling {} -> {}", request.getSource(), request.getTarget());
        JasperCompileManager.compileReportToFile(
                sourceFile.toAbsolutePath().toString(),
                targetFile.toAbsolutePath().toString());
        log.info("Compilation successful: {}", request.getTarget());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void validateRequest(CompileRequest request) throws IOException {
        if (request.getSource() == null || request.getSource().isBlank()) {
            throw new IOException("'source' field is required");
        }
        if (request.getTarget() == null || request.getTarget().isBlank()) {
            throw new IOException("'target' field is required");
        }
    }

    static Path resolveAndValidate(String relativePath) throws IOException {
        Path reportsDir = Paths.get(REPORTS_DIR).toAbsolutePath().normalize();
        Path resolved = reportsDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(reportsDir)) {
            throw new IOException("Path traversal detected: " + relativePath);
        }
        return resolved;
    }
}
