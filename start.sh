#!/usr/bin/env bash
# DevFlow AI one-command start for macOS and Linux.
#   ./start.sh            install what's missing, then start DevFlow AI
#   ./start.sh --no-java  skip installing Java 21 (only Docker is needed to run the app)
#
# What it does:
#   1. Installs Docker (and Java 21, used for development and tests) if they are missing
#   2. Starts Docker if it is not running
#   3. Creates .env with a random database password (first run only)
#   4. Builds and starts the database and app containers
#   5. Waits until the app is healthy and opens it in your browser
set -euo pipefail
cd "$(dirname "$0")"

INSTALL_JAVA=1
[[ "${1:-}" == "--no-java" ]] && INSTALL_JAVA=0
PORT_IN_ENV=""

say()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m ok\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }

OS="$(uname -s)"
SUDO=""
[[ "$(id -u)" -ne 0 ]] && have sudo && SUDO="sudo"

# ------------------------------------------------------------------ 1. Docker
install_docker() {
  case "$OS" in
    Darwin)
      have brew || fail "Homebrew is needed to install Docker automatically. Install it from https://brew.sh, or install Docker Desktop from https://www.docker.com/products/docker-desktop/ and run this again."
      say "Installing Docker Desktop with Homebrew (you may be asked for your password)..."
      brew install --cask docker
      ;;
    Linux)
      if have apt-get; then
        say "Installing Docker with apt (you may be asked for your password)..."
        $SUDO apt-get update -y
        $SUDO apt-get install -y docker.io docker-compose-v2 || $SUDO apt-get install -y docker.io docker-compose-plugin
        $SUDO systemctl enable --now docker 2>/dev/null || $SUDO service docker start || true
        if [[ -n "$SUDO" ]]; then $SUDO usermod -aG docker "$USER" || true; fi
      elif have dnf; then
        $SUDO dnf install -y docker docker-compose-plugin
        $SUDO systemctl enable --now docker
        if [[ -n "$SUDO" ]]; then $SUDO usermod -aG docker "$USER" || true; fi
      else
        fail "Please install Docker from https://docs.docker.com/engine/install/ and run this again."
      fi
      ;;
    *) fail "Unsupported system '$OS'. On Windows, double-click start.bat instead." ;;
  esac
}

if ! have docker; then
  install_docker
fi
have docker || fail "Docker was installed but is not on your PATH yet. Open a new terminal and run ./start.sh again."
ok "Docker installed ($(docker --version))"

# The docker CLI may need sudo right after installation on Linux (group change applies at next login)
DOCKER="docker"
if ! docker info >/dev/null 2>&1 && [[ "$OS" == "Linux" && -n "$SUDO" ]] && $SUDO docker info >/dev/null 2>&1; then
  DOCKER="$SUDO docker"
fi

# ------------------------------------------------------------------ 1b. Java 21 (optional)
java_major() { java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1; }
if [[ $INSTALL_JAVA -eq 1 ]]; then
  if have java && [[ "$(java_major)" -ge 21 ]] 2>/dev/null; then
    ok "Java $(java_major) installed"
  else
    say "Installing Java 21 (used for development and running tests)..."
    case "$OS" in
      Darwin) have brew && brew install --cask temurin@21 || say "Skipped Java: install Temurin 21 from https://adoptium.net if you want to run tests." ;;
      Linux)
        if have apt-get; then $SUDO apt-get install -y openjdk-21-jdk
        elif have dnf; then $SUDO dnf install -y java-21-openjdk-devel
        fi ;;
    esac
  fi
fi

# ------------------------------------------------------------------ 2. Start Docker
if ! $DOCKER info >/dev/null 2>&1; then
  say "Starting Docker..."
  if [[ "$OS" == "Darwin" ]]; then
    open -a Docker || fail "Could not start Docker Desktop. Open it from Applications, accept its terms, then run ./start.sh again."
  else
    $SUDO systemctl start docker 2>/dev/null || $SUDO service docker start 2>/dev/null || true
  fi
  for _ in $(seq 1 90); do
    $DOCKER info >/dev/null 2>&1 && break
    sleep 2
  done
fi
$DOCKER info >/dev/null 2>&1 || fail "Docker is not running. If this is the first time, open Docker Desktop once and accept its terms, then run ./start.sh again."
ok "Docker is running"

# ------------------------------------------------------------------ 3. .env
random_secret() {
  if have openssl; then openssl rand -hex 24
  else LC_ALL=C tr -dc 'a-f0-9' </dev/urandom | head -c 48
  fi
}
if [[ ! -f .env ]]; then
  say "Creating .env with a random database password..."
  PW="$(random_secret)"
  sed -e "s/^DB_PASSWORD=.*/DB_PASSWORD=$PW/" -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$PW/" .env.example > .env
  chmod 600 .env
  unset PW
  ok ".env created (git-ignored; the password is never printed)"
else
  ok ".env already exists, keeping it"
fi
PORT_IN_ENV="$(sed -n 's/^PORT=\([0-9]*\).*/\1/p' .env | head -1)"
PORT="${PORT_IN_ENV:-8080}"

# If another program already uses the port, pick the next free one and remember it in .env
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }
set_env_value() {   # replaces NAME=... or "# NAME=..." in .env, or appends it (works on macOS and Linux)
  awk -v k="$1" -v v="$2" '
    !done && $0 ~ ("^#? *" k "=") { print k "=" v; done = 1; next }
    { print }
    END { if (!done) print k "=" v }' .env > .env.tmp
  mv .env.tmp .env
  chmod 600 .env
}
if [[ -z "$($DOCKER compose ps --status running -q app 2>/dev/null)" ]] && port_in_use "$PORT"; then
  NEW_PORT=""
  for p in $(seq 8081 8099); do
    if ! port_in_use "$p"; then NEW_PORT=$p; break; fi
  done
  [[ -n "$NEW_PORT" ]] || fail "Ports $PORT and 8081-8099 are all in use. Set a free PORT=... in .env and run again."
  say "Port $PORT is already in use, so DevFlow AI will use port $NEW_PORT."
  set_env_value PORT "$NEW_PORT"
  PORT="$NEW_PORT"
fi

# ------------------------------------------------------------------ 4. Build and start
say "Building and starting DevFlow AI (the first build downloads dependencies and takes a few minutes)..."
$DOCKER compose up -d --build

# ------------------------------------------------------------------ 5. Wait for health
URL="http://localhost:$PORT"
say "Waiting for the app to become healthy..."
for _ in $(seq 1 120); do
  if curl -fs "$URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    ok "DevFlow AI is running at $URL"
    if [[ "$OS" == "Darwin" ]]; then open "$URL"; elif have xdg-open; then xdg-open "$URL" >/dev/null 2>&1 || true; fi
    echo
    echo "Stop it with:   ./stop.sh   (your kits and history are kept)"
    exit 0
  fi
  sleep 2
done
$DOCKER compose logs --tail 40 app
fail "The app did not become healthy within 4 minutes. The log above shows why."
