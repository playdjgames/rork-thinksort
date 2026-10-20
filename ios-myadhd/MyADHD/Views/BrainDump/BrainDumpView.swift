import SwiftUI

struct BrainDumpView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var speech = SpeechService()
    @State private var typedText: String = ""
    @State private var result: OrganizerResult?
    @State private var wasSpoken = false
    @FocusState private var isTypingFocused: Bool

    var body: some View {
        Group {
            if let result {
                BrainDumpConfirmView(
                    result: result,
                    onSave: { items in save(items, transcript: result.transcript) },
                    onEdit: { backToCapture() }
                )
            } else {
                captureView
            }
        }
        .canvas()
        .navigationTitle(result == nil ? "Brain Dump" : "")
        .navigationBarTitleDisplayMode(result == nil ? .large : .inline)
        .toolbar(result == nil ? .visible : .hidden, for: .navigationBar)
        .onDisappear { speech.stop() }
        .onChange(of: router.focusBrainDump, initial: true) { _, shouldFocus in
            guard shouldFocus else { return }
            router.focusBrainDump = false
            guard result == nil else { return }
            Task {
                // Let the tab switch settle so the keyboard reliably appears.
                try? await Task.sleep(for: .milliseconds(400))
                isTypingFocused = true
            }
        }
    }

    // MARK: - Capture

    private var captureView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                Text("Just talk. Don't organize it — that's the whole point.")
                    .font(.title3)
                    .foregroundStyle(Theme.inkSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                micCard

                if case .denied(let message) = speech.state {
                    permissionNotice(message, showsSettingsLink: true)
                }
                if case .unavailable(let message) = speech.state {
                    permissionNotice(message, showsSettingsLink: false)
                }

                VStack(alignment: .leading, spacing: 10) {
                    Eyebrow(text: speech.isRecording ? "Listening" : "What's on your mind")
                        .padding(.leading, 4)
                    TextEditor(text: displayBinding)
                        .font(.body)
                        .scrollContentBackground(.hidden)
                        .frame(minHeight: 180)
                        .padding(12)
                        .focused($isTypingFocused)
                        .card()
                        .overlay(alignment: .topLeading) {
                            if currentText.isEmpty {
                                Text("Tap the mic, or type it all out here — one long ramble is fine.")
                                    .font(.body)
                                    .foregroundStyle(Theme.inkTertiary)
                                    .padding(20)
                                    .allowsHitTesting(false)
                            }
                        }
                }

                Button {
                    organize()
                } label: {
                    Label("Organize This", systemImage: "wand.and.sparkles")
                }
                .buttonStyle(PrimaryButtonStyle())
                .disabled(currentText.trimmingCharacters(in: .whitespacesAndNewlines).count < 3)
                .opacity(currentText.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ? 0.5 : 1)

                if !store.brainDumps.isEmpty {
                    recentDumps
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
    }

    private var currentText: String {
        speech.isRecording ? speech.transcript : typedText
    }

    private var displayBinding: Binding<String> {
        Binding(
            get: { currentText },
            set: { newValue in
                if !speech.isRecording { typedText = newValue }
            }
        )
    }

    private var micCard: some View {
        VStack(spacing: 14) {
            Button {
                toggleRecording()
            } label: {
                ZStack {
                    Circle()
                        .fill(speech.isRecording ? Theme.coral.opacity(0.14) : Theme.accentSoft)
                        .frame(width: 118, height: 118)
                    if speech.isRecording, !reduceMotion {
                        Circle()
                            .stroke(Theme.coral.opacity(0.35), lineWidth: 3)
                            .frame(width: 118 + CGFloat(speech.audioLevel * 46),
                                   height: 118 + CGFloat(speech.audioLevel * 46))
                            .animation(.easeOut(duration: 0.18), value: speech.audioLevel)
                    }
                    Image(systemName: speech.isRecording ? "stop.fill" : "mic.fill")
                        .font(.system(size: 42, weight: .medium))
                        .foregroundStyle(speech.isRecording ? Theme.coral : Theme.accent)
                        .contentTransition(.symbolEffect(.replace))
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(speech.isRecording ? "Stop recording" : "Start recording")

            Text(speech.isRecording ? "Listening — take as long as you need" : "Tap to start talking")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .card()
    }

    private func permissionNotice(_ message: String, showsSettingsLink: Bool) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(Theme.coral)
            VStack(alignment: .leading, spacing: 8) {
                Text(message)
                    .font(.footnote)
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                if showsSettingsLink, let url = URL(string: UIApplication.openSettingsURLString) {
                    Link(destination: url) {
                        Label("Open iPhone Settings", systemImage: "gear")
                            .font(.footnote.weight(.semibold))
                    }
                    .foregroundStyle(Theme.accent)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.coralSoft, in: .rect(cornerRadius: 16))
    }

    private var recentDumps: some View {
        SectionCard(title: "Recent dumps") {
            ForEach(Array(store.brainDumps.prefix(3).enumerated()), id: \.element.id) { index, dump in
                VStack(alignment: .leading, spacing: 4) {
                    Text(dump.transcript)
                        .font(.subheadline)
                        .foregroundStyle(Theme.ink)
                        .lineLimit(2)
                    Text("\(dump.createdAt.formatted(date: .abbreviated, time: .shortened)) · \(dump.savedItemCount) item\(dump.savedItemCount == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundStyle(Theme.inkTertiary)
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 12)
                .frame(maxWidth: .infinity, alignment: .leading)

                if index < min(3, store.brainDumps.count) - 1 { RowDivider() }
            }
        }
    }

    // MARK: - Actions

    private func toggleRecording() {
        if speech.isRecording {
            typedText = speech.transcript
            speech.stop()
            wasSpoken = true
        } else {
            isTypingFocused = false
            Task { await speech.start(seed: typedText) }
        }
    }

    private func organize() {
        if speech.isRecording {
            typedText = speech.transcript
            speech.stop()
            wasSpoken = true
        }
        let text = typedText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let organized = NaturalLanguageOrganizer().organize(text)
        guard !organized.isEmpty else {
            router.flash("I couldn't pull anything out of that — try adding a bit more.")
            return
        }
        withAnimation(reduceMotion ? nil : .snappy) {
            result = organized
        }
    }

    private func save(_ items: [ExtractedItem], transcript: String) {
        let count = store.saveBrainDump(transcript: transcript, items: items, wasSpoken: wasSpoken)
        typedText = ""
        speech.reset()
        wasSpoken = false
        withAnimation(reduceMotion ? nil : .snappy) { result = nil }
        router.flash("Saved \(count) thing\(count == 1 ? "" : "s").")
        router.selectedTab = .today
    }

    private func backToCapture() {
        withAnimation(reduceMotion ? nil : .snappy) { result = nil }
        isTypingFocused = true
    }
}

#Preview {
    NavigationStack { BrainDumpView() }
        .environment(BrainStore())
        .environment(AppRouter())
}
