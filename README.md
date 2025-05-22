# URL Shortener Service

Implementation of a URL shortening service using Kotlin.

## Key Features

- Create shortened URLs with optional custom aliases
- Set expiration dates for shortened URLs
- Track click counts for each shortened URL
- Redirect from short URLs to original long URLs

## API Endpoints

### POST /shorten

Creates a new shortened URL.

**Request:**

```json
{
  "long_url": "https://example.com/a/long/url",
  "custom_alias": "",
  "expiry_date": "2025-05-21T18-59-59Z"
}
```

**Response:**

```json
{
  "shortUrl": "https://your.domain/abc123",
  "longUrl": "https://example.com/a/long/url",
  "expirationDate": "2025-05-21T18:59:59Z"
}
```

### GET /{shortUrl}

Redirects to the original URL.
