import Foundation

/// A tiny first step the user saved from the "I'm Stuck" flow, so it can be reused
/// the next time the same kind of friction comes up. Stored on device only.
nonisolated struct Move: Identifiable, Codable, Hashable, Sendable {
    var id: UUID = UUID()
    /// Raw value of the `StuckReason` the user picked when saving it.
    var reason: String
    var stepText: String
    var createdAt: Date = Date()

    var stuckReason: StuckReason? { StuckReason(rawValue: reason) }
}
