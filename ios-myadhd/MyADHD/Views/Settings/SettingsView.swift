import SwiftUI
import UserNotifications
import EventKit
import CoreLocation
import UniformTypeIdentifiers

struct SettingsView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    @State private var notificationStatus: UNAuthorizationStatus = .notDetermined
    @State private var speechStatusText: String = "Not set up yet"
    @State private var showExport = false
    @State private var exportText: String = ""
    @State private var showDeleteConfirm = false
    @State private var showSiriHelp = false
    @State private var showPaywall = false
    @State private var backupFile: BackupFile?
    @State private var showBackupPicker = false
    @State private var pendingBackup: Data?
    @State private var showReplaceConfirm = false
    @State private var showBackupError = false

    private var purchases: StoreService { StoreService.shared }

    private let leadOptions = [10, 15, 30, 45, 60, 120]

    var body: some View {
        @Bindable var store = store

        NavigationStack {
            Form {
                accessSection
                accountSection(store: store)
                notificationsSection(store: store)
                quietHoursSection(store: store)
                siriSection
                calendarSection(store: store)
                locationSection(store: store)
                voiceSection(store: store)
                appearanceSection(store: store)
                focusSection(store: store)
                movesSection
                privacySection
                backupSection
                dataSection
                aboutSection
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task {
                notificationStatus = await NotificationService.shared.authorizationStatus()
                speechStatusText = SpeechService().permissionDescription
                CalendarService.shared.refreshStatus()
            }
            .sheet(isPresented: $showExport) {
                ExportView(text: exportText)
            }
            .sheet(isPresented: $showSiriHelp) {
                SiriHelpView()
            }
            .sheet(isPresented: $showPaywall) {
                NavigationStack {
                    PaywallView()
                }
            }
            .alert("Delete everything?", isPresented: $showDeleteConfirm) {
                Button("Delete all data", role: .destructive) {
                    store.deleteAllData()
                    dismiss()
                    router.flash("All data deleted.")
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Every task, note, list and brain dump is removed from this device. This can't be undone.")
            }
        }
    }

    // MARK: - Sections

    private var accessSection: some View {
        let access = purchases.accessState(trialStartedAt: store.settings.trialStartedAt)
        return Section {
            switch access {
            case .unlocked:
                LabeledContent("ThinkSort", value: "Lifetime access")
            case .trial(let daysRemaining):
                LabeledContent(
                    "Free trial",
                    value: "\(daysRemaining) day\(daysRemaining == 1 ? "" : "s") left"
                )
                Button {
                    showPaywall = true
                } label: {
                    Label("Unlock lifetime access", systemImage: "checkmark.seal")
                }
            case .trialNotStarted, .expired:
                Button {
                    showPaywall = true
                } label: {
                    Label("Unlock lifetime access", systemImage: "checkmark.seal")
                }
            }

            if !purchases.isUnlocked {
                Button {
                    Task { await purchases.restore() }
                } label: {
                    Label("Restore purchase", systemImage: "arrow.clockwise")
                }
                .disabled(purchases.isRestoring)
            }
        } header: {
            Text("Access")
        } footer: {
            Text(purchases.isUnlocked
                 ? "Thanks for buying ThinkSort. It's yours for good — no subscription, no renewals."
                 : "ThinkSort is a one-time purchase. There is no subscription and nothing renews.")
        }
    }

    private func accountSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section {
            TextField("What should ThinkSort call you?", text: $store.settings.displayName)
        } header: {
            Text("Account")
        } footer: {
            Text("ThinkSort works entirely on this device. There's no account and nothing is uploaded.")
        }
    }

    private func notificationsSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section {
            Toggle("Reminders", isOn: $store.settings.notificationsEnabled)
            Toggle("Preparation nudges", isOn: $store.settings.preparationRemindersEnabled)
            Picker("How early to warn me", selection: $store.settings.defaultReminderLeadMinutes) {
                ForEach(leadOptions, id: \.self) { minutes in
                    Text(minutes >= 60 ? "\(minutes / 60) hour\(minutes == 60 ? "" : "s") before" : "\(minutes) min before")
                        .tag(minutes)
                }
            }
            Picker("How often", selection: $store.settings.reminderFrequency) {
                ForEach(ReminderFrequency.allCases) { frequency in
                    Text(frequency.label).tag(frequency)
                }
            }

            switch notificationStatus {
            case .notDetermined:
                Button("Set Up Notifications") {
                    Task {
                        await NotificationService.shared.requestAuthorization()
                        notificationStatus = await NotificationService.shared.authorizationStatus()
                    }
                }
            case .denied:
                LabeledContent("System permission", value: "Off")
                openSystemSettingsLink
            default:
                LabeledContent("System permission", value: "On")
            }
        } header: {
            Text("Notifications")
        } footer: {
            Text("ThinkSort keeps the number of alerts low on purpose. Nothing fires during quiet hours — it waits until they end.")
        }
    }

    private func quietHoursSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section("Quiet hours") {
            Toggle("Hold reminders overnight", isOn: $store.settings.quietHours.isEnabled)
            if store.settings.quietHours.isEnabled {
                Picker("From", selection: $store.settings.quietHours.start) {
                    ForEach(Array(stride(from: 0, to: 24 * 60, by: 30)), id: \.self) { minutes in
                        Text(Self.timeLabel(minutes)).tag(minutes)
                    }
                }
                Picker("Until", selection: $store.settings.quietHours.end) {
                    ForEach(Array(stride(from: 0, to: 24 * 60, by: 30)), id: \.self) { minutes in
                        Text(Self.timeLabel(minutes)).tag(minutes)
                    }
                }
            }
        }
    }

    private var siriSection: some View {
        Section {
            Button {
                showSiriHelp = true
            } label: {
                Label("What can I say to Siri?", systemImage: "mic.circle")
            }
            Link(destination: URL(string: "shortcuts://")!) {
                Label("Open Shortcuts", systemImage: "square.stack.3d.up")
            }
        } header: {
            Text("Siri & Shortcuts")
        } footer: {
            Text("ThinkSort's actions appear in the Shortcuts app, so you can build your own routines around them.")
        }
    }

    private func calendarSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section {
            Toggle("Use my calendar", isOn: Binding(
                get: { store.settings.calendarEnabled },
                set: { newValue in
                    store.settings.calendarEnabled = newValue
                    if newValue {
                        Task {
                            await CalendarService.shared.requestAccess()
                            store.settings.calendarEnabled = CalendarService.shared.isAuthorized
                        }
                    }
                }
            ))
            LabeledContent("Access", value: CalendarService.shared.isAuthorized ? "On" : "Off")
            if isCalendarDenied {
                openSystemSettingsLink
            }
        } header: {
            Text("Calendar")
        } footer: {
            Text("ThinkSort reads your events to know how much free time you have. It never creates or changes an event.")
        }
    }

    private func locationSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section {
            Toggle("Location reminders", isOn: Binding(
                get: { store.settings.locationRemindersEnabled },
                set: { newValue in
                    store.settings.locationRemindersEnabled = newValue
                    if newValue { LocationService.shared.requestAlways() }
                    else { LocationService.shared.stopMonitoringAll() }
                }
            ))
            LabeledContent("Access", value: LocationService.shared.isAuthorized ? "On" : "Off")
            if isLocationDenied {
                openSystemSettingsLink
            }
        } header: {
            Text("Location")
        } footer: {
            Text("Used only for “remind me when I get to…” style reminders. If it's off, those become normal reminders instead.")
        }
    }

    private func voiceSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section {
            Toggle("Voice input", isOn: $store.settings.voiceInputEnabled)
            LabeledContent("Speech recognition", value: speechStatusText)
            if speechStatusText == "Off" {
                openSystemSettingsLink
            }
        } header: {
            Text("Voice")
        } footer: {
            Text("Speech is transcribed on device where your iPhone supports it. You can always type instead.")
        }
    }

    private var isCalendarDenied: Bool {
        let status = CalendarService.shared.authorizationStatus
        return status == .denied || status == .restricted
    }

    private var isLocationDenied: Bool {
        let status = LocationService.shared.authorizationStatus
        return status == .denied || status == .restricted
    }

    /// Shown only after the user has declined in the system prompt, so they can change their mind.
    @ViewBuilder
    private var openSystemSettingsLink: some View {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            Link(destination: url) {
                Label("Change in iPhone Settings", systemImage: "gear")
            }
        }
    }

    private func appearanceSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section("Appearance & feedback") {
            Picker("Theme", selection: $store.settings.appearance) {
                ForEach(AppearanceMode.allCases) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            Toggle("Haptics", isOn: $store.settings.hapticsEnabled)
            Toggle("Reduce animation", isOn: $store.settings.reduceVisualMotion)
        }
    }

    private func focusSection(store: BrainStore) -> some View {
        @Bindable var store = store
        return Section("Focus") {
            Picker("Default session", selection: $store.settings.defaultFocusMinutes) {
                ForEach([5, 15, 25, 45], id: \.self) { minutes in
                    Text("\(minutes) minutes").tag(minutes)
                }
            }
        }
    }

    private var privacySection: some View {
        Section {
            NavigationLink {
                PrivacyView()
            } label: {
                Label("How your data is handled", systemImage: "lock.shield")
            }
        } header: {
            Text("Privacy")
        }
    }

    private var movesSection: some View {
        Section {
            NavigationLink {
                MyMovesView()
            } label: {
                LabeledContent {
                    Text("\(store.moves.count)")
                } label: {
                    Label("My Moves", systemImage: "bookmark")
                }
            }
        } footer: {
            Text("Tiny first steps you saved from I'm Stuck.")
        }
    }

    private var backupSection: some View {
        Section {
            Button {
                exportBackup()
            } label: {
                Label("Export backup", systemImage: "externaldrive")
            }
            Button {
                showBackupPicker = true
            } label: {
                Label("Import backup", systemImage: "square.and.arrow.down")
            }
        } header: {
            Text("Backup")
        } footer: {
            Text("A single file you keep yourself. It stays on your device unless you choose where to send it.")
        }
        .sheet(item: $backupFile) { file in
            ActivityShareSheet(items: [file.url])
                .presentationDetents([.medium, .large])
                .ignoresSafeArea()
        }
        .fileImporter(isPresented: $showBackupPicker, allowedContentTypes: [.json]) { result in
            handlePickedBackup(result)
        }
        .alert("Replace everything?", isPresented: $showReplaceConfirm) {
            Button("Cancel", role: .cancel) { pendingBackup = nil }
            Button("Replace", role: .destructive) { restorePendingBackup() }
        } message: {
            Text("This will replace everything currently in ThinkSort with the backup. This cannot be undone.")
        }
        .alert("That file is not a ThinkSort backup", isPresented: $showBackupError) {
            Button("OK", role: .cancel) {}
        }
    }

    private func exportBackup() {
        let data = store.exportBackup()
        let name = "ThinkSort-Backup-\(CapacityStorage.dayStamp(for: Date())).json"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: [.atomic, .completeFileProtection])
            backupFile = BackupFile(url: url)
        } catch {
            router.flash("Couldn't create the backup file.")
        }
    }

    private func handlePickedBackup(_ result: Result<URL, Error>) {
        guard case .success(let url) = result else { return }
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            showBackupError = true
            return
        }
        pendingBackup = data
        showReplaceConfirm = true
    }

    private func restorePendingBackup() {
        guard let data = pendingBackup else { return }
        pendingBackup = nil
        do {
            try store.importBackup(data: data)
            dismiss()
            router.flash("Backup restored")
        } catch {
            showBackupError = true
        }
    }

    private var dataSection: some View {
        Section("Your data") {
            Button {
                exportText = store.exportJSON()
                showExport = true
            } label: {
                Label("Export everything", systemImage: "square.and.arrow.up")
            }
            Button {
                store.loadSampleData()
                router.flash("Sample list restored.")
            } label: {
                Label("Reload sample list", systemImage: "arrow.clockwise")
            }
            Button(role: .destructive) {
                showDeleteConfirm = true
            } label: {
                Label("Delete all data", systemImage: "trash")
            }
        }
    }

    private var aboutSection: some View {
        Section {
            LabeledContent("Version", value: Self.appVersion)
        } footer: {
            Text("ThinkSort is a productivity and organization app. It doesn't diagnose, treat, or provide medical advice for any condition.")
        }
    }

    /// Reads the shipped version from the bundle so Settings can never drift.
    static var appVersion: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        return short
    }

    static func timeLabel(_ minutes: Int) -> String {
        var components = DateComponents()
        components.hour = minutes / 60
        components.minute = minutes % 60
        let date = Calendar.current.date(from: components) ?? Date()
        return date.formatted(date: .omitted, time: .shortened)
    }
}

struct PrivacyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                block(
                    title: "Everything stays on your iPhone",
                    body: "Your tasks, notes, brain dumps and history are stored locally on this device. ThinkSort has no account, no server, and nothing is sold or shared."
                )
                block(
                    title: "Microphone & speech",
                    body: "Used only while you're recording a brain dump. Transcription happens on device when your iPhone supports it. If you decline, you can type instead and everything still works."
                )
                block(
                    title: "Calendar",
                    body: "Read only, and only if you allow it. ThinkSort uses your events to understand how much free time you have. It never creates, edits or deletes an event."
                )
                block(
                    title: "Location",
                    body: "Used only for reminders you explicitly tie to a place. If you decline, those become normal time-based reminders."
                )
                block(
                    title: "Notifications",
                    body: "Used for reminders you set. You control how often, and quiet hours hold everything back until morning."
                )
                block(
                    title: "Not a medical app",
                    body: "ThinkSort is an organization and productivity tool. It does not diagnose, treat or cure any condition, and it doesn't provide medical advice."
                )
            }
            .padding(Theme.gutter)
        }
        .canvas()
        .navigationTitle("Privacy")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func block(title: String, body text: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
                .foregroundStyle(Theme.ink)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(18)
        .card()
    }
}

struct ExportView: View {
    let text: String

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(text)
                    .font(.system(.footnote, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(Theme.gutter)
            }
            .canvas()
            .navigationTitle("Export")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    ShareLink(item: text) {
                        Image(systemName: "square.and.arrow.up")
                    }
                }
            }
        }
    }
}

struct SiriHelpView: View {
    @Environment(\.dismiss) private var dismiss

    /// Phrases registered as App Shortcuts — these work by voice with no setup.
    private let phrases: [(String, [String])] = [
        ("Capture", [
            "Add to ThinkSort",
            "Remember this in ThinkSort",
        ]),
        ("Orient", [
            "What's next in ThinkSort",
            "What's coming up in ThinkSort",
            "What am I forgetting in ThinkSort",
        ]),
        ("Resume", [
            "Where did I leave off in ThinkSort",
            "Start my next task in ThinkSort",
            "I'm stuck in ThinkSort",
        ]),
        ("Finish & prepare", [
            "Mark that done in ThinkSort",
            "What do I need before I leave in ThinkSort",
            "Start my morning in ThinkSort",
        ]),
    ]

    /// Actions available in the Shortcuts app, where you can give them your own phrase.
    private let shortcutActions: [String] = [
        "Move That to Tomorrow",
        "Remind Me Later",
        "Evening Reset",
        "Add to Shopping List",
        "Brain Dump",
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Say “Hey Siri,” then any of these. They also show up as actions in the Shortcuts app.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.inkSecondary)
                        .fixedSize(horizontal: false, vertical: true)

                    ForEach(phrases, id: \.0) { group in
                        SectionCard(title: group.0) {
                            ForEach(Array(group.1.enumerated()), id: \.element) { index, phrase in
                                Text("“\(phrase)”")
                                    .font(.body)
                                    .foregroundStyle(Theme.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 18)
                                    .padding(.vertical, 12)
                                if index < group.1.count - 1 { RowDivider() }
                            }
                        }
                    }

                    SectionCard(title: "Also in shortcuts") {
                        ForEach(Array(shortcutActions.enumerated()), id: \.element) { index, action in
                            Text(action)
                                .font(.body)
                                .foregroundStyle(Theme.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 12)
                            if index < shortcutActions.count - 1 { RowDivider() }
                        }
                    }

                    Text("Open the Shortcuts app to give any of these your own phrase.")
                        .font(.footnote)
                        .foregroundStyle(Theme.inkTertiary)
                        .padding(.horizontal, 4)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(Theme.gutter)
            }
            .canvas()
            .navigationTitle("Siri phrases")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }
}

/// Every saved "I'm Stuck" move, grouped by reason. Swipe to delete.
struct MyMovesView: View {
    @Environment(BrainStore.self) private var store

    private var groups: [(reason: StuckReason, moves: [Move])] {
        StuckReason.allCases.compactMap { reason in
            let moves = store.moves.filter { $0.reason == reason.rawValue }
            return moves.isEmpty ? nil : (reason, moves)
        }
    }

    var body: some View {
        List {
            ForEach(groups, id: \.reason) { group in
                Section(group.reason.label) {
                    ForEach(group.moves) { move in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(move.stepText)
                                .font(.body)
                            Text(move.createdAt.formatted(date: .abbreviated, time: .omitted))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 2)
                    }
                    .onDelete { offsets in
                        for index in offsets { store.deleteMove(group.moves[index].id) }
                    }
                }
            }
        }
        .overlay {
            if store.moves.isEmpty {
                ContentUnavailableView(
                    "No moves yet",
                    systemImage: "bookmark",
                    description: Text("When a tiny step from I'm Stuck works, tap \u{201C}Save this as a move\u{201D} to keep it here.")
                )
            }
        }
        .navigationTitle("My Moves")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// A backup file written to the app's temporary folder, ready to hand to the share sheet.
struct BackupFile: Identifiable {
    let url: URL
    var id: URL { url }
}

/// The system share sheet. The user decides where (if anywhere) the file goes.
struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

#Preview {
    SettingsView()
        .environment(BrainStore())
        .environment(AppRouter())
}
