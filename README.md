# LinkForge

A full-stack, self-hosted URL shortener built with **Spring Boot** and vanilla **HTML/CSS/JavaScript** — no frameworks, no third-party shortening service, no accounts.

Paste a long URL, get back a short one. Optionally pick a custom alias, set an expiry date, lock it with a password, and share it as a scannable QR code. Every visit is tracked and shown on a live dashboard with click-over-time charts.

Deployed and tested on **AWS EC2** as a systemd-managed service.

## Features

- **Shorten any URL** with a randomly generated, collision-checked Base62 code
- **Custom aliases** (`/my-launch` instead of `/aZ3xQ1p`)
- **Optional expiry dates** — expired links return `410 Gone` and are flagged in the dashboard
- **Password-protected links** — set a password on any link; visitors land on a small unlock page before being redirected. Passwords are hashed with BCrypt, never stored in plain text
- **Click tracking** — every redirect is logged with a timestamp, powering a per-link "clicks over time" chart and a site-wide sparkline in the header
- **QR code generation** on the server (via ZXing) for every short link, downloadable as PNG
- **Bulk CSV import/export** — export every link as a CSV file, or bulk-create links by uploading a CSV with `originalUrl` / `customAlias` columns
- **Dark/light theme toggle** — preference saved to `localStorage`, respects OS preference on first visit
- **Dashboard** listing every link with live search/filter, copy, QR, per-link stats and delete actions
- **REST API** with proper HTTP status codes (`201`, `401`, `404`, `409`, `410`, `413`, `422`) and JSON error bodies
- **Zero-config persistence** — ships with an embedded H2 file database, with an optional PostgreSQL profile for production
- **Input validation** on both the client and server (URL format, alias format, password length, expiry in the future)
- Unit tests covering the service layer (creation, aliasing, expiry, click tracking, password protection, analytics, deletion)

## Tech stack

| Layer          | Technology                                                |
|----------------|-------------------------------------------------------------|
| Backend        | Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA     |
| Database       | H2 (embedded, file-based) — PostgreSQL profile included     |
| Security       | Spring Security Crypto (BCrypt password hashing)             |
| QR codes       | ZXing (`com.google.zxing`)                                    |
| CSV            | Apache Commons CSV                                             |
| Frontend       | Vanilla HTML5, CSS3, JavaScript (no build step)                 |
| Deployment     | AWS EC2 (Ubuntu), systemd-managed process                        |
| Testing        | JUnit 5, AssertJ, Spring Boot Test                                |
| Build          | Maven                                                              |

## Architecture

```
src/main/java/com/linkforge/urlshortener/
├── controller/     REST API (UrlController) + public redirect/unlock handler (RedirectController)
├── service/        Business logic — code generation, expiry rules, password hashing, click tracking, CSV, analytics
├── repository/     Spring Data JPA repositories (UrlMapping, ClickEvent)
├── entity/         UrlMapping and ClickEvent JPA entities
├── dto/            Request/response payloads
├── exception/      Domain exceptions + a @RestControllerAdvice mapping them to HTTP status codes
└── util/           Base62Encoder, QrCodeGenerator

src/main/resources/
├── static/         Frontend (index.html, assets/style.css, assets/app.js)
└── application.properties
```

The frontend is served as static resources directly by Spring Boot — open `http://localhost:8080` and it's there, no separate dev server or `npm install` required.

## Running it locally

Requirements: **Java 21+** and **Maven** (or use the included wrapper).

```bash
./mvnw spring-boot:run
```

Then open **http://localhost:8080**.

The app creates a `./data/linkforge.mv.db` H2 database file on first run — no setup needed. The embedded H2 console is available at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/linkforge`, user `sa`, blank password) if you want to inspect the data directly.

### Using PostgreSQL instead

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=postgres
```

Update the connection details in `src/main/resources/application-postgres.properties` first.

### Running the tests

```bash
./mvnw test
```

## Deploying to AWS EC2

This project was deployed and load-tested on a t3.micro EC2 instance (Ubuntu, free tier). Summary of the process:

1. Launch an EC2 instance (Ubuntu, `t2.micro`/`t3.micro` for free tier)
2. Open inbound ports **22** (SSH) and **8080** (app) in the instance's security group
3. Install Java 21: `sudo apt update && sudo apt install -y openjdk-21-jdk git`
4. Clone the repo and build: `git clone <repo-url> && cd linkforge && ./mvnw clean package -DskipTests`
5. Run it as a systemd service (auto-restarts on crash/reboot):
   ```ini
   # /etc/systemd/system/linkforge.service
   [Unit]
   Description=LinkForge URL Shortener
   After=network.target

   [Service]
   User=ubuntu
   WorkingDirectory=/home/ubuntu/linkforge
   ExecStart=/usr/bin/java -jar /home/ubuntu/linkforge/target/linkforge-0.0.1-SNAPSHOT.jar --app.base-url=http://<PUBLIC_IP>:8080
   Restart=always

   [Install]
   WantedBy=multi-user.target
   ```
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable linkforge
   sudo systemctl start linkforge
   ```
6. Set `app.base-url` to the instance's public/Elastic IP so generated short links and QR codes resolve correctly

Note: the Clipboard API (`navigator.clipboard`) requires a secure context (HTTPS or `localhost`). On a plain-HTTP deployment like a bare EC2 IP, the frontend falls back to the legacy `document.execCommand('copy')` method so the copy button still works.

## API reference

| Method | Path                            | Description                                       |
|--------|----------------------------------|-----------------------------------------------------|
| POST   | `/api/urls`                     | Create a short URL                                 |
| GET    | `/api/urls`                     | List all short URLs, newest first                  |
| GET    | `/api/urls/{shortCode}`         | Get stats for one short URL                        |
| DELETE | `/api/urls/{shortCode}`         | Delete a short URL                                 |
| POST   | `/api/urls/{shortCode}/unlock`  | Verify a password and get the original URL         |
| GET    | `/api/urls/{shortCode}/qrcode`  | Get a PNG QR code for the short URL                |
| GET    | `/api/urls/{shortCode}/analytics?days=14` | Clicks per day for one link               |
| GET    | `/api/urls/analytics/summary?days=14`     | Clicks per day across all links           |
| GET    | `/api/urls/stats/summary`       | Total link count and total clicks                  |
| GET    | `/api/urls/export/csv`          | Download all links as a CSV file                    |
| POST   | `/api/urls/import/csv`          | Bulk-create links from an uploaded CSV (`multipart/form-data`, field name `file`) |
| GET    | `/{shortCode}`                  | Redirect to the original URL (302), or show a password prompt if protected |

**Create a short URL**

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/long/path", "customAlias": "demo", "expiresAt": "2026-12-31T23:59:00", "password": "hunter2"}'
```

```json
{
  "id": 1,
  "originalUrl": "https://example.com/some/long/path",
  "shortCode": "demo",
  "shortUrl": "http://localhost:8080/demo",
  "createdAt": "2026-09-08T10:15:30",
  "expiresAt": "2026-12-31T23:59:00",
  "lastAccessedAt": null,
  "clickCount": 0,
  "expired": false,
  "passwordProtected": true
}
```

`password` and `expiresAt` are both optional and independent of each other.

**CSV import format**

The importer looks for an `originalUrl` (or `url`) column, and an optional `customAlias` (or `alias`) column. Extra columns are ignored. Rows with an invalid URL or duplicate alias are skipped and reported back in the response, without failing the whole import.

```csv
originalUrl,customAlias
https://example.com/page-one,page-one
https://example.com/page-two,
```

## Possible next steps

- User accounts / API keys, so links are scoped per owner
- Rate limiting on the create endpoint
- HTTPS via a domain name + Nginx reverse proxy + Let's Encrypt
- Analytics broken down by referrer, country or device
