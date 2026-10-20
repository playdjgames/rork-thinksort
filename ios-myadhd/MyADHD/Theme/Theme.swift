import SwiftUI
import UIKit

/// Central design tokens for ThinkSort. Fresh, airy, calm — never clinical.
enum Theme {
    static let canvas = adaptive(light: 0xF4F8F7, dark: 0x101614)
    static let surface = adaptive(light: 0xFFFFFF, dark: 0x1A2220)
    static let surfaceMuted = adaptive(light: 0xEAF3F0, dark: 0x202B28)
    static let ink = adaptive(light: 0x1F2A28, dark: 0xECF2F0)
    static let inkSecondary = adaptive(light: 0x5B6E6A, dark: 0x9BAFAA)
    static let inkTertiary = adaptive(light: 0x8CA09B, dark: 0x6F837E)
    static let accent = adaptive(light: 0x2E9E8B, dark: 0x45C0A9)
    static let accentSoft = adaptive(light: 0xE2F1ED, dark: 0x1F3A34)
    static let coral = adaptive(light: 0xD9674A, dark: 0xF0997F)
    static let coralSoft = adaptive(light: 0xFBEBE6, dark: 0x3A241E)
    static let hairline = adaptive(light: 0xE3ECE9, dark: 0x2A3733)

    static let cardRadius: CGFloat = 22
    static let gutter: CGFloat = 20

    static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(UIColor { traits in
            UIColor(rgbValue: traits.userInterfaceStyle == .dark ? dark : light)
        })
    }
}

extension UIColor {
    convenience init(rgbValue: UInt32) {
        self.init(
            red: CGFloat((rgbValue >> 16) & 0xFF) / 255,
            green: CGFloat((rgbValue >> 8) & 0xFF) / 255,
            blue: CGFloat(rgbValue & 0xFF) / 255,
            alpha: 1
        )
    }
}

struct CardBackground: ViewModifier {
    var color: Color
    var radius: CGFloat

    func body(content: Content) -> some View {
        content
            .background(color, in: .rect(cornerRadius: radius))
            .shadow(color: Color.black.opacity(0.05), radius: 12, y: 4)
    }
}

extension View {
    func card(_ color: Color = Theme.surface, radius: CGFloat = Theme.cardRadius) -> some View {
        modifier(CardBackground(color: color, radius: radius))
    }
}

/// Small uppercase tracked label used above focal content ("NEXT UP", "TODAY").
struct Eyebrow: View {
    let text: String
    var color: Color = Theme.inkSecondary

    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.semibold))
            .tracking(1.1)
            .foregroundStyle(color)
    }
}

/// Circular tinted well holding an SF Symbol.
struct IconWell: View {
    let symbol: String
    var size: CGFloat = 56
    var tint: Color = Theme.accent
    var background: Color = Theme.accentSoft

    var body: some View {
        Circle()
            .fill(background)
            .frame(width: size, height: size)
            .overlay {
                Image(systemName: symbol)
                    .font(.system(size: size * 0.42, weight: .medium))
                    .foregroundStyle(tint)
            }
            .accessibilityHidden(true)
    }
}
