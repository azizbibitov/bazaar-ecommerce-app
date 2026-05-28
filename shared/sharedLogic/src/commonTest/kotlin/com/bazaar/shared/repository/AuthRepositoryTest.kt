package com.bazaar.shared.repository

import com.bazaar.shared.error.BazaarError
import com.bazaar.shared.network.BazaarJson
import com.bazaar.shared.storage.InMemoryTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthRepositoryTest {

    private fun mockClient(status: HttpStatusCode = HttpStatusCode.OK, body: String): HttpClient =
        HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())) }) {
            install(ContentNegotiation) { json(BazaarJson) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }

    @Test
    fun loginSavesTokens() = runTest {
        val storage = InMemoryTokenStorage()
        val repo = AuthRepositoryImpl(
            tokenStorage = storage,
            baseUrl = "http://test",
            client = mockClient(body = """{"access_token":"at","refresh_token":"rt","token_type":"bearer"}"""),
        )

        val tokens = repo.login("user@test.com", "pass")

        assertEquals("at", tokens.accessToken)
        assertEquals("rt", tokens.refreshToken)
        assertEquals("at", storage.getAccessToken())
        assertEquals("rt", storage.getRefreshToken())
    }

    @Test
    fun loginThrowsAuthErrorOn401() = runTest {
        val repo = AuthRepositoryImpl(
            tokenStorage = InMemoryTokenStorage(),
            baseUrl = "http://test",
            client = mockClient(HttpStatusCode.Unauthorized, """{"detail":"Invalid credentials"}"""),
        )

        assertFailsWith<BazaarError.Auth> { repo.login("user@test.com", "wrong") }
    }

    @Test
    fun registerReturnsUserProfile() = runTest {
        val repo = AuthRepositoryImpl(
            tokenStorage = InMemoryTokenStorage(),
            baseUrl = "http://test",
            client = mockClient(
                HttpStatusCode.Created,
                """{"id":"uuid-123","email":"user@test.com","full_name":"Test User","role":"buyer"}""",
            ),
        )

        val profile = repo.register("user@test.com", "pass", "Test User")

        assertEquals("user@test.com", profile.email)
        assertEquals("buyer", profile.role)
    }

    @Test
    fun registerThrowsConflictOn409() = runTest {
        val repo = AuthRepositoryImpl(
            tokenStorage = InMemoryTokenStorage(),
            baseUrl = "http://test",
            client = mockClient(HttpStatusCode.Conflict, """{"detail":"Email already registered"}"""),
        )

        assertFailsWith<BazaarError.Conflict> { repo.register("existing@test.com", "pass", "User") }
    }

    @Test
    fun refreshRotatesTokens() = runTest {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("old_at", "old_rt")
        val repo = AuthRepositoryImpl(
            tokenStorage = storage,
            baseUrl = "http://test",
            client = mockClient(body = """{"access_token":"new_at","refresh_token":"new_rt","token_type":"bearer"}"""),
        )

        val tokens = repo.refresh()

        assertEquals("new_at", tokens.accessToken)
        assertEquals("new_at", storage.getAccessToken())
        assertEquals("new_rt", storage.getRefreshToken())
    }

    @Test
    fun logoutClearsTokens() = runTest {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("at", "rt")
        val repo = AuthRepositoryImpl(
            tokenStorage = storage,
            baseUrl = "http://test",
            client = mockClient(HttpStatusCode.NoContent, ""),
        )

        repo.logout()

        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }

    @Test
    fun meReturnsUserProfile() = runTest {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("at", "rt")
        val repo = AuthRepositoryImpl(
            tokenStorage = storage,
            baseUrl = "http://test",
            client = mockClient(body = """{"id":"uuid-123","email":"user@test.com","full_name":"Test User","role":"buyer"}"""),
        )

        val profile = repo.me()

        assertEquals("user@test.com", profile.email)
        assertEquals("Test User", profile.fullName)
    }
}
