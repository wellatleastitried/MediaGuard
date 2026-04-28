#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_DIR="$ROOT_DIR/secrets"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
ENV_FILE="$ROOT_DIR/.env"
SERVER_ENV_FILE="$SECRETS_DIR/server.env"
CLIENT_ENV_FILE="$SECRETS_DIR/client.env"

mkdir -p "$SECRETS_DIR"

prompt() {
  local var_name="$1"
  local prompt_text="$2"
  local default_value="${3:-}"
  local value=""

  read -r -p "$prompt_text" value
  if [[ -z "$value" ]]; then
    value="$default_value"
  fi

  printf -v "$var_name" '%s' "$value"
}

yes_no() {
  local var_name="$1"
  local prompt_text="$2"
  local default_value="${3:-y}"
  local response
  read -r -p "$prompt_text" response
  response="${response:-$default_value}"
  if [[ "$response" =~ ^[Yy]([Ee][Ss])?$ ]]; then
    printf -v "$var_name" 'true'
  else
    printf -v "$var_name" 'false'
  fi
}

cat <<'INTRO'
MediaGuard Docker setup

This script creates:
1. docker-compose.yml
2. .env
3. secrets/server.env (if applicable)
4. secrets/client.env (if applicable)

All backup runner inputs are local filesystem paths on the server host.
You can re-run this script at any time to regenerate the files.
INTRO

# Ask what to set up
setup_choice=""
while [[ ! "$setup_choice" =~ ^[scb]$ ]]; do
  read -r -p "What would you like to set up? [s]erver, [c]lient, or [b]oth: " setup_choice
  setup_choice="${setup_choice,,}"
done

setup_server="false"
setup_client="false"
if [[ "$setup_choice" == "s" ]]; then
  setup_server="true"
elif [[ "$setup_choice" == "c" ]]; then
  setup_client="true"
else
  setup_server="true"
  setup_client="true"
fi

overwrite="false"
if [[ -f "$COMPOSE_FILE" || -f "$ENV_FILE" || -f "$SERVER_ENV_FILE" || -f "$CLIENT_ENV_FILE" ]]; then
  yes_no overwrite "Existing setup files found. Overwrite them? [y/N]: " "n"
  if [[ "$overwrite" != "true" ]]; then
    echo "Aborted."
    exit 0
  fi
fi

prompt server_port "MediaGuard-Server host port [38471]: " "38471"
prompt client_port "MediaGuard-Client host port [8081]: " "8081"
prompt backup_interval "Server automatic backup interval in ISO-8601 format [PT12H]: " "PT12H"
prompt pickup_interval "Client automatic pickup interval in ISO-8601 format [PT12H]: " "PT12H"

if [[ "$setup_server" == "true" ]]; then
  prompt backup_root_host "Server backup host path [./data/server/backups]: " "./data/server/backups"
  
  yes_no jellyfin_enabled "Enable Jellyfin backups? [Y/n]: " "y"
  prompt jellyfin_path "Jellyfin source base directory [/mnt/appdata/jellyfin]: " "/mnt/appdata/jellyfin"

  yes_no radarr_enabled "Enable Radarr backups? [Y/n]: " "y"
  prompt radarr_path "Radarr source base directory [/mnt/appdata/radarr]: " "/mnt/appdata/radarr"

  yes_no sonarr_enabled "Enable Sonarr backups? [Y/n]: " "y"
  prompt sonarr_path "Sonarr source base directory [/mnt/appdata/sonarr]: " "/mnt/appdata/sonarr"

  yes_no prowlarr_enabled "Enable Prowlarr backups? [Y/n]: " "y"
  prompt prowlarr_path "Prowlarr source base directory [/mnt/appdata/prowlarr]: " "/mnt/appdata/prowlarr"

  yes_no tdarr_enabled "Enable Tdarr backups? [Y/n]: " "y"
  prompt tdarr_path "Tdarr source base directory [/mnt/appdata/tdarr]: " "/mnt/appdata/tdarr"

  yes_no qbittorrent_enabled "Enable qBittorrent backups? [Y/n]: " "y"
  prompt qbittorrent_path "qBittorrent source base directory [/mnt/appdata/qbittorrent]: " "/mnt/appdata/qbittorrent"
  prompt qbittorrent_graveyard_path "qBittorrent graveyard source directory [/mnt/media/graveyard]: " "/mnt/media/graveyard"
fi

if [[ "$setup_client" == "true" ]]; then
  prompt client_downloads_host "Client download host path [./data/client/downloads]: " "./data/client/downloads"
  prompt client_state_host "Client state host path [./data/client]: " "./data/client"
  prompt preferred_server "Preferred server URL for client [http://mediaguard-server:8080]: " "http://mediaguard-server:8080"
fi

cat > "$ENV_FILE" <<EOF_ENV
SERVER_PORT=$server_port
CLIENT_PORT=$client_port
EOF_ENV

if [[ "$setup_server" == "true" ]]; then
  cat >> "$ENV_FILE" <<EOF_ENV_SERVER
BACKUP_ROOT_HOST_PATH=$backup_root_host
JELLYFIN_PATH=$jellyfin_path
RADARR_PATH=$radarr_path
SONARR_PATH=$sonarr_path
PROWLARR_PATH=$prowlarr_path
TDARR_PATH=$tdarr_path
QBITTORRENT_PATH=$qbittorrent_path
QBITTORRENT_GRAVEYARD_PATH=$qbittorrent_graveyard_path
MEDIAGUARD_BACKUP_INTERVAL=$backup_interval
EOF_ENV_SERVER
fi

if [[ "$setup_client" == "true" ]]; then
  cat >> "$ENV_FILE" <<EOF_ENV_CLIENT
CLIENT_DOWNLOADS_HOST_PATH=$client_downloads_host
CLIENT_STATE_HOST_PATH=$client_state_host
MEDIAGUARD_SERVER_URL=$preferred_server
CLIENT_PICKUP_INTERVAL=$pickup_interval
EOF_ENV_CLIENT
fi

if [[ "$setup_server" == "true" ]]; then
  cat > "$SERVER_ENV_FILE" <<EOF_SERVER
SERVER_PORT=8080
MEDIAGUARD_BACKUP_INTERVAL=$backup_interval
MEDIAGUARD_BACKUP_ROOT=/var/lib/mediaguard/backups
MEDIAGUARD_RETENTION_COUNT=10
JELLYFIN_ENABLED=$jellyfin_enabled
RADARR_ENABLED=$radarr_enabled
SONARR_ENABLED=$sonarr_enabled
PROWLARR_ENABLED=$prowlarr_enabled
TDARR_ENABLED=$tdarr_enabled
QBITTORRENT_ENABLED=$qbittorrent_enabled
EOF_SERVER
fi

if [[ "$setup_client" == "true" ]]; then
  cat > "$CLIENT_ENV_FILE" <<EOF_CLIENT
SERVER_PORT=$server_port
CLIENT_PORT=$client_port
MEDIAGUARD_SERVER_URL=$preferred_server
CLIENT_PICKUP_INTERVAL=$pickup_interval
CLIENT_DOWNLOAD_DIRECTORY=/var/lib/mediaguard/downloads
CLIENT_STATE_FILE=/var/lib/mediaguard/client/state.json
EOF_CLIENT
fi

cat > "$COMPOSE_FILE" <<'EOF_COMPOSE_START'
services:
EOF_COMPOSE_START

if [[ "$setup_server" == "true" ]]; then
  cat >> "$COMPOSE_FILE" <<EOF_COMPOSE_SERVER
  mediaguard-server:
    build:
      context: .
      dockerfile: MediaGuard-Server/Dockerfile
    container_name: mediaguard-server
    env_file:
      - ./secrets/server.env
    ports:
      - "\${SERVER_PORT}:8080"
    volumes:
      - \${BACKUP_ROOT_HOST_PATH}:/var/lib/mediaguard/backups
      - \${JELLYFIN_PATH}:/srv/sources/jellyfin:ro
      - \${RADARR_PATH}:/srv/sources/radarr:ro
      - \${SONARR_PATH}:/srv/sources/sonarr:ro
      - \${PROWLARR_PATH}:/srv/sources/prowlarr:ro
      - \${TDARR_PATH}:/srv/sources/tdarr:ro
      - \${QBITTORRENT_PATH}:/srv/sources/qbittorrent:ro
      - \${QBITTORRENT_GRAVEYARD_PATH}:/srv/sources/qbittorrent-graveyard:ro
    restart: unless-stopped

EOF_COMPOSE_SERVER
fi

if [[ "$setup_client" == "true" ]]; then
  if [[ "$setup_server" == "true" ]]; then
    cat >> "$COMPOSE_FILE" <<EOF_COMPOSE_CLIENT
  mediaguard-client:
    build:
      context: .
      dockerfile: MediaGuard-Client/Dockerfile
    container_name: mediaguard-client
    env_file:
      - ./secrets/client.env
    depends_on:
      - mediaguard-server
    ports:
      - "\${CLIENT_PORT}:8081"
    volumes:
      - \${CLIENT_DOWNLOADS_HOST_PATH}:/var/lib/mediaguard/downloads
      - \${CLIENT_STATE_HOST_PATH}:/var/lib/mediaguard/client
    restart: unless-stopped

EOF_COMPOSE_CLIENT
  else
    cat >> "$COMPOSE_FILE" <<EOF_COMPOSE_CLIENT
  mediaguard-client:
    build:
      context: .
      dockerfile: MediaGuard-Client/Dockerfile
    container_name: mediaguard-client
    env_file:
      - ./secrets/client.env
    ports:
      - "\${CLIENT_PORT}:8081"
    volumes:
      - \${CLIENT_DOWNLOADS_HOST_PATH}:/var/lib/mediaguard/downloads
      - \${CLIENT_STATE_HOST_PATH}:/var/lib/mediaguard/client
    restart: unless-stopped

EOF_COMPOSE_CLIENT
  fi
fi

echo ""
echo "Created: $COMPOSE_FILE"
echo "Created: $ENV_FILE"
if [[ "$setup_server" == "true" ]]; then
  echo "Created: $SERVER_ENV_FILE"
fi
if [[ "$setup_client" == "true" ]]; then
  echo "Created: $CLIENT_ENV_FILE"
fi
echo ""
echo "Next steps:"
echo "1. Review .env"
step=2
if [[ "$setup_server" == "true" ]]; then
  echo "$step. Review secrets/server.env"
  ((step++))
fi
if [[ "$setup_client" == "true" ]]; then
  echo "$step. Review secrets/client.env"
  ((step++))
fi
echo "$step. Start services with: docker compose up --build -d"
if [[ "$setup_client" == "true" ]]; then
  ((step++))
  echo "$step. Open MediaGuard Client at: http://localhost:$client_port"
fi
