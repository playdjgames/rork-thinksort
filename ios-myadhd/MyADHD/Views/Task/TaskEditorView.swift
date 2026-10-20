import SwiftUI

/// Full editor for an existing task.
struct TaskEditorView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var draft: TaskItem
    @State private var hasDate: Bool
    @State private var date: Date
    @State private var hasEstimate: Bool
    @State private var estimate: Int
    @State private var hasLead: Bool
    @State private var lead: Int

    let onSave: (TaskItem) -> Void

    init(task: TaskItem, onSave: @escaping (TaskItem) -> Void) {
        _draft = State(initialValue: task)
        _hasDate = State(initialValue: task.dueDate != nil)
        _date = State(initialValue: task.dueDate ?? Date())
        _hasEstimate = State(initialValue: task.estimatedMinutes != nil)
        _estimate = State(initialValue: task.estimatedMinutes ?? 20)
        _hasLead = State(initialValue: task.reminderLeadMinutes != nil)
        _lead = State(initialValue: task.reminderLeadMinutes ?? 45)
        self.onSave = onSave
    }

    private let estimateOptions = [5, 10, 15, 20, 30, 45, 60, 90, 120]
    private let leadOptions = [10, 15, 30, 45, 60, 120, 180]

    var body: some View {
        NavigationStack {
            Form {
                Section("What") {
                    TextField("Title", text: $draft.title, axis: .vertical)
                    TextField("Notes", text: $draft.notes, axis: .vertical)
                        .lineLimit(2...6)
                    Picker("Type", selection: $draft.category) {
                        ForEach(ItemCategory.allCases) { category in
                            Label(category.label, systemImage: category.symbol).tag(category)
                        }
                    }
                }

                Section("When") {
                    Toggle("Give it a day", isOn: $hasDate.animation())
                    if hasDate {
                        DatePicker("Day", selection: $date, displayedComponents: .date)
                        Toggle("Set a time", isOn: $draft.hasTime.animation())
                        if draft.hasTime {
                            DatePicker("Time", selection: $date, displayedComponents: .hourAndMinute)
                        }
                    }
                    Picker("Repeats", selection: Binding(
                        get: { draft.recurrence },
                        set: { draft.recurrence = $0 }
                    )) {
                        Text("Never").tag(RecurrenceRule?.none)
                        ForEach(RecurrenceRule.allCases) { rule in
                            Text(rule.label).tag(RecurrenceRule?.some(rule))
                        }
                    }
                }

                Section("How long & how urgent") {
                    Toggle("Estimate how long", isOn: $hasEstimate.animation())
                    if hasEstimate {
                        Picker("About", selection: $estimate) {
                            ForEach(estimateOptions, id: \.self) { minutes in
                                Text(minutes >= 60 ? "\(minutes / 60)h\(minutes % 60 == 0 ? "" : " \(minutes % 60)m")" : "\(minutes) min")
                                    .tag(minutes)
                            }
                        }
                    }
                    Picker("Priority", selection: $draft.priority) {
                        ForEach(Priority.allCases) { priority in
                            Text(priority.label).tag(priority)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section("Reminders") {
                    Toggle("Remind me ahead of time", isOn: $hasLead.animation())
                    if hasLead {
                        Picker("How early", selection: $lead) {
                            ForEach(leadOptions, id: \.self) { minutes in
                                Text(minutes >= 60 ? "\(minutes / 60) hour\(minutes == 60 ? "" : "s") before" : "\(minutes) min before")
                                    .tag(minutes)
                            }
                        }
                    }
                    Toggle("Keep reminding me until it's done", isOn: $draft.repeatUntilDone)
                }

                Section("Where") {
                    TextField("Place (optional)", text: Binding(
                        get: { draft.locationName ?? "" },
                        set: { draft.locationName = $0.isEmpty ? nil : $0 }
                    ))
                }
            }
            .navigationTitle("Edit task")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        draft.dueDate = hasDate ? date : nil
                        if !hasDate { draft.hasTime = false }
                        draft.estimatedMinutes = hasEstimate ? estimate : nil
                        draft.reminderLeadMinutes = hasLead ? lead : nil
                        onSave(draft)
                        dismiss()
                    }
                    .disabled(draft.title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

/// Quick natural-language capture — the "Ask ThinkSort" entry point.
struct QuickAddView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    @State private var text: String = ""
    @State private var preview: [ExtractedItem] = []
    @State private var speech = SpeechService()
    @FocusState private var isFocused: Bool

    private let examples = [
        "Remind me to call John tomorrow at 9",
        "Next time I'm at Walmart, remind me to buy batteries",
        "I need to finish the taxes before Friday",
        "Tomorrow clean the garage, but I need to buy trash bags first",
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Say it however it comes out.")
                        .font(.title3)
                        .foregroundStyle(Theme.inkSecondary)

                    HStack(alignment: .top, spacing: 12) {
                        TextField("Type or dictate…", text: inputBinding, axis: .vertical)
                            .font(.body)
                            .lineLimit(2...6)
                            .focused($isFocused)
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

                    if !preview.isEmpty {
                        SectionCard(title: "I'll create") {
                            ForEach(Array(preview.enumerated()), id: \.element.id) { index, item in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(item.title)
                                        .font(.body)
                                        .foregroundStyle(Theme.ink)
                                    HStack(spacing: 8) {
                                        CategoryTag(category: item.category)
                                        if let due = item.dueDate {
                                            Text(item.hasTime
                                                 ? due.formatted(date: .abbreviated, time: .shortened)
                                                 : due.formatted(date: .abbreviated, time: .omitted))
                                                .font(.footnote)
                                                .foregroundStyle(Theme.inkSecondary)
                                        }
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 12)

                                if index < preview.count - 1 { RowDivider() }
                            }
                        }
                    }

                    if text.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Eyebrow(text: "Try saying")
                                .padding(.leading, 4)
                            ForEach(examples, id: \.self) { example in
                                Button {
                                    text = example
                                    updatePreview()
                                } label: {
                                    HStack {
                                        Text("“\(example)”")
                                            .font(.subheadline)
                                            .foregroundStyle(Theme.inkSecondary)
                                            .multilineTextAlignment(.leading)
                                        Spacer(minLength: 0)
                                    }
                                    .padding(14)
                                    .frame(maxWidth: .infinity)
                                    .background(Theme.surfaceMuted, in: .rect(cornerRadius: 16))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    Button {
                        save()
                    } label: {
                        Label("Add It", systemImage: "plus")
                    }
                    .buttonStyle(PrimaryButtonStyle())
                    .disabled(text.trimmingCharacters(in: .whitespaces).count < 3)
                    .opacity(text.trimmingCharacters(in: .whitespaces).count < 3 ? 0.5 : 1)
                }
                .padding(.horizontal, Theme.gutter)
                .padding(.bottom, 24)
            }
            .canvas()
            .scrollIndicators(.hidden)
            .navigationTitle("Ask ThinkSort")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        speech.stop()
                        dismiss()
                    }
                }
            }
            .onAppear { isFocused = true }
            .onDisappear { speech.stop() }
        }
    }

    private var inputBinding: Binding<String> {
        Binding(
            get: { speech.isRecording ? speech.transcript : text },
            set: { newValue in
                guard !speech.isRecording else { return }
                text = newValue
                updatePreview()
            }
        )
    }

    private func toggleMic() {
        if speech.isRecording {
            text = speech.transcript
            speech.stop()
            updatePreview()
        } else {
            isFocused = false
            Task { await speech.start(seed: text) }
        }
    }

    private func updatePreview() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count > 3 else {
            preview = []
            return
        }
        preview = NaturalLanguageOrganizer().organize(trimmed).items
    }

    private func save() {
        if speech.isRecording {
            text = speech.transcript
            speech.stop()
        }
        let message = store.quickCapture(text)
        dismiss()
        router.flash(message)
    }
}
