import SwiftUI

struct LoginView: View {
    @StateObject var viewModel: AuthViewModel
    let onLoginSuccess: () -> Void
    let onNavigateToRegister: () -> Void

    @State private var email = ""
    @State private var password = ""

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                Spacer().frame(height: Spacing.xl)

                Text("Welcome back")
                    .textStyle(.title)

                Text("Sign in to your account")
                    .textStyle(.caption)

                Spacer().frame(height: Spacing.sm)

                VStack(spacing: Spacing.md) {
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .padding(Spacing.md)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                    SecureField("Password", text: $password)
                        .padding(Spacing.md)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                if let error = viewModel.errorMessage {
                    Text(error)
                        .textStyle(.caption)
                        .foregroundColor(.bazaarError)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                Button {
                    Task {
                        if await viewModel.login(email: email, password: password) {
                            onLoginSuccess()
                        }
                    }
                } label: {
                    Group {
                        if viewModel.isLoading {
                            ProgressView().tint(.white)
                        } else {
                            Text("Sign In")
                                .fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(Spacing.md)
                    .background(Color.bazaarPrimary)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .disabled(viewModel.isLoading || email.isEmpty || password.isEmpty)

                Button {
                    onNavigateToRegister()
                } label: {
                    HStack(spacing: Spacing.xs) {
                        Text("Don't have an account?")
                            .textStyle(.caption)
                        Text("Register")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.bazaarPrimary)
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
        .navigationBarHidden(true)
    }
}
