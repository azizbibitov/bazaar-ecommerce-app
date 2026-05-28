package com.bazaar.shared.storage

interface TokenStorage {
    fun saveTokens(accessToken: String, refreshToken: String)
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun clearTokens()
}

expect fun createTokenStorage(): TokenStorage
