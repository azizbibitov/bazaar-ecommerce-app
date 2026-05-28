package com.bazaar.shared.network

import com.bazaar.shared.storage.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal val BazaarJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun createBazaarClient(
    tokenStorage: TokenStorage,
    engine: HttpClientEngine = httpEngine(),
): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(BazaarJson)
    }
    install(BazaarAuthPlugin) {
        this.tokenStorage = tokenStorage
    }
    defaultRequest {
        contentType(ContentType.Application.Json)
    }
}
