import SwiftUI

struct RegisterView: View {
    @StateObject var viewModel: AuthViewModel
    let onRegisterSuccess: () -> Void

    @State private var fullName = ""
    @State private var email = ""
    @State private var password = ""

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.lg) {
                Spacer().frame(height: Spacing.xl)

                Text("Create account")
                    .textStyle(.title)

                Text("Join Bazaar today")
                    .textStyle(.caption)

                Spacer().frame(height: Spacing.sm)

                VStack(spacing: Spacing.md) {
                    TextField("Full name", text: $fullName)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled()
                        .padding(Spacing.md)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))

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
                        if await viewModel.register(email: email, password: password, fullName: fullName) {
                            onRegisterSuccess()
                        }
                    }
                } label: {
                    Group {
                        if viewModel.isLoading {
                            ProgressView().tint(.white)
                        } else {
                            Text("Create Account")
                                .fontWeight(.semibold)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(Spacing.md)
                    .background(Color.bazaarPrimary)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .disabled(viewModel.isLoading || fullName.isEmpty || email.isEmpty || password.isEmpty)

                Button {
                    dismiss()
                } label: {
                    HStack(spacing: Spacing.xs) {
                        Text("Already have an account?")
                            .textStyle(.caption)
                        Text("Sign In")
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
