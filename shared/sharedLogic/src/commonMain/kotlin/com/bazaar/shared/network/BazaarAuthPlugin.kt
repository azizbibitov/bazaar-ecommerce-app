package com.bazaar.shared.network

import com.bazaar.shared.storage.TokenStorage
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

class BazaarAuthConfig {
    lateinit var tokenStorage: TokenStorage
}

val BazaarAuthPlugin = createClientPlugin("BazaarAuth", ::BazaarAuthConfig) {
    val config = pluginConfig
    onRequest { request, _ ->
        config.tokenStorage.getAccessToken()?.let { token ->
            request.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}
