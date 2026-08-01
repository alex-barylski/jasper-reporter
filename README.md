# Jasper Reporter

A lightweight **Java 17 / Javalin** microservice that exposes [JasperReports](https://community.jaspersoft.com/project/jasperreports-library) functionality over a minimal HTTP API.

Designed to run as a **sidecar container** alongside any PHP application, sharing a `/reports` volume for JRXML and JASPER files.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [API Endpoints](#api-endpoints)
   - [POST /compile](#post-compile)
   - [POST /render](#post-render)
   - [GET /list](#get-list)
3. [Error Envelope Format](#error-envelope-format)
4. [Dockerfile Instructions](#dockerfile-instructions)
5. [Docker Compose Example](#docker-compose-example)
6. [PHP Usage Examples](#php-usage-examples)
7. [Configuration](#configuration)
8. [Security Notes](#security-notes)

---

## Project Overview

| Property     | Value                            |
|--------------|----------------------------------|
| Language     | Java 17                          |
| Framework    | Javalin 6                        |
| Reports lib  | JasperReports 6.21.3             |
| Default port | 8080 (override with `PORT` env)  |
| Reports dir  | `/reports` (override with `REPORTS_DIR` env) |

### Core goals

- Minimal three-endpoint HTTP API  
- Fast JVM warm state with optional in-memory report cache  
- No JasperStarter or JasperStudio dependency  
- Version-consistent JRXML compilation  
- JSON, JDBC, mixed, and parameter-only datasource support  
- Clean, consistent JSON error envelopes  
- Path-traversal protection on all file operations  

---

## API Endpoints

### POST /compile

Compile a single `.jrxml` file into a `.jasper` file.

#### Request

```json
{
  "source":  "invoice/invoice.jrxml",
  "target":  "invoice/invoice.jasper",
  "force":   false,
  "dry_run": false
}
```

| Field     | Type    | Required | Default | Description                                                  |
|-----------|---------|----------|---------|--------------------------------------------------------------|
| `source`  | string  | yes      | —       | Path relative to `/reports`. Must end in `.jrxml`.           |
| `target`  | string  | yes      | —       | Path relative to `/reports`. Must end in `.jasper`.          |
| `force`   | boolean | no       | `false` | Overwrite target if it already exists.                       |
| `dry_run` | boolean | no       | `false` | Validate inputs only; do not write any files.                |

#### Success response

```json
{
  "success": true,
  "source":  "invoice/invoice.jrxml",
  "target":  "invoice/invoice.jasper"
}
```

#### Error response

```json
{
  "success": false,
  "error":   "Compilation failed",
  "details": "JRException: … stack trace …"
}
```

---

### POST /render

Render a `.jasper` file and return the binary output.

#### Request

```json
{
  "report":  "invoice/invoice.jasper",
  "format":  "pdf",
  "params": {
    "invoice_id": 1234
  },
  "datasource": {
    "type": "json",
    "data": {
      "items": [
        { "sku": "ABC", "qty": 2 },
        { "sku": "XYZ", "qty": 1 }
      ]
    }
  },
  "dry_run": false
}
```

| Field        | Type    | Required | Default | Description                                                  |
|--------------|---------|----------|---------|--------------------------------------------------------------|
| `report`     | string  | yes      | —       | Path relative to `/reports`. Must end in `.jasper`.          |
| `format`     | string  | yes      | —       | One of `pdf`, `xlsx`, `csv`, `html`, `ods`, `docx`, `rtf`, `xml`. |
| `params`     | object  | no       | `{}`    | Key/value map passed as JasperReports parameters.            |
| `datasource` | object  | no       | `null`  | See datasource config below.                                 |
| `dry_run`    | boolean | no       | `false` | Validate inputs only; do not render.                         |

#### Datasource config

| Field         | Description                                          |
|---------------|------------------------------------------------------|
| `type`        | `json` \| `jdbc` \| `mixed` \| `none`               |
| `data`        | Raw JSON (object or array) used for `json`/`mixed`.  |
| `jdbc.driver` | Fully-qualified JDBC driver class name (optional).   |
| `jdbc.url`    | JDBC URL, e.g. `jdbc:postgresql://host/db`.          |
| `jdbc.username` | Database username.                                 |
| `jdbc.password` | Database password.                                 |

**Datasource types:**

- **`json`** — data object/array is passed to `JsonDataSource`; the JRXML query string navigates the JSON path.  
- **`jdbc`** — a live JDBC connection is opened and passed to `JasperFillManager`. The JDBC driver jar must be present on the classpath (mount it into the container).  
- **`mixed`** — JDBC connection is used as the primary datasource; JSON data is also available as the `JSON_DATA` string parameter for subreports.  
- **`none`** — `JREmptyDataSource` is used; report relies entirely on `params`.

A `SUBREPORT_DIR` parameter is automatically set to the parent directory of the main report so relative subreport references resolve without extra configuration.

#### Success response

Binary file bytes with the appropriate `Content-Type` header.  
On `dry_run=true`, a JSON acknowledgement is returned instead:

```json
{
  "success": true,
  "report":  "invoice/invoice.jasper",
  "format":  "pdf"
}
```

#### Error response

```json
{
  "success": false,
  "error":   "Render failed",
  "details": "JRException: … stack trace …"
}
```

---

### GET /list

List all `.jrxml` and `.jasper` files under `/reports`, sorted alphabetically.

#### Success response

```json
{
  "success": true,
  "files": [
    "invoice/invoice.jasper",
    "invoice/invoice.jrxml",
    "invoice/subreports/items.jasper",
    "invoice/subreports/items.jrxml"
  ]
}
```

#### Error response

```json
{
  "success": false,
  "error":   "Unable to scan reports directory",
  "details": "IOException: …"
}
```

---

## Error Envelope Format

All error responses share this structure:

```json
{
  "success": false,
  "error":   "<short human-readable message>",
  "details": "<exception message or stack trace>"
}
```

HTTP status codes:

| Status | Meaning                                        |
|--------|------------------------------------------------|
| `200`  | Success                                        |
| `400`  | Bad request / validation error                 |
| `500`  | Server error (compilation failed, IO error, …) |

---

## Dockerfile Instructions

The project ships with a two-stage `Dockerfile`:

```
Stage 1 (builder)  maven:3.9-eclipse-temurin-17
Stage 2 (runtime)  eclipse-temurin:17-jre
```

### Build the image

```bash
docker build -t jasper-reporter:latest .
```

### Run locally

```bash
docker run --rm \
  -p 8080:8080 \
  -v "$(pwd)/reports:/reports" \
  jasper-reporter:latest
```

### Environment variables

| Variable      | Default    | Description                    |
|---------------|------------|--------------------------------|
| `PORT`        | `8080`     | HTTP port the server listens on |
| `REPORTS_DIR` | `/reports` | Root directory for report files |

---

## Docker Compose Example

```yaml
services:
  app:
    build: .
    networks: [internal]
    volumes:
      - ./reports:/reports

  jasper:
    image: ghcr.io/alex-barylski/jasper-reporter:latest
    networks: [internal]
    expose:
      - "8080"
    volumes:
      - ./reports:/reports

networks:
  internal:
    driver: bridge
```

> **Note:** `expose` makes port 8080 available only within the `internal` network.  
> The PHP `app` service calls `http://jasper:8080/...` — Jasper Reporter is never exposed to the public internet.

---

## PHP Usage Examples

All examples use the [Symfony HttpClient](https://symfony.com/doc/current/http_client.html).

### Compile a report

```php
use Symfony\Component\HttpClient\HttpClient;

$client = HttpClient::create();

$response = $client->request('POST', 'http://jasper:8080/compile', [
    'json' => [
        'source' => 'invoice/invoice.jrxml',
        'target' => 'invoice/invoice.jasper',
        'force'  => true,
    ],
]);

$data = $response->toArray();
// $data['success'] === true
```

### Render a report to PDF

```php
$response = $client->request('POST', 'http://jasper:8080/render', [
    'json' => [
        'report'     => 'invoice/invoice.jasper',
        'format'     => 'pdf',
        'params'     => ['invoice_id' => 1234],
        'datasource' => ['type' => 'jdbc'],
    ],
]);

$pdfBytes = $response->getContent();
file_put_contents('/tmp/invoice.pdf', $pdfBytes);
```

### Render with inline JSON data

```php
$response = $client->request('POST', 'http://jasper:8080/render', [
    'json' => [
        'report'  => 'invoice/invoice.jasper',
        'format'  => 'pdf',
        'datasource' => [
            'type' => 'json',
            'data' => [
                'items' => [
                    ['sku' => 'ABC', 'qty' => 2],
                    ['sku' => 'XYZ', 'qty' => 1],
                ],
            ],
        ],
    ],
]);
```

### List available reports

```php
$response = $client->request('GET', 'http://jasper:8080/list');
$files = $response->toArray()['files'];
// ['invoice/invoice.jasper', 'invoice/invoice.jrxml', …]
```

---

## Configuration

### Custom JDBC driver

Mount your JDBC driver jar into the container and override the entrypoint to add it to the classpath:

```yaml
jasper:
  image: jasper-reporter:latest
  volumes:
    - ./reports:/reports
    - ./lib/postgresql-42.7.3.jar:/app/lib/postgresql.jar
  entrypoint: ["java", "-cp", "app.jar:/app/lib/*", "com.alexbarylski.jasperreporter.JasperReporterApp"]
```

### Report caching

On startup the service automatically scans `/reports` and pre-loads every `.jasper` file into an in-memory `ConcurrentHashMap`. Subsequent render calls hit the cache instead of disk. Restarting the container (or calling `/compile` with `force=true`) refreshes stale entries.

---

## Security Notes

- **Path traversal protection** — all file paths are resolved relative to `REPORTS_DIR` and validated to remain within that directory. Any attempt to escape (e.g. `../../etc/passwd`) results in a 400 error.
- **Internal-only networking** — expose Jasper Reporter only within a private Docker network; never bind port 8080 directly to the host in production.
- **No authentication** — the service has no built-in authentication. Do not expose it on a public interface without adding an authentication layer (reverse proxy with mTLS, API gateway, etc.). See TODO below.
- **JDBC credentials** — database credentials sent in the render request body are handled in memory and never logged or persisted.

### TODO: authentication for external exposure

If you need to expose Jasper Reporter outside the internal Docker network, consider one of:

1. **Reverse proxy with HTTP Basic Auth** (nginx, Caddy)  
2. **API key header** validated by a servlet filter or Javalin `before()` handler  
3. **mTLS** between the PHP app and the sidecar  
4. **Service mesh** (Istio, Linkerd) with policy enforcement