import Foundation

/// Whether the user can use ThinkSort right now: a 7-day free trial they opt
/// into, then a single lifetime unlock purchase.
nonisolated enum AccessState: Equatable, Sendable {
    /// Onboarding finished but the trial was never activated.
    case trialNotStarted
    case trial(daysRemaining: Int)
    case expired
    case unlocked

    var isUsable: Bool {
        switch self {
        case .trial, .unlocked: true
        case .trialNotStarted, .expired: false
        }
    }
}
