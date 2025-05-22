package url.shortener.application.dto

data class ShortenResponse(
    val shortUrl: String,
    val longUrl: String,
    val expirationDate: String?
)
