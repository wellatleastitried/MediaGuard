# MediaGuard
Generate periodic, recoverable backups of common media stacks.

## What This Project Does

MediaGuard is split into two containers:

1. `MediaGuard-Server`
Runs scheduled backups for Jellyfin, Radarr, Sonarr, Prowlarr, Tdarr, and qBittorrent.

2. `MediaGuard-Client`
Hosts a simple web UI for discovery, manual backup control, schedule updates, and automatic pickup of backup archives.

## Secure Docker Setup

The easiest setup path is the guided script:

```bash
chmod +x ./docker-setup.sh
./docker-setup.sh
```

The script creates:

1. `docker-compose.yml`
2. `.env`
3. `secrets/server.env`
4. `secrets/client.env`

Service enable flags are stored in `secrets/server.env` and host source paths are stored in `.env` for Docker volume mounts.

After running the script:

```bash
docker compose up --build -d
```

Then open the client UI at `http://localhost:8081` unless you selected a different client port.

## Service Path Fields

Every runner now copies files from directories mounted into the server container from the host filesystem.

For each enabled service, provide required path values:

1. Jellyfin
`JELLYFIN_PATH` (mounted, runner reads `config/` under this path)

2. Radarr
`RADARR_PATH` (mounted, runner reads `data/`, `config/`, and `docker-compose.yml`)

3. Sonarr
`SONARR_PATH` (mounted, runner reads `data/`, `config/`, and `docker-compose.yml`)

4. Prowlarr
`PROWLARR_PATH` (mounted, runner reads `config/` and `docker-compose.yml`)

5. Tdarr
`TDARR_PATH` (mounted, runner reads `configs/`, `server/`, `logs/`, and `docker-compose.yml`)

6. qBittorrent
`QBITTORRENT_PATH`, `QBITTORRENT_GRAVEYARD_PATH` (separate graveyard mount)

The guided setup script writes host source path values into `.env`, and service enable flags into `secrets/server.env`.

## Files To Edit Manually If Needed

If you do not want to use the guided script, start from:

1. `docker-compose.example.yml`
2. `secrets/server.env`
3. `secrets/client.env`

Keep secret values in the env files only.

## Build And Test

```bash
./gradlew :MediaGuard-Server:test :MediaGuard-Client:test
```

## Real Endpoint Integration Test (.env-driven)

`RunnersIntegrationTest` can run against your real service paths by loading settings from env files.

1. Copy `.env.test.example` to `.env.test` and fill values.
2. Enable the services you want to test (`*_enabled=true`).
3. Provide real local absolute paths for each required `*_path` value.

Run:

```bash
./gradlew :MediaGuard-Server:realRunnersEnvTest --console=plain --no-daemon
```

File loading priority for this test:

1. `MEDIAGUARD_TEST_ENV_FILE` (if set)
2. `.env.test`
3. `.env`
