import SwiftUI
import SharedLogic

@MainActor
final class AuthViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let authRepository: AuthRepository

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    func login(email: String, password: String) async -> Bool {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            _ = try await authRepository.login(email: email, password: password)
            return true
        } catch {
            errorMessage = describe(error)
            return false
        }
    }

    func register(email: String, password: String, fullName: String) async -> Bool {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            _ = try await authRepository.register(email: email, password: password, fullName: fullName)
            _ = try await authRepository.login(email: email, password: password)
            return true
        } catch {
            errorMessage = describe(error)
            return false
        }
    }

    private func describe(_ error: Error) -> String {
        if let e = error as? BazaarErrorAuth { return e.message ?? "Invalid credentials." }
        if let e = error as? BazaarErrorConflict { return e.message ?? "Email already registered." }
        if error is BazaarErrorNetwork { return "Network error. Check your connection." }
        return "Something went wrong. Please try again."
    }
}
