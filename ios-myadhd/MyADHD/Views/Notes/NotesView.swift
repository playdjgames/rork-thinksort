import SwiftUI

struct NotesView: View {
    @Environment(BrainStore.self) private var store

    @State private var newText: String = ""
    @State private var newIsIdea: Bool = true

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                addCard

                if store.ideas.isEmpty && store.plainNotes.isEmpty {
                    emptyState
                } else {
                    if !store.ideas.isEmpty {
                        noteGroup(title: "Ideas", notes: store.ideas)
                    }
                    if !store.plainNotes.isEmpty {
                        noteGroup(title: "Notes", notes: store.plainNotes)
                    }
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .navigationTitle("Notes & ideas")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }

    private var addCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            TextField("Something you want to keep, not do", text: $newText, axis: .vertical)
                .lineLimit(1...4)

            HStack(spacing: 10) {
                Picker("Kind", selection: $newIsIdea) {
                    Text("Idea").tag(true)
                    Text("Note").tag(false)
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 180)

                Spacer(minLength: 0)

                Button("Save") {
                    store.addNote(newText, isIdea: newIsIdea)
                    newText = ""
                }
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Theme.accent)
                .disabled(newText.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(16)
        .card()
    }

    private func noteGroup(title: String, notes: [NoteItem]) -> some View {
        SectionCard(title: title) {
            ForEach(Array(notes.enumerated()), id: \.element.id) { index, note in
                VStack(alignment: .leading, spacing: 8) {
                    Text(note.text)
                        .font(.body)
                        .foregroundStyle(Theme.ink)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: 14) {
                        Text(note.createdAt.formatted(date: .abbreviated, time: .omitted))
                            .font(.caption)
                            .foregroundStyle(Theme.inkTertiary)
                        Spacer(minLength: 0)
                        Button("Make it a task") {
                            store.convertNoteToTask(note.id)
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.accent)
                        Button("Delete", role: .destructive) {
                            store.deleteNote(note.id)
                        }
                        .font(.caption.weight(.semibold))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 18)
                .padding(.vertical, 14)

                if index < notes.count - 1 { RowDivider() }
            }
        }
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 10) {
            IconWell(symbol: "lightbulb", size: 56)
            Text("Nothing saved yet")
                .font(.title3.bold())
                .foregroundStyle(Theme.ink)
            Text("Not everything is a task. Say “I have an idea for my game” in a brain dump and it lands here instead of your to-do list.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }
}

struct SearchView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router

    @State private var query: String = ""

    private var results: BrainStore.SearchResults { store.search(query) }

    private let examples = [
        "When did I say I'd call Mike?",
        "What did I need for the vet?",
        "What did I postpone?",
        "What was I working on?",
    ]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if query.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Eyebrow(text: "Try asking").padding(.leading, 4)
                        ForEach(examples, id: \.self) { example in
                            Button {
                                query = example
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
                } else if results.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Nothing matched")
                            .font(.title3.bold())
                            .foregroundStyle(Theme.ink)
                        Text("Try a single word — a name, a place, or the thing itself.")
                            .font(.subheadline)
                            .foregroundStyle(Theme.inkSecondary)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card()
                } else {
                    if !results.tasks.isEmpty {
                        TaskGroupCard(title: "Tasks", tasks: results.tasks, showCategory: true) { task in
                            store.complete(taskID: task.id)
                        } onSelect: { task in
                            router.openTask(task.id)
                        }
                    }

                    if !results.notes.isEmpty {
                        SectionCard(title: "Notes & ideas") {
                            ForEach(Array(results.notes.enumerated()), id: \.element.id) { index, note in
                                Text(note.text)
                                    .font(.body)
                                    .foregroundStyle(Theme.ink)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.horizontal, 18)
                                    .padding(.vertical, 14)
                                if index < results.notes.count - 1 { RowDivider() }
                            }
                        }
                    }

                    if !results.shopping.isEmpty {
                        SectionCard(title: "Shopping") {
                            ForEach(Array(results.shopping.enumerated()), id: \.element.id) { index, item in
                                HStack {
                                    Text(item.name).foregroundStyle(Theme.ink)
                                    Spacer()
                                    Text(item.storeLabel)
                                        .font(.footnote)
                                        .foregroundStyle(Theme.inkSecondary)
                                }
                                .padding(.horizontal, 18)
                                .padding(.vertical, 14)
                                if index < results.shopping.count - 1 { RowDivider() }
                            }
                        }
                    }

                    if !results.history.isEmpty {
                        SectionCard(title: "History") {
                            ForEach(Array(results.history.enumerated()), id: \.element.id) { index, entry in
                                HistoryRow(entry: entry)
                                if index < results.history.count - 1 { RowDivider(leading: 18) }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Ask about anything you said")
        .navigationTitle("Search")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }
}

struct HistoryView: View {
    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router

    private var grouped: [(day: Date, entries: [HistoryEntry])] {
        let buckets = Dictionary(grouping: store.history) { Calendar.current.startOfDay(for: $0.date) }
        return buckets.sorted { $0.key > $1.key }.map { (day: $0.key, entries: $0.value) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                if store.history.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("No history yet")
                            .font(.title3.bold())
                            .foregroundStyle(Theme.ink)
                        Text("Everything you start, finish, move or dump shows up here.")
                            .font(.subheadline)
                            .foregroundStyle(Theme.inkSecondary)
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .card()
                } else {
                    ForEach(grouped, id: \.day) { group in
                        SectionCard(title: label(for: group.day)) {
                            ForEach(Array(group.entries.enumerated()), id: \.element.id) { index, entry in
                                Button {
                                    if let taskID = entry.taskID, store.task(id: taskID) != nil {
                                        router.openTask(taskID)
                                    }
                                } label: {
                                    HistoryRow(entry: entry)
                                }
                                .buttonStyle(.plain)
                                .disabled(entry.taskID == nil)

                                if index < group.entries.count - 1 { RowDivider(leading: 18) }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .navigationTitle("History")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }

    private func label(for day: Date) -> String {
        let calendar = Calendar.current
        if calendar.isDateInToday(day) { return "Today" }
        if calendar.isDateInYesterday(day) { return "Yesterday" }
        return day.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
    }
}

struct HistoryRow: View {
    let entry: HistoryEntry

    var body: some View {
        HStack(spacing: 12) {
            Text(entry.kind.label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(Theme.accent)
                .frame(width: 74, alignment: .leading)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.title)
                    .font(.subheadline)
                    .foregroundStyle(Theme.ink)
                    .multilineTextAlignment(.leading)
                if !entry.detail.isEmpty {
                    Text(entry.detail)
                        .font(.caption)
                        .foregroundStyle(Theme.inkTertiary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Text(entry.date.formatted(date: .omitted, time: .shortened))
                .font(.caption)
                .foregroundStyle(Theme.inkTertiary)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}
