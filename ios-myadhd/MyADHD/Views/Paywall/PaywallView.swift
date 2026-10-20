import SwiftUI

/// The single unlock screen. Shown as a hard gate when the trial has ended, and
/// as a dismissible upsell from Settings during the trial.
struct PaywallView: View {
    /// When true the screen cannot be dismissed — the trial is over.
    var isHardGate: Bool = false

    @Environment(BrainStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appeared = false
    private var purchases: StoreService { StoreService.shared }

    private let benefits: [(String, String, String)] = [
        ("mic.fill", "Dump your brain", "Ramble in one box and get back sorted tasks, prep, lists and ideas."),
        ("arrow.right.circle.fill", "One next step", "ThinkSort always answers “what do I do now?” — and why it matters."),
        ("bookmark.fill", "Never lose your place", "Pick up exactly where you stopped, even days later."),
        ("target", "Focus & unstick", "Timed sessions, and a tiny first step whenever you freeze."),
        ("lock.shield", "Private by design", "Everything stays on your iPhone. No account, no cloud, no ads."),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                header
                benefitList
                purchaseBlock
                footer
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, isHardGate ? 32 : 12)
            .padding(.bottom, 32)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .toolbar {
            if !isHardGate {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .navigationTitle(isHardGate ? "" : "ThinkSort Lifetime")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(isHardGate)
        .task {
            if purchases.lifetimePriceText == nil { await purchases.loadProducts() }
            withAnimation(reduceMotion ? nil : .snappy(duration: 0.4)) { appeared = true }
        }
        .onChange(of: purchases.isUnlocked) { _, isUnlocked in
            if isUnlocked, !isHardGate { dismiss() }
        }
        .alert("Something went wrong", isPresented: errorBinding) {
            Button("OK") { purchases.error = nil }
        } message: {
            Text(purchases.error ?? "")
        }
    }

    // MARK: - Pieces

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            IconWell(symbol: "checkmark.seal.fill", size: 84)
                .scaleEffect(appeared ? 1 : 0.85)
                .opacity(appeared ? 1 : 0)

            Eyebrow(text: isHardGate ? "Your free trial has ended" : "One payment, yours forever")

            Text(isHardGate ? "Keep your external brain" : "ThinkSort Lifetime")
                .font(.largeTitle.bold())
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)

            Text(isHardGate
                 ? "Your tasks and notes are safe on this device. Unlock once to get straight back to them."
                 : "Unlock everything with a single payment. No subscription, no renewals.")
                .font(.title3)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var benefitList: some View {
        VStack(spacing: 0) {
            ForEach(Array(benefits.enumerated()), id: \.element.0) { index, benefit in
                HStack(alignment: .top, spacing: 14) {
                    IconWell(symbol: benefit.0, size: 40)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(benefit.1)
                            .font(.headline)
                            .foregroundStyle(Theme.ink)
                        Text(benefit.2)
                            .font(.footnote)
                            .foregroundStyle(Theme.inkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: 0)
                }
                .padding(18)

                if index < benefits.count - 1 { RowDivider() }
            }
        }
        .card()
    }

    private var purchaseBlock: some View {
        VStack(spacing: 14) {
            Button {
                Task { await purchases.purchaseLifetime() }
            } label: {
                if purchases.isPurchasing {
                    ProgressView().tint(.white)
                } else {
                    Text(priceLabel)
                }
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(purchases.isPurchasing || purchases.isRestoring)

            Text("One-time payment · Lifetime access · No subscription")
                .font(.footnote)
                .foregroundStyle(Theme.inkSecondary)
                .multilineTextAlignment(.center)

            Button {
                Task { await purchases.restore() }
            } label: {
                if purchases.isRestoring {
                    ProgressView()
                } else {
                    Text("Restore Purchase")
                }
            }
            .buttonStyle(SoftButtonStyle(isCompact: true))
            .disabled(purchases.isPurchasing || purchases.isRestoring)
        }
    }

    private var footer: some View {
        VStack(spacing: 8) {
            Text("Already bought ThinkSort on another iPhone? Tap Restore Purchase.")
                .font(.footnote)
                .foregroundStyle(Theme.inkTertiary)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: 18) {
                Link("Privacy Policy", destination: URL(string: "https://playdjgames.com/privacy")!)
                Link("Terms of Use", destination: URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!)
            }
            .font(.footnote)
            .foregroundStyle(Theme.accent)
        }
        .frame(maxWidth: .infinity)
    }

    private var priceLabel: String {
        if let price = purchases.lifetimePriceText {
            return "Unlock Forever · \(price)"
        }
        return "Unlock Forever"
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { purchases.error != nil },
            set: { if !$0 { purchases.error = nil } }
        )
    }
}

#Preview {
    NavigationStack {
        PaywallView(isHardGate: true)
            .environment(BrainStore())
    }
}
