import SwiftUI

enum BazaarTextStyle {
    case title, headline, body, caption
}

struct BazaarTextStyleModifier: ViewModifier {
    let style: BazaarTextStyle

    func body(content: Content) -> some View {
        switch style {
        case .title:
            content.font(.title2).fontWeight(.bold)
        case .headline:
            content.font(.headline).fontWeight(.semibold)
        case .body:
            content.font(.body)
        case .caption:
            content.font(.caption).foregroundColor(.secondary)
        }
    }
}

extension View {
    func textStyle(_ style: BazaarTextStyle) -> some View {
        modifier(BazaarTextStyleModifier(style: style))
    }
}
