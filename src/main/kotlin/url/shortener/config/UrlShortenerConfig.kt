package url.shortener.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import url.shortener.application.service.UrlShortenerService
import url.shortener.domain.repository.UrlMappingRepository

@Configuration
class UrlShortenerConfig {
    @Bean
    fun urlShortenerService(urlMappingRepository: UrlMappingRepository): UrlShortenerService {
        return UrlShortenerService(urlMappingRepository)
    }
}
