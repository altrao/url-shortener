package url.shortener

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.cassandra.CassandraContainer

@TestConfiguration
class CassandraTestContainerConfiguration {
    @Bean
    @ServiceConnection
    fun cassandraContainer(): CassandraContainer {
        return CassandraContainer("cassandra:latest").withInitScript("cassandra-keyspace.cql")
    }
}

@TestConfiguration
class RedisTestContainerConfiguration {
    @Bean
    @ServiceConnection
    fun redisContainer(): RedisContainer {
        return RedisContainer("redis:6.2.6")
    }

    @Bean
    fun registerRedisProperties(redisContainer: RedisContainer): DynamicPropertyRegistrar {
        return DynamicPropertyRegistrar { registry ->
            registry.add("spring.data.redis.host", redisContainer::getRedisHost)
            registry.add("spring.data.redis.port", redisContainer::getRedisPort)
        }
    }
}
