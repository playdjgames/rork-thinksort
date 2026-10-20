import SwiftUI
import Combine

struct FocusView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var selectedTaskID: UUID?
    @State private var minutes: Int = 25
    @State private var customMinutes: Int = 40
    @State private var showCustomPicker = false
    @State private var remaining: Int = 25 * 60
    @State private var isRunning = false
    @State private var startedAt: Date?
    @State private var showKeepGoing = false
    @State private var completedPulse = false

    private let ticker = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    private let presets = [5, 15, 25]

    private var task: TaskItem? {
        if let selectedTaskID { return store.task(id: selectedTaskID) }
        return store.nextAction()?.task
    }

    private var progress: Double {
        let total = Double(minutes * 60)
        guard total > 0 else { return 0 }
        return max(0, min(1, 1 - Double(remaining) / total))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                taskCard
                timerRing
                if !isRunning { durationPicker }
                controls
                if !store.data.focusSessions.isEmpty { recentSessions }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .navigationTitle("Focus")
        .onReceive(ticker) { _ in tick() }
        .onChange(of: minutes) { _, newValue in
            if !isRunning { remaining = newValue * 60 }
        }
        .alert("Time's up", isPresented: $showKeepGoing) {
            Button("Keep going 10 more") { extend(by: 10) }
            Button("I'm done") { finish(completed: true) }
            Button("Mark task done") {
                if let task { store.complete(taskID: task.id) }
                finish(completed: true)
            }
        } message: {
            Text("Do you want to keep going?")
        }
        .sheet(isPresented: $showCustomPicker) {
            NavigationStack {
                Form {
                    Picker("Minutes", selection: $customMinutes) {
                        ForEach([10, 20, 30, 40, 45, 50, 60, 75, 90], id: \.self) { value in
                            Text("\(value) minutes").tag(value)
                        }
                    }
                    .pickerStyle(.wheel)
                }
                .navigationTitle("Custom length")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Set") {
                            minutes = customMinutes
                            showCustomPicker = false
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .sensoryFeedback(.success, trigger: completedPulse)
    }

    // MARK: - Pieces

    private var taskCard: some View {
        Group {
            if let task {
                VStack(alignment: .leading, spacing: 10) {
                    Eyebrow(text: "Focusing on")
                    Text(task.title)
                        .font(.title2.bold())
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    if let step = task.nextSubtask {
                        Text("Next step: \(step.title)")
                            .font(.subheadline)
                            .foregroundStyle(Theme.inkSecondary)
                    }
                    if !isRunning {
                        Menu {
                            ForEach(store.openTasks) { candidate in
                                Button(candidate.title) { selectedTaskID = candidate.id }
                            }
                        } label: {
                            Label("Pick a different task", systemImage: "arrow.triangle.2.circlepath")
                                .font(.subheadline)
                                .foregroundStyle(Theme.accent)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .card()
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Nothing to focus on yet")
                        .font(.title3.bold())
                        .foregroundStyle(Theme.ink)
                    Text("Add something first and it'll show up here.")
                        .font(.subheadline)
                        .foregroundStyle(Theme.inkSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .card()
            }
        }
    }

    private var timerRing: some View {
        ZStack {
            Circle()
                .stroke(Theme.hairline, lineWidth: 14)
            Circle()
                .trim(from: 0, to: progress)
                .stroke(Theme.accent, style: StrokeStyle(lineWidth: 14, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(reduceMotion ? nil : .linear(duration: 0.9), value: progress)

            VStack(spacing: 4) {
                Text(timeString)
                    .font(.system(size: 52, weight: .bold, design: .rounded))
                    .monospacedDigit()
                    .foregroundStyle(Theme.ink)
                Text(isRunning ? "Focusing" : "Ready")
                    .font(.subheadline)
                    .foregroundStyle(Theme.inkSecondary)
            }
        }
        .frame(width: 240, height: 240)
        .padding(.vertical, 8)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Focus timer")
        .accessibilityValue("\(remaining / 60) minutes \(remaining % 60) seconds remaining")
    }

    private var timeString: String {
        String(format: "%d:%02d", remaining / 60, remaining % 60)
    }

    private var durationPicker: some View {
        HStack(spacing: 10) {
            ForEach(presets, id: \.self) { preset in
                Button {
                    minutes = preset
                } label: {
                    Text("\(preset)m")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(minutes == preset ? .white : Theme.ink)
                        .frame(maxWidth: .infinity, minHeight: 46)
                        .background(minutes == preset ? Theme.accent : Theme.surface, in: .capsule)
                        .overlay { Capsule().strokeBorder(Theme.hairline, lineWidth: minutes == preset ? 0 : 1) }
                }
                .buttonStyle(.plain)
            }
            Button {
                showCustomPicker = true
            } label: {
                Text(presets.contains(minutes) ? "Custom" : "\(minutes)m")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(presets.contains(minutes) ? Theme.ink : .white)
                    .frame(maxWidth: .infinity, minHeight: 46)
                    .background(presets.contains(minutes) ? Theme.surface : Theme.accent, in: .capsule)
                    .overlay { Capsule().strokeBorder(Theme.hairline, lineWidth: presets.contains(minutes) ? 1 : 0) }
            }
            .buttonStyle(.plain)
        }
    }

    private var controls: some View {
        VStack(spacing: 10) {
            Button {
                isRunning ? pause() : start()
            } label: {
                Label(isRunning ? "Pause" : "Start Focus", systemImage: isRunning ? "pause.fill" : "play.fill")
            }
            .buttonStyle(PrimaryButtonStyle())
            .disabled(task == nil)
            .opacity(task == nil ? 0.5 : 1)

            if isRunning || remaining < minutes * 60 {
                HStack(spacing: 10) {
                    Button {
                        finish(completed: false)
                    } label: {
                        Label("Stop", systemImage: "stop.fill")
                    }
                    .buttonStyle(SoftButtonStyle(isCompact: true))

                    Button {
                        if let task {
                            store.complete(taskID: task.id)
                            finish(completed: true)
                        }
                    } label: {
                        Label("Task Done", systemImage: "checkmark")
                    }
                    .buttonStyle(SoftButtonStyle(isCompact: true))
                }
            }
        }
    }

    private var recentSessions: some View {
        SectionCard(title: "Recent focus") {
            ForEach(Array(store.data.focusSessions.prefix(4).enumerated()), id: \.element.id) { index, session in
                HStack(spacing: 12) {
                    IconWell(symbol: session.finished ? "checkmark" : "pause", size: 34)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(session.taskTitle)
                            .font(.subheadline)
                            .foregroundStyle(Theme.ink)
                            .lineLimit(1)
                        Text("\(session.completedMinutes) min · \(session.startedAt.formatted(date: .abbreviated, time: .shortened))")
                            .font(.caption)
                            .foregroundStyle(Theme.inkTertiary)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                if index < min(4, store.data.focusSessions.count) - 1 { RowDivider(leading: 16) }
            }
        }
    }

    // MARK: - Timer control

    private func start() {
        guard let task else { return }
        if remaining <= 0 { remaining = minutes * 60 }
        if startedAt == nil { startedAt = Date() }
        store.start(taskID: task.id)
        isRunning = true
    }

    private func pause() {
        isRunning = false
    }

    private func tick() {
        guard isRunning else { return }
        if remaining > 0 {
            remaining -= 1
        }
        if remaining == 0 {
            isRunning = false
            completedPulse.toggle()
            showKeepGoing = true
        }
    }

    private func extend(by extra: Int) {
        remaining = extra * 60
        minutes += extra
        isRunning = true
    }

    private func finish(completed: Bool) {
        let elapsed = max(0, minutes * 60 - remaining) / 60
        if let task, let startedAt {
            store.recordFocusSession(FocusSessionRecord(
                taskID: task.id,
                taskTitle: task.title,
                plannedMinutes: minutes,
                completedMinutes: elapsed,
                startedAt: startedAt,
                endedAt: Date(),
                finished: completed
            ))
            if !completed {
                store.saveCheckpoint(taskID: task.id, note: "Paused a focus session")
            }
        }
        isRunning = false
        startedAt = nil
        remaining = minutes * 60
    }
}

#Preview {
    NavigationStack { FocusView() }
        .environment(BrainStore())
        .environment(AppRouter())
}
