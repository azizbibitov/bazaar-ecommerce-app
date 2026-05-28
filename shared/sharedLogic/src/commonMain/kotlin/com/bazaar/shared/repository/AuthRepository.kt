package com.bazaar.shared.repository

import com.bazaar.shared.models.AuthTokens
import com.bazaar.shared.models.UserProfile

interface AuthRepository {
    suspend fun register(email: String, password: String, fullName: String): UserProfile
    suspend fun login(email: String, password: String): AuthTokens
    suspend fun refresh(): AuthTokens
    suspend fun logout()
    suspend fun me(): UserProfile
}
