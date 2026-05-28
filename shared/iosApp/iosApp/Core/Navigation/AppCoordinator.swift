import SwiftUI
import SharedLogic

@MainActor
final class AppCoordinator: ObservableObject {
    @Published var isAuthenticated: Bool

    let container: AppContainer

    init(container: AppContainer) {
        self.container = container
        isAuthenticated = container.tokenStorage.getAccessToken() != nil
    }

    func signOut() {
        Task { try? await container.authRepository.logout() }
        isAuthenticated = false
    }
}
