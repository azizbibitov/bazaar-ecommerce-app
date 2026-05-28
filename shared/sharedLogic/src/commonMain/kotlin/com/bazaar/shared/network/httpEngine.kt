package com.bazaar.shared.network

import io.ktor.client.engine.HttpClientEngine

expect fun httpEngine(): HttpClientEngine
