package com.alexbarylski.jasperreporter.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class RenderRequest {

    private String report;
    private String format;
    private Map<String, Object> params;
    private Datasource datasource;

    @JsonProperty("dry_run")
    private boolean dryRun = false;

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Datasource getDatasource() {
        return datasource;
    }

    public void setDatasource(Datasource datasource) {
        this.datasource = datasource;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    // -----------------------------------------------------------------------
    // Nested types
    // -----------------------------------------------------------------------

    public static class Datasource {

        /** json | jdbc | mixed | none */
        private String type;

        /** Raw data passed to the JSON-style datasource when type = json or mixed */
        private Object data;

        /** JDBC connection config when type = jdbc or mixed */
        private JdbcConfig jdbc;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public JdbcConfig getJdbc() {
            return jdbc;
        }

        public void setJdbc(JdbcConfig jdbc) {
            this.jdbc = jdbc;
        }
    }

    public static class JdbcConfig {

        private String driver;
        private String url;
        private String username;
        private String password;

        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
