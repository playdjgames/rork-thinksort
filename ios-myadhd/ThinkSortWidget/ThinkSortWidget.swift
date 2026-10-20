import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Intent (widget-side declaration)

/// Mirrors the app's `DumpBrainIntent` (same name and shape) so widget buttons can
/// reference it. `openAppWhenRun` makes the system launch ThinkSort and perform the
/// app's version, which routes to the brain dump or, after the trial, the paywall.
nonisolated struct DumpBrainIntent: AppIntent {
    static var title: LocalizedStringResource = "Dump"
    static var description = IntentDescription("Open ThinkSort on a fresh brain dump.")
    static var openAppWhenRun: Bool = true

    func perform() async throws -> some IntentResult {
        .result()
    }
}

// MARK: - Static timeline

/// The widget never reads or shows user content, so a single entry that never
/// refreshes is all it needs. It survives restarts because nothing depends on state.
nonisolated struct DumpProvider: TimelineProvider {
    func placeholder(in context: Context) -> DumpEntry { DumpEntry(date: .now) }

    func getSnapshot(in context: Context, completion: @escaping (DumpEntry) -> Void) {
        completion(DumpEntry(date: .now))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<DumpEntry>) -> Void) {
        completion(Timeline(entries: [DumpEntry(date: .now)], policy: .never))
    }
}

nonisolated struct DumpEntry: TimelineEntry {
    let date: Date
}

// MARK: - Palette (from the design contract)

private enum WidgetPalette {
    static let accent = Color(light: 0x2E9E8B, dark: 0x45C0A9)
    static let accentSoft = Color(light: 0xE2F1ED, dark: 0x1F3A34)
    static let surface = Color(light: 0xFFFFFF, dark: 0x1A2220)
    static let ink = Color(light: 0x1F2A28, dark: 0xECF2F0)
}

private extension Color {
    init(light: UInt32, dark: UInt32) {
        self.init(uiColor: UIColor { traits in
            let hex = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((hex >> 16) & 0xFF) / 255,
                green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}

private let dumpURL = URL(string: "thinksort://dump")!

// MARK: - Home Screen (systemSmall)

struct DumpHomeView: View {
    var body: some View {
        Button(intent: DumpBrainIntent()) {
            VStack(alignment: .leading, spacing: 0) {
                ZStack {
                    Circle().fill(WidgetPalette.accent)
                    Image(systemName: "mic.fill")
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(.white)
                }
                .frame(width: 52, height: 52)
                .widgetAccentable()

                Spacer(minLength: 0)

                Text("Dump")
                    .font(.title.bold())
                    .foregroundStyle(WidgetPalette.ink)
                    .minimumScaleFactor(0.7)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Dump")
        .accessibilityHint("Opens ThinkSort on a new brain dump")
    }
}

struct DumpHomeWidget: Widget {
    let kind = "ThinkSortDumpWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DumpProvider()) { _ in
            DumpHomeView()
                .widgetURL(dumpURL)
                .containerBackground(for: .widget) {
                    ZStack(alignment: .bottomTrailing) {
                        WidgetPalette.surface
                        Circle()
                            .fill(WidgetPalette.accentSoft)
                            .frame(width: 140, height: 140)
                            .offset(x: 50, y: 50)
                    }
                }
        }
        .configurationDisplayName("Dump")
        .description("One tap to empty your head into ThinkSort.")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Lock Screen (accessoryCircular + accessoryRectangular)

struct DumpAccessoryView: View {
    @Environment(\.widgetFamily) private var family

    var body: some View {
        Button(intent: DumpBrainIntent()) {
            switch family {
            case .accessoryRectangular:
                HStack(spacing: 8) {
                    Image(systemName: "mic.fill")
                        .font(.title3.weight(.semibold))
                    Text("Dump")
                        .font(.headline)
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            default:
                ZStack {
                    AccessoryWidgetBackground()
                    VStack(spacing: 1) {
                        Image(systemName: "mic.fill")
                            .font(.title3.weight(.semibold))
                        Text("Dump")
                            .font(.caption2.weight(.semibold))
                    }
                }
            }
        }
        .buttonStyle(.plain)
        .widgetAccentable()
        .accessibilityLabel("Dump")
        .accessibilityHint("Opens ThinkSort on a new brain dump")
    }
}

struct DumpLockScreenWidget: Widget {
    let kind = "ThinkSortDumpLockWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: DumpProvider()) { _ in
            DumpAccessoryView()
                .widgetURL(dumpURL)
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("Dump")
        .description("Start a brain dump from your Lock Screen.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular])
    }
}

#Preview(as: .systemSmall) {
    DumpHomeWidget()
} timeline: {
    DumpEntry(date: .now)
}

#Preview(as: .accessoryCircular) {
    DumpLockScreenWidget()
} timeline: {
    DumpEntry(date: .now)
}
