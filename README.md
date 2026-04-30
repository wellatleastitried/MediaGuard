# MediaGuard

MediaGuard creates scheduled backups for common media stack apps and provides a simple client UI to manage backups.

## Install

```bash
chmod +x ./docker-setup.sh
./docker-setup.sh
```

Run the setup script on each machine and choose either server or client.

## Start

```bash
docker compose up --build -d
```

Open the client UI at `http://localhost:8081` (or the client port you selected during setup).
