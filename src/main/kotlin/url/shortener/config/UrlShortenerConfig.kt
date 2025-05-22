package url.shortener.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import url.shortener.application.service.UrlShortenerService
import url.shortener.domain.repository.UrlMappingRepository
import url.shortener.infrastructure.persistence.UrlMappingRepositoryImpl

@Configuration
class UrlShortenerConfig {

    @Value("\${app.base-url}")
    private lateinit var baseUrl: String

    @Bean
    fun urlShortenerService(urlMappingRepository: UrlMappingRepository): UrlShortenerService {
        return UrlShortenerService(urlMappingRepository)
    }

    @Bean
    fun urlMappingRepository(cassandraRepo: UrlMappingRepositoryImpl): UrlMappingRepository {
        return cassandraRepo
    }
}
