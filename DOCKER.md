# Docker and Container Registry Guide

This file contains build/run instructions and examples for publishing images to Docker Hub, GitHub Container Registry (ghcr.io), and Google Artifact Registry (GAR).

## Build the image

From the repository root:

```bash
docker build -t jasper-reporter:latest .
```

Tagging for registries (examples):

```bash
docker tag jasper-reporter:latest ghcr.io/<OWNER>/jasper-reporter:latest
docker tag jasper-reporter:latest docker.io/<USERNAME>/jasper-reporter:latest
docker tag jasper-reporter:latest us-central1-docker.pkg.dev/<PROJECT>/<REPO>/jasper-reporter:latest
```

## Run locally (host mapping)

Map to a different host port if 8080 is already used (example uses 8081):

```bash
docker run --rm -p 8081:8080 -v "$(pwd)/reports:/reports" jasper-reporter:latest
```

Let Docker choose a random host port:

```bash
docker run --rm -P -v "$(pwd)/reports:/reports" jasper-reporter:latest
# then inspect with: docker port <container-id> 8080/tcp
```

Run internal-only (no host binding) on a user network:

```bash
docker network create jasper-net
docker run --rm --name jasper --network jasper-net -v "$(pwd)/reports:/reports" jasper-reporter:latest
# From another container on the same network:
docker run --rm --network jasper-net curlimages/curl curl http://jasper:8080/list
```

Or change the container port at runtime with the PORT env (container must be mapped):

```bash
docker run --rm -e PORT=9090 -p 9090:9090 -v "$(pwd)/reports:/reports" jasper-reporter:latest
```

## Environment variables

| Variable      | Default    | Description                    |
|---------------|------------|--------------------------------|
| `PORT`        | `8080`     | HTTP port the server listens on |
| `REPORTS_DIR` | `/reports` | Root directory for report files |

## Docker Compose example

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

Note: `expose` makes port 8080 available only within the `internal` network.

---

## Publishing images

Below are short examples for publishing to common registries. Always protect credentials (do not hardcode in CI logs).

### GitHub Container Registry (ghcr.io)

1. Authenticate locally:

```bash
# Personal access token with packages:write (or use gh cli)
echo $GITHUB_TOKEN | docker login ghcr.io -u <OWNER> --password-stdin
```

2. Tag & push:

```bash
docker tag jasper-reporter:latest ghcr.io/<OWNER>/jasper-reporter:latest
docker push ghcr.io/<OWNER>/jasper-reporter:latest
```

3. GitHub Actions snippet (publish on release):

```yaml
# .github/workflows/publish-ghcr.yml
name: Publish GHCR
on:
  push:
    tags: ['v*']

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      - name: Log in to GHCR
        uses: docker/login-action@v2
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/${{ github.repository_owner }}/jasper-reporter:latest
```

### Docker Hub

1. Authenticate:

```bash
echo $DOCKERHUB_PASS | docker login -u "$DOCKERHUB_USER" --password-stdin
```

2. Tag & push:

```bash
docker tag jasper-reporter:latest docker.io/<USERNAME>/jasper-reporter:latest
docker push docker.io/<USERNAME>/jasper-reporter:latest
```

3. GitHub Actions snippet:

```yaml
# .github/workflows/publish-dockerhub.yml
name: Publish Docker Hub
on:
  push:
    tags: ['v*']

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - name: Log in to Docker Hub
        uses: docker/login-action@v2
        with:
          username: ${{ secrets.DOCKERHUB_USER }}
          password: ${{ secrets.DOCKERHUB_PASS }}
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: docker.io/${{ secrets.DOCKERHUB_USER }}/jasper-reporter:latest
```

### Google Artifact Registry (GAR)

1. Create a docker repository in GAR (example):

```bash
gcloud artifacts repositories create jasper-repo --repository-format=docker --location=us-central1
```

2. Authenticate Docker to GAR:

```bash
gcloud auth configure-docker us-central1-docker.pkg.dev
```

3. Tag & push:

```bash
docker tag jasper-reporter:latest us-central1-docker.pkg.dev/<PROJECT>/<REPO>/jasper-reporter:latest
docker push us-central1-docker.pkg.dev/<PROJECT>/<REPO>/jasper-reporter:latest
```

4. CI snippet (GitHub Actions):

```yaml
# .github/workflows/publish-gar.yml
name: Publish GAR
on:
  push:
    tags: ['v*']

permissions: write-all

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: google-github-actions/auth@v1
        with:
          credentials_json: ${{ secrets.GCP_SERVICE_ACCOUNT_KEY }}
      - name: Configure Docker
        run: |
          gcloud --quiet auth configure-docker us-central1-docker.pkg.dev
      - uses: docker/setup-buildx-action@v3
      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          push: true
          tags: us-central1-docker.pkg.dev/<PROJECT>/<REPO>/jasper-reporter:latest
```

---

## Local development tips

- Mount the `reports/` directory containing jrxml/jasper files to `/reports` as shown above.
- When using JDBC datasources, mount the driver jar into `/app/lib` and update the entrypoint to include `/app/lib/*` on the classpath (example in README PHP section).
- Use `dry_run=true` for quick validation calls to `/compile` and `/render` before performing long-running renders.

If additional registry or CI examples are needed, request the target platform and desired authentication method (PAT, service account, or CI secret).