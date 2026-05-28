package com.bazaar.shared.error

sealed class BazaarError(message: String) : Exception(message) {
    class Network(message: String) : BazaarError(message)
    class Auth(message: String) : BazaarError(message)
    class Conflict(message: String) : BazaarError(message)
    class NotFound(message: String) : BazaarError(message)
    class Unknown(message: String) : BazaarError(message)
}
