# URL Shortener Service

A URL shortening service built with Kotlin and Spring Boot.

## System Architecture

The service follows a layered architecture with:

1. **Presentation Layer**: REST controllers handling HTTP requests
2. **Service Layer**: Core business logic for URL shortening/expansion
3. **Repository Layer**: Data access and persistence
4. **Infrastructure Layer**: Redis caching, Cassandra persistence, monitoring

### Technology Stack

- **Framework**: Spring Boot 3.x
- **Language**: Kotlin
- **Database**: Cassandra (primary store), Redis (caching)
- **Monitoring**: Prometheus + Grafana
- **Load Testing**: K6
- **Build**: Gradle

## Key Features

- URL shortening with custom aliases
- URL expiration support
- Two-tier rate limiting (normal + burst)
- Comprehensive metrics collection
- Scheduled cleanup of expired URLs
- Redis caching for performance
- Containerized deployment

## API Documentation

### POST /shorten

Creates a new shortened URL.

**Request:**
```json
{
  "longUrl": "https://example.com/a/long/url",
  "customAlias": "optional-alias",
  "expirationDate": "2025-05-21T18:59:59Z"
}
```

**Response:**
```json
{
  "shortUrl": "https://your.domain/optional-alias",
  "longUrl": "https://example.com/a/long/url",
  "expirationDate": "2025-05-21T18:59:59Z"
}
```

### GET /{shortUrl}

Redirects to the original URL (HTTP 301).

## Implementation Details

### URL Shortening Algorithm

1. Uses Murmur3 hash of the long URL
2. Base64 URL-safe encoding without padding
3. Recursive collision handling by appending random characters
4. Custom aliases supported if available

### Performance Optimizations

- **Two-level caching**:
  - Redis cache with configurable TTL (default: 30 minutes)
  - Cassandra as persistent store
- **Rate limiting**:
  - 30 requests/minute normal rate
  - 5 requests/second burst capacity

### Monitoring

Metrics collected via Micrometer and exposed for Prometheus:

- Request processing times
- Cache hit/miss rates
- Error rates
- Cleanup statistics

## Configuration

The service can be configured using environment variables:

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `SHORTENER_BASE_URL` | Base URL for shortened links | `http://localhost:8080` |
| `SHORTENER_RATE_LIMIT_CAPACITY` | Normal rate limit requests per minute | `30` |
| `SHORTENER_RATE_LIMIT_MINUTES` | Time window for normal rate limit | `1` |
| `SHORTENER_RATE_LIMIT_BURST_CAPACITY` | Burst rate limit requests | `10` |
| `SHORTENER_RATE_LIMIT_BURST_SECONDS` | Time window for burst rate limit | `5` |
| `SHORTENER_CACHE_TTL_MINUTES` | Redis cache TTL in minutes | `60` |
| `SHORTENER_DEFAULT_EXPIRATION` | Default URL expiration in minutes | `2880` (48h) |
| `SHORTENER_MAX_EXPIRATION` | Maximum allowed expiration in minutes | `10080` (1w) |
| `CASSANDRA_KEYSPACE` | Cassandra keyspace name | `url_shortener` |
| `CASSANDRA_CONTACT_POINTS` | Cassandra contact points | `localhost` |
| `CASSANDRA_PORT` | Cassandra port | `9042` |
| `CASSANDRA_USERNAME` | Cassandra username | `admin` |
| `CASSANDRA_PASSWORD` | Cassandra password | `admin` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `SERVER_PORT` | Application HTTP port | `8080` |

Example Docker run command with custom configuration:
```bash
docker run -e SHORTENER_BASE_URL=https://short.example.com \
           -e CASSANDRA_CONTACT_POINTS=cassandra1,cassandra2 \
           -e REDIS_HOST=redis \
           -p 8080:8080 \
           url-shortener
```

## Design Decisions and Rationale

1. **Database Choice**:
   - Cassandra was selected for its horizontal scalability and ability to handle high write throughput

2. **Caching Strategy**:
   - Redis provides sub-millisecond response times for hot URLs
   - Cache invalidation handled automatically via TTL

3. **URL Generation**:
   - Murmur3 hash provides good distribution with low collision probability
   - Base64 encoding produces URL-safe identifiers
   - Recursive collision handling ensures uniqueness

4. **Rate Limiting**:
   - Two-tier approach prevents abuse while allowing bursts
   - Redis-backed implementation is distributed and consistent
   - Metrics help identify and adjust limits as needed

5. **Monitoring**:
   - Prometheus + Grafana provide real-time visibility
   - Micrometer integration gives detailed application metrics
   - Helps identify performance bottlenecks and errors

## Load Testing

Included K6 test script (`k6/url-shortener-test.js`) simulates:
- URL shortening requests
- URL expansion requests
- Mixed workload

Run with:
```bash
k6 run k6/url-shortener-test.js
```
