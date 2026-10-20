import SwiftUI

/// Sign-up step for the 7-day free trial. No account is created — activating
/// the trial is the sign-up, and it happens once per device.
struct TrialStartView: View {
    @Environment(BrainStore.self) private var store
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appeared = false
    @State private var showPaywall = false
    private var purchases: StoreService { StoreService.shared }

    private let timeline: [(String, String, String)] = [
        ("1", "Today", "Full access to everything — brain dump, next step, focus, lists."),
        ("3", "Day 3", "We'll remind you the trial is halfway through. No card needed."),
        ("7", "Day 7", "Trial ends. Unlock once for lifetime access, or stop — your data stays."),
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 26) {
                VStack(alignment: .leading, spacing: 12) {
                    IconWell(symbol: "gift.fill", size: 84)
                        .scaleEffect(appeared ? 1 : 0.85)
                        .opacity(appeared ? 1 : 0)

                    Eyebrow(text: "Free for 7 days")

                    Text("Try ThinkSort free for a week")
                        .font(.largeTitle.bold())
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)

                    Text("No payment now and no card required. After 7 days it's one payment for lifetime access — never a subscription.")
                        .font(.title3)
                        .foregroundStyle(Theme.inkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                VStack(spacing: 0) {
                    ForEach(Array(timeline.enumerated()), id: \.element.0) { index, step in
                        HStack(alignment: .top, spacing: 14) {
                            Text(step.0)
                                .font(.headline)
                                .foregroundStyle(Theme.accent)
                                .frame(width: 40, height: 40)
                                .background(Theme.accentSoft, in: .circle)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(step.1)
                                    .font(.headline)
                                    .foregroundStyle(Theme.ink)
                                Text(step.2)
                                    .font(.footnote)
                                    .foregroundStyle(Theme.inkSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(18)

                        if index < timeline.count - 1 { RowDivider() }
                    }
                }
                .card()

                VStack(spacing: 14) {
                    Button {
                        store.startTrialIfNeeded()
                    } label: {
                        Label("Start My Free Week", systemImage: "arrow.right")
                    }
                    .buttonStyle(PrimaryButtonStyle())

                    Button("I'd rather unlock it now") { showPaywall = true }
                        .font(.subheadline)
                        .foregroundStyle(Theme.inkSecondary)
                        .frame(maxWidth: .infinity)

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
                    .disabled(purchases.isRestoring)
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 24)
            .padding(.bottom, 32)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .task {
            if purchases.lifetimePriceText == nil { await purchases.loadProducts() }
            withAnimation(reduceMotion ? nil : .snappy(duration: 0.4)) { appeared = true }
        }
        .sheet(isPresented: $showPaywall) {
            NavigationStack {
                PaywallView()
            }
        }
        .alert("Something went wrong", isPresented: Binding(
            get: { purchases.error != nil },
            set: { if !$0 { purchases.error = nil } }
        )) {
            Button("OK") { purchases.error = nil }
        } message: {
            Text(purchases.error ?? "")
        }
    }
}

#Preview {
    TrialStartView()
        .environment(BrainStore())
}
