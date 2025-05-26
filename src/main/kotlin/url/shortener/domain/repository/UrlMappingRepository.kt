package url.shortener.domain.repository

import url.shortener.domain.model.UrlMapping

interface UrlMappingRepository {
    fun find(shortUrl: String): UrlMapping?
    fun save(urlMapping: UrlMapping): UrlMapping
    fun exists(shortUrl: String): Boolean
    fun findAllExpired(): List<UrlMapping>
    fun deleteAll(urls: List<UrlMapping>): Int
}
