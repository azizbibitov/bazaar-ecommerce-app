package com.bazaar.shared

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform