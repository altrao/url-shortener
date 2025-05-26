package url.shortener.configuration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class MetricsTestConfiguration {
    @Bean
    fun metricsContainer(): MeterRegistry {
        return SimpleMeterRegistry()
    }
}
