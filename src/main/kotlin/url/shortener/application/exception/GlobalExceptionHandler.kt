package url.shortener.application.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(InvalidRequestException::class)
    fun handleAllExceptions(ex: InvalidRequestException): ResponseEntity<ErrorResponse> {
        return ResponseEntity(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                message = ex.message ?: "Internal server error"
            ),
            HttpStatus.BAD_REQUEST
        )
    }
}
