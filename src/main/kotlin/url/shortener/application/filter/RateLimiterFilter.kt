package url.shortener.application.filter

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier

/**
 * RateLimiterFilter is responsible for rate limiting incoming requests based on the client's IP address.
 * It uses Redis via Bucket4j to track request rates.
 */
@Component
class RateLimiterFilter(
    private val bucketConfiguration: Supplier<BucketConfiguration>,
    private val proxyManager: ProxyManager<ByteArray>,
    private val meterRegistry: MeterRegistry
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ipAddress = request.remoteAddr
        val path = request.requestURI
        val startTime = Instant.now()

        try {
            logger.debug("Rate limit check for IP $ipAddress | Path: $path")

            val bucket = proxyManager.builder().build(ipAddress.toByteArray(), bucketConfiguration)
            val consumptionProbe = bucket.tryConsumeAndReturnRemaining(1)

            if (!consumptionProbe.isConsumed) {
                meterRegistry.counter("rate.limit.exceeded").increment()
                logger.warn("Rate limit exceeded for IP $ipAddress | Path: $path | Remaining wait: ${consumptionProbe.nanosToWaitForRefill / 1_000_000}ms")
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too many requests")
                return
            }

            meterRegistry.counter("rate.limit.allowed").increment()
            logger.debug("Rate limit allowed for IP $ipAddress | Path: $path | Remaining tokens: ${consumptionProbe.remainingTokens}")

            filterChain.doFilter(request, response)
        } catch (ex: Exception) {
            meterRegistry.counter("rate.limit.errors").increment()
            logger.error("Rate limiting error for IP $ipAddress | Path: $path", ex)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        } finally {
            val duration = Duration.between(startTime, Instant.now())
            meterRegistry.timer("rate.limit.check.duration").record(duration)
            logger.debug("Rate limit check completed in ${duration.toMillis()}ms")
        }
    }
}
