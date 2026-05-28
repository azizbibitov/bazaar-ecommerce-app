import Foundation
import SharedLogic

final class AppContainer {
    let tokenStorage: KeychainTokenStorage
    let authRepository: AuthRepository

    init(baseUrl: String = APIConfig.baseUrl) {
        tokenStorage = KeychainTokenStorage()
        authRepository = AuthRepositoryImplKt.createAuthRepository(
            tokenStorage: tokenStorage,
            baseUrl: baseUrl
        )
    }
}
