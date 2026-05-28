import SwiftUI

struct RootView: View {
    @EnvironmentObject var coordinator: AppCoordinator
    @State private var authPath = NavigationPath()

    var body: some View {
        if coordinator.isAuthenticated {
            Text("Main") // placeholder - replaced when catalog is built
        } else {
            NavigationStack(path: $authPath) {
                LoginView(
                    viewModel: AuthViewModel(authRepository: coordinator.container.authRepository),
                    onLoginSuccess: { coordinator.isAuthenticated = true },
                    onNavigateToRegister: { authPath.append(AuthRoute.register) }
                )
                .navigationDestination(for: AuthRoute.self) { route in
                    switch route {
                    case .register:
                        RegisterView(
                            viewModel: AuthViewModel(authRepository: coordinator.container.authRepository),
                            onRegisterSuccess: { coordinator.isAuthenticated = true }
                        )
                    }
                }
            }
        }
    }
}
