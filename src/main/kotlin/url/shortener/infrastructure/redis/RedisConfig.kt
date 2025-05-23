package url.shortener.infrastructure.redis

import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import url.shortener.infrastructure.limiter.RateLimitConfiguration
import java.time.Duration
import java.util.function.Supplier

/**
 * Configuration class for setting up Redis-related beans and functionalities.
 *
 * This class is responsible for configuring Redis connection factory, client, and proxy manager with the help of properties
 * injected from `RedisProperties` and rate-limiting configurations provided by `RateLimitConfiguration`.
 *
 * It also provides a supplier for creating a bucket configuration used for rate-limiting setups.
 *
 * @param rateLimitConfiguration Configuration object for rate-limiting settings such as capacity, burst limits, and time intervals.
 * @param redisProperties Configuration properties for Redis, including hostname and port details.
 */
@Configuration
class RedisConfig(
    private val rateLimitConfiguration: RateLimitConfiguration,
    private val redisProperties: RedisProperties
) {
    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        return LettuceConnectionFactory(redisProperties.host, redisProperties.port)
    }

    @Bean
    fun redisClient(): RedisClient {
        return RedisClient.create(RedisURI.create(redisProperties.host, redisProperties.port))
    }

    @Bean
    fun lettuceBasedProxyManager(): ProxyManager<ByteArray> {
        val redisClient = RedisClient.create(RedisURI.Builder.redis(redisProperties.host, redisProperties.port).build())

        return LettuceBasedProxyManager.builderFor(redisClient)
            .withClientSideConfig(
                ClientSideConfig.getDefault().withExpirationAfterWriteStrategy(
                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10))
                )
            ).build()
    }

    @Bean
    fun bucketConfiguration() = Supplier {
        BucketConfiguration.builder().apply {
            addLimit { bandwidth ->
                bandwidth.capacity(rateLimitConfiguration.capacity).refillIntervally(
                    rateLimitConfiguration.capacity,
                    Duration.ofMinutes(rateLimitConfiguration.minutes)
                )
            }

            addLimit { bandwidth ->
                bandwidth.capacity(rateLimitConfiguration.burst.capacity).refillIntervally(
                    rateLimitConfiguration.burst.capacity,
                    Duration.ofSeconds(rateLimitConfiguration.burst.seconds)
                )
            }
        }.build()
    }
}