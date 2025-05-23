package url.shortener.infrastructure.limiter

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "shortener.rate-limit")
open class RateLimitConfiguration(
    var capacity: Long = 30,
    var minutes: Long = 1,
    var burst: Burst = Burst()
) {
    data class Burst(var capacity: Long = 5, var seconds: Long = 1)
}
