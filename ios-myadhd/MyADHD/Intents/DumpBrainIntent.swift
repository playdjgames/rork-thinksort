import AppIntents
import Foundation
import SwiftUI

/// Opens ThinkSort straight onto an empty brain dump with the keyboard up.
/// Run by the home-screen and Lock Screen "Dump" widgets, and available in Shortcuts.
///
/// The widget extension declares an identically named copy so its buttons can
/// reference it; because `openAppWhenRun` is true, the system launches the app and
/// performs this app-side version.
nonisolated struct DumpBrainIntent: AppIntent {
    static var title: LocalizedStringResource = "Dump"
    static var description = IntentDescription("Open ThinkSort on a fresh brain dump.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        DumpBrainLauncher.shared.request()
        return .result()
    }
}

/// Holds a "open the brain dump" request until the app's root is ready to apply it,
/// so it works on a cold launch (e.g. right after a device restart) as well as warm.
@Observable
final class DumpBrainLauncher {
    static let shared = DumpBrainLauncher()

    /// Deep link used by the widgets as a fallback when the tap lands outside the button.
    static let url = URL(string: "thinksort://dump")!

    private(set) var isPending = false

    private init() {}

    func request() { isPending = true }

    func consume() -> Bool {
        guard isPending else { return false }
        isPending = false
        return true
    }
}
