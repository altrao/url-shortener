package url.shortener.application.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import url.shortener.application.dto.ShortenRequest
import url.shortener.application.service.UrlShortenerService
import url.shortener.domain.model.UrlMapping
import java.time.Instant
import java.time.temporal.ChronoUnit

@WebMvcTest(ShortenController::class)
class ShortenControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @TestConfiguration
    class TestConfig {
        @Bean
        fun urlShortenerService(): UrlShortenerService = mockk()
    }

    @Autowired
    private lateinit var urlShortenerService: UrlShortenerService

    @Test
    fun `should shorten url`() {
        val request = ShortenRequest("https://example.com", null, null)

        every {
            urlShortenerService.shortenUrl("https://example.com", null, null)
        } returns UrlMapping("abc123", "https://example.com")

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isOk
            jsonPath("$.shortUrl").value("http://localhost/abc123")
            jsonPath("$.longUrl").value("https://example.com")
        }
    }

    @Test
    fun `should shorten url with custom alias`() {
        val request = ShortenRequest("https://example.com", "custom", null)

        every {
            urlShortenerService.shortenUrl("https://example.com", "custom", null)
        } returns UrlMapping("custom", "https://example.com")

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isOk
            jsonPath("$.shortUrl").value("http://localhost/custom")
        }
    }

    @Test
    fun `should redirect when expanding url`() {
        every { urlShortenerService.getUrlMapping("abc123") } returns UrlMapping("abc123", "https://example.com")

        mockMvc.perform(get("/abc123")).andExpect {
            status().isMovedPermanently
            header().string("Location", "https://example.com")
        }
    }

    @Test
    fun `should return 404 when url not found`() {
        every { urlShortenerService.getUrlMapping("not-found") } returns null

        mockMvc.perform(get("/not-found")).andExpect {
            status().isNotFound
        }
    }

    @Test
    fun `should handle expiration date`() {
        val expiration = Instant.now().plus(1, ChronoUnit.DAYS)
        val request = ShortenRequest("https://example.com", null, expiration)

        every {
            urlShortenerService.shortenUrl("https://example.com", null, expiration)
        } returns UrlMapping("abc123", "https://example.com", expiration)

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isOk
            jsonPath("$.expirationDate").isNotEmpty
        }
    }
}
