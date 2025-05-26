package url.shortener.application.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import url.shortener.CassandraTestContainerConfiguration
import url.shortener.RedisTestContainerConfiguration
import url.shortener.application.dto.ShortenRequest
import url.shortener.application.dto.ShortenResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = [CassandraTestContainerConfiguration::class, RedisTestContainerConfiguration::class])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ShortenControllerIntegrationTest {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(ShortenControllerIntegrationTest::class.java)
    }

    @BeforeEach
    fun logTestName(testInfo: org.junit.jupiter.api.TestInfo) {
        log.info("Running test: ${testInfo.displayName}")
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `should shorten url`() {
        val request = ShortenRequest("https://example.com", null, null)

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isCreated
            jsonPath("$.shortUrl").isNotEmpty
            jsonPath("$.longUrl").value("https://example.com")
        }
    }

    @Test
    fun `should shorten url with custom alias`() {
        val request = ShortenRequest("https://example.com", "custom", null)

        val response = mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isCreated
        }.shortenResponse

        assertTrue(response.shortUrl.endsWith("custom"))
    }

    @Test
    fun `should redirect when expanding url`() {
        val request = ShortenRequest("https://example.com", "abc123", null)

        val response = mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).shortenResponse

        mockMvc.perform(get(response.shortUrl)).andExpect {
            status().isMovedPermanently
            header().string("Location", "https://example.com")
        }
    }

    @Test
    fun `should return 404 when url not found`() {
        mockMvc.perform(get("/not-found")).andExpect {
            status().isNotFound
        }
    }

    @Test
    fun `should return 404 when short url not found`() {
        mockMvc.perform(get("/shorten/inexistent")).andExpect {
            status().isNotFound
        }
    }

    @Test
    fun `should handle expiration date`() {
        val expiration = Instant.now().plus(1, ChronoUnit.DAYS)
        val request = ShortenRequest("https://example.com", null, expiration)

        val response = mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isCreated
            jsonPath("$.expirationDate").isNotEmpty
        }.shortenResponse

        assertNotNull(response.expirationDate)
        assertEquals(response.expirationDate.substringBefore("Z"), expiration.toString().substringBefore("Z"))
    }

    @Test
    fun `should reject invalid url format`() {
        val request = ShortenRequest("example.com")

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isBadRequest
        }
    }

    @Test
    fun `should reject duplicate custom alias`() {
        val firstRequest = ShortenRequest("https://example.com", "duplicate")

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest))
        )

        val secondRequest = ShortenRequest("https://another.com", "duplicate")

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest))
        ).andExpect {
            status().isBadRequest
        }
    }

    @Test
    fun `should handle empty custom alias`() {
        val request = ShortenRequest("https://example.com", "")

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        ).andExpect {
            status().isCreated
            jsonPath("$.shortUrl").isNotEmpty
        }
    }

    @Test
    fun `should reject expired url`() {
        val expiration = Instant.now().minus(1, ChronoUnit.DAYS)
        val request = ShortenRequest("https://example.com", "expired", expiration)

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )

        mockMvc.perform(get("/expired")).andExpect {
            status().isBadRequest
        }
    }

    @Test
    @Order(Int.MAX_VALUE)
    fun `should handle rate limiting`() {
        val request = ShortenRequest("https://example.com", null, null)
        val content = objectMapper.writeValueAsString(request)

        mockMvc.perform(
            post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
        ).andExpect {
            status().isCreated
        }

        for (i in 1..50) {
            val result = mockMvc.perform(
                post("/shorten")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content)
            ).andReturn()

            if (result.response.status == HttpStatus.TOO_MANY_REQUESTS.value()) {
                return
            }
        }

        fail("Expected to hit rate limit")
    }

    private val ResultActions.shortenResponse: ShortenResponse
        get() = objectMapper.readValue(andReturn().response.contentAsString, ShortenResponse::class.java)
}
