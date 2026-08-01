# Standalone Build

This project can be built and run without Docker.

## Requirements

- Java 17 JDK
- Maven 3.9+
- `curl`
- `jq` for the sample JSON payload in the render command

## Build

From the repository root:

```bash
mvn clean package -DskipTests
```

This produces `target/jasper-reporter-1.0.0.jar`.

## Run

Start the service from the host:

```bash
PORT=8081 REPORTS_DIR=./reports java -jar target/jasper-reporter-1.0.0.jar
```

`REPORTS_DIR` must point at the local `reports/` directory so the service can find JRXML files, subreports, and images.

## Test

Compile the sample report:

```bash
curl -sS -X POST http://localhost:8081/compile \
  -H 'Content-Type: application/json' \
  -d '{"source":"timesheet.jrxml","target":"timesheet.jasper","force":true}'
```

Render the compiled report to PDF:

```bash
curl -sS -X POST http://localhost:8081/render \
  -H 'Content-Type: application/json' \
  -d "$(jq -nc --slurpfile input reports/input.json '{report:\"timesheet.jasper\",format:\"pdf\",params:{json:($input[0]|tojson)},datasource:{type:\"none\"}}')" \
  -o timesheet.pdf
```

## Notes

- The app entry point is `src/main/java/com/alexbarylski/jasperreporter/JasperReporterApp.java`.
- The reusable build/render logic lives in `src/main/java/com/alexbarylski/jasperreporter/services/`.
- If `java` or `mvn` is not found on the host, install a local JDK/Maven before using the standalone commands.
