# Changelog

All notable changes to DevFlow AI are listed here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.0] - 2026-11-19

### Added
- Task kits: generate every file a development task needs from one short form, with live preview and ZIP download
- Template engine with naming-style filters (`pascal`, `camel`, `kebab`, `snake`, `constant`, `title`, `plural`, `path`, `lower`, `upper`) and built-in `today`/`year`
- Built-in kits: Spring Boot REST feature, Bug fix kit, New project starter files
- Kit editor in the browser: create, edit, duplicate, delete, export and import kits as JSON
- Generation history and time-saved statistics stored in PostgreSQL
- REST API, health check (`/actuator/health`), and Flyway database migrations
- One-command install and start: `start.bat` (Windows) and `start.sh` (macOS/Linux) install Docker and Java 21 if needed, create `.env` with a random password, and run everything in Docker
- Automated tests (31) and a GitHub Actions CI workflow

### Security
- Generated file paths cannot escape the target folder (zip-slip protection)
- Secrets only come from environment variables or the git-ignored `.env`
- The app listens on `127.0.0.1` only, and the container runs as a non-root user

### Fixed
- Windows start failed when port 5432 was in use or reserved. The database is now private to Docker, and the app picks a free port if 8080 is taken.
- UI: stale responses no longer overwrite the selected kit or typed values, and Save can no longer create a kit twice
