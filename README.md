# OpenStorm

**Open-data weather radar for Android and Android Auto.**

OpenStorm is an original weather radar application that provides professional-grade storm analysis tools powered by free public data from NOAA, NWS, SPC, and GOES.

## Features

- **Live NEXRAD radar** — real-time reflectivity and velocity from 150+ WSR-88D stations
- **NWS alerts** — watches, warnings, and advisories with map overlays
- **Android Auto** — driver-safe weather UI using Car App Library templates
- **Loop playback** — animated radar loops with scrubbing
- **Station auto-selection** — GPS-based nearest radar detection
- **Dark/Light mode** — automatic system theme matching
- **Offline caching** — recent radar frames cached for offline viewing
- **Provider architecture** — pluggable data sources for commercial extensions

## Architecture

```
OpenStorm/
├── android/        # Android app (Kotlin, Compose, Hilt, Room)
│   ├── app/        # Phone UI
│   ├── core/       # Domain models, repos, providers
│   └── auto/       # Android Auto car screens
├── backend/        # Ktor backend (Kotlin)
└── docs/           # Documentation
```

## Data Sources

| Source | Status | License |
|---|---|---|
| NEXRAD Level III (NOAA) | Active | Public domain |
| NWS Alerts API | Active | Public domain |
| SPC Products | Planned | Public domain |
| GOES Satellite | Planned | Public domain |
| Lightning | Interface only | Commercial (feature-flagged OFF) |

## Getting Started

### Backend

```bash
cd backend
docker-compose up -d    # Start Postgres + Redis
./gradlew run           # Start Ktor server on :8080
```

### Android

```bash
cd android
./gradlew assembleDebug
```

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, Hilt, Room, Coroutines/Flow, MapLibre
- **Android Auto**: AndroidX Car App Library
- **Backend**: Kotlin, Ktor, Exposed ORM, PostgreSQL/PostGIS, Redis
- **CI/CD**: GitHub Actions
- **Maps**: MapLibre GL Native + OpenFreeMap tiles

## Legal

- OpenStorm is not affiliated with or endorsed by NOAA, NWS, or any government agency
- Weather data from NOAA/NWS is public domain per 17 USC §105
- Map data: OpenStreetMap contributors (ODbL license)
- This is an independent project — not a clone of any commercial weather app

## License

Apache 2.0
