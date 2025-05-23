package url.shortener.application.filter

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.function.Supplier

/**
 * RateLimiterFilter is responsible for rate limiting incoming requests based on the client's IP address.
 * It uses Redis via Bucket4j to track request rates.
 */
@Component
class RateLimiterFilter(
    private val bucketConfiguration: Supplier<BucketConfiguration>,
    private val proxyManager: ProxyManager<ByteArray>
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ipAddress = request.remoteAddr

        logger.info("$ipAddress | Calling on ${request.requestURI}")

        try {
            val bucket = proxyManager.builder().build(ipAddress.toByteArray(), bucketConfiguration)
            val consumptionProbe = bucket.tryConsumeAndReturnRemaining(1)

            if (!consumptionProbe.isConsumed) {
                logger.warn("Rate limiting exceeded for IP ${request.remoteAddr} | Calling on ${request.requestURI}")
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests")
                return
            }
        } catch (ex: Exception) {
            logger.error("Rate limiting error for IP ${request.remoteAddr}: ${ex.message}", ex)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            return
        }

        filterChain.doFilter(request, response)
    }
}
