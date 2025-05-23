package url.shortener.application.exception

data class ErrorResponse(
    val status: Int,
    val message: String
)