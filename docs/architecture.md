# OpenStorm Architecture

## System Overview

```
┌──────────────────────────────────────────────────────────┐
│                    Android Device                         │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐ │
│  │  Phone App   │  │  Car App    │  │   Core Module    │ │
│  │  (Compose)   │  │  (Auto)     │  │ (Domain + Data)  │ │
│  └──────┬───────┘  └──────┬──────┘  └────────┬─────────┘ │
│         └─────────────────┴──────────────────┘           │
│                          │                                │
│              ┌───────────┴────────────┐                   │
│              │   Room DB + File Cache  │                  │
│              └────────────────────────┘                   │
└──────────────────────────┬───────────────────────────────┘
                           │ HTTPS
┌──────────────────────────┴───────────────────────────────┐
│                   OpenStorm Backend (Ktor)                │
│  ┌────────────┐  ┌──────────────┐  ┌──────────────────┐ │
│  │  REST API   │  │  Ingestion   │  │    Provider      │ │
│  │  Routes     │  │  Workers     │  │    Layer         │ │
│  └──────┬──────┘  └──────┬───────┘  └────────┬─────────┘ │
│         └────────────────┴───────────────────┘           │
│                          │                                │
│  ┌──────────┐  ┌────────┴──┐  ┌─────────────────────┐   │
│  │ PostgreSQL│  │   Redis   │  │   Object Storage    │   │
│  │ + PostGIS │  │   Cache   │  │   (S3/R2/MinIO)     │   │
│  └──────────┘  └───────────┘  └─────────────────────┘   │
└──────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
    ┌──────────┐    ┌──────────┐    ┌──────────┐
    │   NOAA   │    │   NWS    │    │   SPC    │
    │  NEXRAD  │    │  Alerts  │    │ Outlooks │
    └──────────┘    └──────────┘    └──────────┘
```

## Key Design Decisions

1. **Server-side radar rendering**: Decodes NEXRAD binary on server, serves pre-rendered tiles to clients. Saves battery and ensures consistency.

2. **Provider abstraction**: All data sources behind interfaces. Swap NOAA for commercial providers without app changes.

3. **Feature flags**: Data-source-dependent features gated by flags. Lightning OFF by default (commercial licensing).

4. **REST over GraphQL**: Weather data has predictable shapes. REST is simpler to cache at CDN layer.

5. **Ktor over Node**: Same language as Android client. Potential for Kotlin Multiplatform shared models.

6. **MapLibre over Mapbox**: Open-source, no API key, no usage fees. Same rendering quality.

7. **Room + DataStore**: Room for structured data (stations, alerts). DataStore for user preferences. No account required.

## Android Module Graph

```
:app ──→ :core
  │
  └──→ :auto ──→ :core
```

## Data Flow

1. User opens app → GPS location acquired
2. App calls `/api/v1/radar/stations?lat=X&lon=Y`
3. Backend queries station list, calculates distances, returns sorted
4. App stores stations in Room, displays nearest
5. App calls `/api/v1/radar/stations/KTLX/products/N0Q?frames=10`
6. Backend returns tile URLs for last 10 scans
7. App loads tiles into MapLibre as raster overlay
8. Loop playback swaps tile source on timer
```
