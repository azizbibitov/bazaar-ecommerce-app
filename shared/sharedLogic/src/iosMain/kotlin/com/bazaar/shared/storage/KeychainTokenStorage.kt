@file:Suppress("UNCHECKED_CAST")

package com.bazaar.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.interpretObjCPointerOrNull
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

actual fun createTokenStorage(): TokenStorage = KeychainTokenStorage()

@OptIn(ExperimentalForeignApi::class)
class KeychainTokenStorage : TokenStorage {

    companion object {
        private const val SERVICE = "com.bazaar.shared"
        private const val ACCESS_TOKEN = "access_token"
        private const val REFRESH_TOKEN = "refresh_token"
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        set(ACCESS_TOKEN, accessToken)
        set(REFRESH_TOKEN, refreshToken)
    }

    override fun getAccessToken(): String? = get(ACCESS_TOKEN)
    override fun getRefreshToken(): String? = get(REFRESH_TOKEN)

    override fun clearTokens() {
        delete(ACCESS_TOKEN)
        delete(REFRESH_TOKEN)
    }

    @Suppress("UNCHECKED_CAST")
    private fun set(account: String, value: String) {
        delete(account)
        val data = value.encodeToByteArray().toNSData()
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        )
        SecItemAdd(query as CFDictionaryRef, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun get(account: String): String? = memScoped {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
            kSecReturnData to true,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status != errSecSuccess) return null
        val raw = result.value ?: return null
        val data = interpretObjCPointerOrNull<NSData>(raw.rawValue) ?: return null
        data.toByteArray().decodeToString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun delete(account: String) {
        val query = mapOf<Any?, Any?>(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to SERVICE,
            kSecAttrAccount to account,
        )
        SecItemDelete(query as CFDictionaryRef)
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }

    private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length)
        }
    }
}
