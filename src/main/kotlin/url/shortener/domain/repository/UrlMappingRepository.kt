package url.shortener.domain.repository

import url.shortener.domain.model.UrlMapping

interface UrlMappingRepository {
    fun find(shortUrl: String): UrlMapping?
    fun save(urlMapping: UrlMapping): UrlMapping
    fun delete(shortUrl: String): Boolean
    fun exists(shortUrl: String): Boolean
}
