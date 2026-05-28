package com.bazaar.shared.repository

import com.bazaar.shared.error.BazaarError
import com.bazaar.shared.models.AuthTokens
import com.bazaar.shared.models.UserProfile
import com.bazaar.shared.network.ApiError
import com.bazaar.shared.network.LoginRequest
import com.bazaar.shared.network.RefreshRequest
import com.bazaar.shared.network.RegisterRequest
import com.bazaar.shared.network.createBazaarClient
import com.bazaar.shared.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRepositoryImpl(
    private val tokenStorage: TokenStorage,
    private val baseUrl: String = "http://localhost:8000",
    private val client: HttpClient = createBazaarClient(tokenStorage),
) : AuthRepository {

    private val refreshMutex = Mutex()

    override suspend fun register(email: String, password: String, fullName: String): UserProfile {
        val response = client.post("$baseUrl/auth/register") {
            setBody(RegisterRequest(email, password, fullName))
        }
        return response.toResult()
    }

    override suspend fun login(email: String, password: String): AuthTokens {
        val response = client.post("$baseUrl/auth/login") {
            setBody(LoginRequest(email, password))
        }
        val tokens: AuthTokens = response.toResult()
        tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
        return tokens
    }

    override suspend fun refresh(): AuthTokens = refreshMutex.withLock {
        val refreshToken = tokenStorage.getRefreshToken()
            ?: throw BazaarError.Auth("No refresh token stored")
        val response = client.post("$baseUrl/auth/refresh") {
            setBody(RefreshRequest(refreshToken))
        }
        val tokens: AuthTokens = response.toResult()
        tokenStorage.saveTokens(tokens.accessToken, tokens.refreshToken)
        tokens
    }

    override suspend fun logout() {
        try {
            client.post("$baseUrl/auth/logout")
        } catch (ignored: Exception) {
        } finally {
            tokenStorage.clearTokens()
        }
    }

    override suspend fun me(): UserProfile {
        val response = client.get("$baseUrl/auth/me")
        return response.toResult()
    }

    private suspend inline fun <reified T> HttpResponse.toResult(): T {
        if (status.isSuccess()) return body()
        val detail = runCatching { body<ApiError>().detail }.getOrElse { status.description }
        throw when (status.value) {
            401 -> BazaarError.Auth(detail)
            409 -> BazaarError.Conflict(detail)
            404 -> BazaarError.NotFound(detail)
            else -> BazaarError.Unknown("HTTP ${status.value}: $detail")
        }
    }
}

fun createAuthRepository(
    tokenStorage: TokenStorage,
    baseUrl: String = "http://localhost:8000",
): AuthRepository = AuthRepositoryImpl(tokenStorage, baseUrl)
