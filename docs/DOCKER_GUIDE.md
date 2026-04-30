# Docker Deployment Guide

This guide explains how to build a Docker image for SimpleDQC and run it with `docker-compose`.

---

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) installed and running
- [Docker Compose](https://docs.docker.com/compose/install/) (included with Docker Desktop)

---

## Building the Docker Image

A multi-stage `Dockerfile` is provided at the project root. It compiles the application in a JDK container and produces a lean runtime image.

### Build the image

From the project root directory:

```bash
docker build -t simpledqc:latest .
```

The build process:
1. Compiles the source with Maven inside a `eclipse-temurin:25-jdk` container (no local JDK or Maven required).
2. Packages the resulting JAR into a clean runtime image.

### Tag for a registry (optional)

```bash
docker tag simpledqc:latest your-registry/simpledqc:1.0.0
docker push your-registry/simpledqc:1.0.0
```

---

## Running with Docker Compose

A `docker-compose.yml` is provided at the project root.

### 1. Configure environment variables

Open `docker-compose.yml` and set the values under `environment:` for your deployment:

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_SOURCE_JDBC-URL` | JDBC URL of your source database |
| `SPRING_DATASOURCE_SOURCE_USERNAME` | Source DB username |
| `SPRING_DATASOURCE_SOURCE_PASSWORD` | Source DB password |
| `SPRING_MAIL_HOST` | SMTP server host |
| `SPRING_MAIL_PORT` | SMTP port (default 587) |
| `SPRING_MAIL_USERNAME` | SMTP login |
| `SPRING_MAIL_PASSWORD` | SMTP password or app password |
| `DQ_DEMO` | `true` to enable demo mode |
| `DQ_ALLOW-WRITE` | `true` to allow write operations (disable in production) |
| `DQ_EMAIL-ENABLED` | `true` to send email notifications |
| `DQ_SEND-ON-FAILURE` | `true` to email only when issues are found |
| `DQ_RECIPIENTS` | Comma-separated list of notification recipients |

For sensitive values, prefer a `.env` file (see below) over editing `docker-compose.yml` directly.

### 2. Use a .env file for secrets (recommended)

Create a `.env` file in the project root (it is already in `.gitignore` by convention):

```dotenv
SOURCE_DB_URL=jdbc:mysql://your-source-db:3306/your_schema
SOURCE_DB_USER=your_user
SOURCE_DB_PASSWORD=your_password
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_app_password
```

Then reference these in `docker-compose.yml`:

```yaml
environment:
  - SPRING_DATASOURCE_SOURCE_JDBC-URL=${SOURCE_DB_URL}
  - SPRING_DATASOURCE_SOURCE_USERNAME=${SOURCE_DB_USER}
  - SPRING_DATASOURCE_SOURCE_PASSWORD=${SOURCE_DB_PASSWORD}
  - SPRING_MAIL_USERNAME=${MAIL_USERNAME}
  - SPRING_MAIL_PASSWORD=${MAIL_PASSWORD}
```

### 3. Start the application

```bash
docker compose up -d
```

The application will be available at `http://localhost:8090`.

### 4. View logs

```bash
docker compose logs -f simpledqc
```

### 5. Stop the application

```bash
docker compose down
```

---

## Mounted Volumes

The container uses the following bind mounts (mapped to local directories):

| Container path | Local path | Purpose |
|---|---|---|
| `/app/controls` | `./controls` | SQL control query files |
| `/app/repository` | `./repository` | H2 embedded repository database |
| `/app/scheduler` | `./scheduler` | `cron.txt` scheduler configuration |
| `/app/certs` | `./certs` | TLS certificates (optional) |

Control query files dropped in `./controls` are picked up live — no container restart required.

---

## TLS / HTTPS

To enable HTTPS, mount your PEM certificates and add the TLS environment variables. See [TLS_CONFIGURATION.md](TLS_CONFIGURATION.md) for details, then add to `docker-compose.yml`:

```yaml
volumes:
  - ./certs:/app/certs
environment:
  - SERVER_SSL_ENABLED=true
  - SERVER_SSL_CERTIFICATE=/app/certs/cert.pem
  - SERVER_SSL_CERTIFICATE-PRIVATE-KEY=/app/certs/key.pem
```

---

## Including a Test MySQL Source Database

The `docker-compose.yml` includes a commented-out `mysql-source` service. Uncomment it to spin up a local MySQL instance alongside SimpleDQC for development or testing:

```yaml
services:
  mysql-source:
    image: mysql:8
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: your_schema
      MYSQL_USER: your_user
      MYSQL_PASSWORD: your_password
```

Then set the source JDBC URL to:

```
jdbc:mysql://mysql-source:3306/your_schema
```

(Docker Compose resolves service names as hostnames within the same network.)

---

## Building from Source inside Docker Compose

The `build: .` directive in `docker-compose.yml` is commented out by default (assumes a pre-built image). To build from source when running Compose:

```yaml
simpledqc:
  build: .
  # image: simpledqc:latest   # comment this out
```

Then run:

```bash
docker compose up --build -d
```
