package url.shortener

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.cassandra.CassandraContainer
import org.testcontainers.containers.GenericContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun cassandraContainer(): CassandraContainer {
        return CassandraContainer("cassandra:latest").withInitScript("cassandra-keyspace.cql")
    }

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        return GenericContainer("redis:latest").withExposedPorts(6379)
    }
}
