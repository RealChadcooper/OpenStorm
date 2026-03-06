# OpenStorm API Contracts

Base URL: `https://api.openstorm.app` (production) / `http://localhost:8080` (local)

All responses are JSON. All timestamps are ISO-8601 UTC.

## Endpoints

### Health

```
GET /api/v1/health
```

Response:
```json
{"status": "ok", "version": "1.0.0", "uptime": 3600}
```

### Radar Stations

```
GET /api/v1/radar/stations?lat={lat}&lon={lon}&radius={km}
```

Parameters:
- `lat` (required): Latitude
- `lon` (required): Longitude
- `radius` (optional, default 200): Search radius in km

Response:
```json
{
  "stations": [
    {
      "id": "KTLX",
      "name": "Oklahoma City, OK",
      "lat": 35.3331,
      "lon": -97.2778,
      "elevation": 370.0,
      "stationType": "WSR88D",
      "status": "ACTIVE",
      "distanceKm": 15.2,
      "products": ["N0Q", "N0U"]
    }
  ]
}
```

### Radar Frames

```
GET /api/v1/radar/stations/{stationId}/products/{product}?frames={count}
```

Parameters:
- `stationId` (required): ICAO station code
- `product` (required): Product code (N0Q, N0U, etc.)
- `frames` (optional, default 10): Number of frames

Response:
```json
{
  "station": "KTLX",
  "product": "N0Q",
  "frames": [
    {
      "timestamp": "2026-03-06T18:00:00Z",
      "tileUrl": "https://cdn.openstorm.app/tiles/KTLX/N0Q/2026-03-06T18:00:00Z/{z}/{x}/{y}.webp",
      "expiresAt": "2026-03-06T18:10:00Z"
    }
  ]
}
```

### Alerts

```
GET /api/v1/alerts?lat={lat}&lon={lon}&radius={km}
```

Response:
```json
{
  "alerts": [
    {
      "id": "urn:oid:...",
      "type": "WARNING",
      "event": "Tornado Warning",
      "headline": "Tornado Warning issued...",
      "description": "...",
      "areaDesc": "Cleveland County",
      "severity": "EXTREME",
      "certainty": "OBSERVED",
      "urgency": "IMMEDIATE",
      "effective": "2026-03-06T17:45:00Z",
      "expires": "2026-03-06T18:15:00Z",
      "senderName": "NWS Norman OK"
    }
  ]
}
```

## Error Responses

All errors return:
```json
{"error": "Description of what went wrong"}
```

Status codes:
- 400: Missing or invalid parameters
- 404: Resource not found
- 429: Rate limited (60 req/min per IP)
- 500: Internal server error
