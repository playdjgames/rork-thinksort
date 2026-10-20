import SwiftUI
import UserNotifications

struct OnboardingView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var pageIndex: Int = 0
    @State private var stage: Stage = .intro
    @State private var firstTaskText: String = ""
    @State private var createdTask: TaskItem?
    @State private var speech = SpeechService()
    @FocusState private var isFieldFocused: Bool

    private enum Stage {
        case intro, permissions, firstTask, firstStep
    }

    private struct Page: Identifiable {
        let id = UUID()
        let symbol: String
        let title: String
        let body: String
    }

    private let pages: [Page] = [
        Page(symbol: "brain.head.profile", title: "Welcome to ThinkSort", body: "Add a little structure to your daily chaos."),
        Page(symbol: "mic.fill", title: "Dump Your Brain", body: "Tell us what's on your mind. We'll help organize it."),
        Page(symbol: "arrow.right.circle.fill", title: "Know What's Next", body: "ThinkSort helps you focus on one thing at a time."),
        Page(symbol: "bookmark.fill", title: "Never Lose Your Place", body: "Ask where you left off and pick up where you stopped."),
        Page(symbol: "waveform", title: "Talk to ThinkSort", body: "Use Siri to add tasks, ask what's next, and get reminders."),
    ]

    var body: some View {
        VStack(spacing: 0) {
            switch stage {
            case .intro: introFlow
            case .permissions: PermissionsFlow(onDone: { advance(to: .firstTask) })
            case .firstTask: firstTaskFlow
            case .firstStep: firstStepFlow
            }
        }
        .canvas()
        .animation(reduceMotion ? nil : .snappy(duration: 0.3), value: stage)
    }

    // MARK: - Intro pages

    private var introFlow: some View {
        VStack(spacing: 0) {
            HStack {
                Spacer()
                Button("Skip") { finishIntro() }
                    .font(.subheadline)
                    .foregroundStyle(Theme.inkSecondary)
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)

            TabView(selection: $pageIndex) {
                ForEach(Array(pages.enumerated()), id: \.element.id) { index, page in
                    VStack(alignment: .leading, spacing: 20) {
                        Spacer()
                        IconWell(symbol: page.symbol, size: 92)
                        Text(page.title)
                            .font(.largeTitle.bold())
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(page.body)
                            .font(.title3)
                            .foregroundStyle(Theme.inkSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer()
                        Spacer()
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, Theme.gutter)
                    .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .indexViewStyle(.page(backgroundDisplayMode: .always))

            Button {
                if pageIndex < pages.count - 1 {
                    withAnimation(reduceMotion ? nil : .snappy) { pageIndex += 1 }
                } else {
                    finishIntro()
                }
            } label: {
                Text(pageIndex < pages.count - 1 ? "Next" : "Let's Go")
            }
            .buttonStyle(PrimaryButtonStyle())
            .padding(.horizontal, Theme.gutter)
            .padding(.bottom, 16)
        }
    }

    private func finishIntro() {
        advance(to: .permissions)
    }

    // MARK: - First task

    private var firstTaskFlow: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()

            Text("What's something you need to get done today?")
                .font(.largeTitle.bold())
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)

            Text("Say it however it comes out — ThinkSort will sort the details.")
                .font(.title3)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(alignment: .top, spacing: 12) {
                TextField("Call the electric company…", text: inputBinding, axis: .vertical)
                    .font(.body)
                    .lineLimit(1...4)
                    .focused($isFieldFocused)
                    .padding(14)
                    .card()

                Button {
                    toggleMic()
                } label: {
                    Image(systemName: speech.isRecording ? "stop.fill" : "mic.fill")
                        .font(.title3)
                        .foregroundStyle(speech.isRecording ? Theme.coral : Theme.accent)
                        .frame(width: 52, height: 52)
                        .background(speech.isRecording ? Theme.coralSoft : Theme.accentSoft, in: .circle)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(speech.isRecording ? "Stop dictation" : "Dictate")
            }

            Spacer()

            Button {
                createFirstTask()
            } label: {
                Label("Add It", systemImage: "arrow.right")
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(firstTaskText.trimmingCharacters(in: .whitespaces).count < 3)
            .opacity(firstTaskText.trimmingCharacters(in: .whitespaces).count < 3 ? 0.5 : 1)

            Button("I'll do this later") { completeOnboarding() }
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, Theme.gutter)
        .padding(.bottom, 16)
        .onAppear { isFieldFocused = true }
        .onDisappear { speech.stop() }
    }

    private var inputBinding: Binding<String> {
        Binding(
            get: { speech.isRecording ? speech.transcript : firstTaskText },
            set: { newValue in
                guard !speech.isRecording else { return }
                firstTaskText = newValue
            }
        )
    }

    private func toggleMic() {
        if speech.isRecording {
            firstTaskText = speech.transcript
            speech.stop()
        } else {
            isFieldFocused = false
            Task { await speech.start(seed: firstTaskText) }
        }
    }

    private func createFirstTask() {
        if speech.isRecording {
            firstTaskText = speech.transcript
            speech.stop()
        }
        let text = firstTaskText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }

        let organizer = NaturalLanguageOrganizer()
        var item = organizer.parseSingle(text) ?? ExtractedItem(title: text.capitalizedFirst, category: .task)
        if item.dueDate == nil {
            item.dueDate = Date()
        }
        item.needsClarification = false

        var task = TaskItem(title: item.title, category: item.category == .idea ? .task : item.category)
        task.dueDate = item.dueDate
        task.hasTime = item.hasTime
        task.priority = .now
        task.estimatedMinutes = item.estimatedMinutes
        task.locationName = item.locationName
        store.add(task)
        createdTask = task
        advance(to: .firstStep)
    }

    // MARK: - First step

    private var firstStepFlow: some View {
        VStack(alignment: .leading, spacing: 20) {
            Spacer()

            Eyebrow(text: "Your first next step", color: Theme.accent)

            if let createdTask {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 16) {
                        IconWell(symbol: createdTask.category.symbol, size: 62, background: Theme.surface)
                        Text(createdTask.title)
                            .font(.title.bold())
                            .foregroundStyle(Theme.ink)
                            .fixedSize(horizontal: false, vertical: true)
                        Spacer(minLength: 0)
                    }
                    Text("That's the whole idea: ThinkSort always shows you one thing, and why it matters.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.inkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.accentSoft, in: .rect(cornerRadius: Theme.cardRadius))
            }

            Spacer()

            Button {
                if let createdTask {
                    store.start(taskID: createdTask.id)
                    completeOnboarding()
                    router.show(fullScreen: .oneThing(createdTask.id))
                } else {
                    completeOnboarding()
                }
            } label: {
                Label("Start", systemImage: "play.fill")
            }
            .buttonStyle(PrimaryButtonStyle())

            Button("Take me to my day") { completeOnboarding() }
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, Theme.gutter)
        .padding(.bottom, 16)
    }

    // MARK: - Flow control

    private func advance(to next: Stage) {
        withAnimation(reduceMotion ? nil : .snappy(duration: 0.3)) {
            stage = next
        }
    }

    private func completeOnboarding() {
        var settings = store.settings
        settings.hasCompletedOnboarding = true
        store.settings = settings
        router.selectedTab = .today
    }
}

/// Explains each permission one at a time, then always proceeds to the system prompt.
/// Per App Review 5.1.1(iv): the only button is "Continue" and there is no way to skip
/// the system request from here — the user decides in Apple's own dialog.
struct PermissionsFlow: View {
    let onDone: () -> Void

    @Environment(BrainStore.self) private var store
    @State private var index: Int = 0
    @State private var isRequesting: Bool = false

    private struct Ask: Identifiable {
        let id = UUID()
        let symbol: String
        let title: String
        let body: String
    }

    private let asks: [Ask] = [
        Ask(
            symbol: "bell.badge",
            title: "Reminders at the right moment",
            body: "ThinkSort can nudge you before something starts and tell you what to bring. It keeps alerts deliberately few, and holds them during quiet hours."
        ),
        Ask(
            symbol: "waveform",
            title: "Talk instead of typing",
            body: "Speak your brain dump and ThinkSort turns it into tasks. This uses the microphone and speech recognition, on your device where your iPhone supports it."
        ),
        Ask(
            symbol: "calendar",
            title: "Know how much time you have",
            body: "Reading your calendar lets ThinkSort suggest things that actually fit between your events. It never creates or changes an event."
        ),
        Ask(
            symbol: "mappin.and.ellipse",
            title: "Remind me when I get there",
            body: "For “when I'm at Walmart, remind me to buy batteries.” Without location, those become normal reminders instead."
        ),
    ]

    var body: some View {
        let ask = asks[min(index, asks.count - 1)]

        VStack(alignment: .leading, spacing: 20) {
            Spacer()

            IconWell(symbol: ask.symbol, size: 84)

            Text(ask.title)
                .font(.largeTitle.bold())
                .foregroundStyle(Theme.ink)
                .fixedSize(horizontal: false, vertical: true)

            Text(ask.body)
                .font(.title3)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer()

            Text("\(index + 1) of \(asks.count) · You can change this anytime in iPhone Settings.")
                .font(.footnote)
                .foregroundStyle(Theme.inkTertiary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)

            Button {
                requestCurrent()
            } label: {
                Text("Continue")
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(isRequesting)
        }
        .padding(.horizontal, Theme.gutter)
        .padding(.bottom, 16)
        .animation(.snappy(duration: 0.25), value: index)
    }

    private func requestCurrent() {
        guard !isRequesting else { return }
        isRequesting = true
        Task {
            switch index {
            case 0:
                await NotificationService.shared.requestAuthorization()
            case 1:
                _ = await SpeechService().requestPermissions()
            case 2:
                let granted = await CalendarService.shared.requestAccess()
                var settings = store.settings
                settings.calendarEnabled = granted
                store.settings = settings
            default:
                LocationService.shared.requestAlways()
                var settings = store.settings
                settings.locationRemindersEnabled = true
                store.settings = settings
            }
            isRequesting = false
            next()
        }
    }

    private func next() {
        if index < asks.count - 1 {
            index += 1
        } else {
            onDone()
        }
    }
}

#Preview {
    OnboardingView()
        .environment(BrainStore())
        .environment(AppRouter())
}
