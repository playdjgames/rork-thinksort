import SwiftUI

/// Conversational confirmation of what ThinkSort heard and how it organized it.
struct BrainDumpConfirmView: View {
    let result: OrganizerResult
    let onSave: ([ExtractedItem]) -> Void
    let onEdit: () -> Void

    @State private var items: [ExtractedItem]
    @State private var editingItem: ExtractedItem?

    init(result: OrganizerResult, onSave: @escaping ([ExtractedItem]) -> Void, onEdit: @escaping () -> Void) {
        self.result = result
        self.onSave = onSave
        self.onEdit = onEdit
        _items = State(initialValue: result.items)
    }

    private var groups: [(label: String, items: [ExtractedItem])] {
        let scheduled = items.filter { !$0.needsClarification }
        let order: [String] = scheduled.reduce(into: []) { acc, item in
            let label = groupLabel(for: item)
            if !acc.contains(label) { acc.append(label) }
        }
        return order.map { label in
            (label: label, items: scheduled.filter { groupLabel(for: $0) == label })
        }
    }

    private func groupLabel(for item: ExtractedItem) -> String {
        switch item.category {
        case .shopping: return "Shopping"
        case .idea: return "Ideas"
        case .note: return "Notes"
        default: return item.dayBucketLabel
        }
    }

    private var unclear: [ExtractedItem] {
        items.filter(\.needsClarification)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Got it — here's what I heard and how I organized it:")
                    .font(.title2.bold())
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 4)

                ForEach(groups, id: \.label) { group in
                    groupCard(group.label, group.items)
                }

                ForEach(unclear) { item in
                    clarificationCard(item)
                }

                Button {
                    onSave(items)
                } label: {
                    Label("Looks Good, Save It", systemImage: "checkmark")
                }
                .buttonStyle(PrimaryButtonStyle())

                Button {
                    onEdit()
                } label: {
                    Label("Edit Something", systemImage: "pencil")
                }
                .buttonStyle(SoftButtonStyle(tint: Theme.accent))
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.bottom, 28)
        }
        .scrollIndicators(.hidden)
        .sheet(item: $editingItem) { item in
            ExtractedItemEditor(item: item) { updated in
                if let index = items.firstIndex(where: { $0.id == updated.id }) {
                    items[index] = updated
                }
            } onDelete: { id in
                items.removeAll { $0.id == id }
            }
        }
    }

    private func groupCard(_ label: String, _ groupItems: [ExtractedItem]) -> some View {
        SectionCard(title: label, tint: label == "Today" ? Theme.accentSoft : Theme.surface) {
            ForEach(Array(groupItems.enumerated()), id: \.element.id) { index, item in
                Button {
                    editingItem = item
                } label: {
                    extractedRow(item)
                }
                .buttonStyle(.plain)

                if index < groupItems.count - 1 { RowDivider() }
            }
        }
    }

    private func extractedRow(_ item: ExtractedItem) -> some View {
        HStack(spacing: 14) {
            Button {
                toggleSelection(item)
            } label: {
                CheckCircle(isDone: item.isSelected)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(item.isSelected ? "Don't save \(item.title)" : "Save \(item.title)")

            VStack(alignment: .leading, spacing: 6) {
                Text(item.title)
                    .font(.body)
                    .foregroundStyle(item.isSelected ? Theme.ink : Theme.inkTertiary)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)

                HStack(spacing: 8) {
                    CategoryTag(category: item.category)
                    if let detail = detailLine(item) {
                        Text(detail)
                            .font(.footnote)
                            .foregroundStyle(Theme.inkSecondary)
                    }
                }
            }

            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Theme.inkTertiary)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .contentShape(Rectangle())
    }

    private func detailLine(_ item: ExtractedItem) -> String? {
        var parts: [String] = []
        if let due = item.dueDate, item.hasTime {
            parts.append(due.formatted(date: .omitted, time: .shortened))
        }
        if let target = item.preparationForTitle {
            parts.append("Before \(target.lowercasedFirst)")
        }
        if let trigger = item.locationTrigger {
            parts.append("\(trigger.kind.label) \(trigger.placeName)")
        } else if let store = item.store {
            parts.append(store)
        }
        if let recurrence = item.recurrence {
            parts.append(recurrence.label)
        }
        if let lead = item.reminderLeadMinutes {
            parts.append("\(lead) min before")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    private func clarificationCard(_ item: ExtractedItem) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "exclamationmark.circle")
                    .font(.title3)
                    .foregroundStyle(Theme.coral)
                Text(item.clarificationQuestion ?? "Is “\(item.title)” a task or an idea?")
                    .font(.body)
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 10) {
                Button {
                    resolve(item, as: .task)
                } label: {
                    Label("Task", systemImage: "checkmark")
                }
                .buttonStyle(SoftButtonStyle(fill: Theme.surfaceMuted, isCompact: true))

                Button {
                    resolve(item, as: .idea)
                } label: {
                    Label("Idea", systemImage: "lightbulb")
                }
                .buttonStyle(SoftButtonStyle(fill: Theme.surfaceMuted, isCompact: true))
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private func toggleSelection(_ item: ExtractedItem) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        items[index].isSelected.toggle()
    }

    private func resolve(_ item: ExtractedItem, as category: ItemCategory) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        withAnimation(.snappy(duration: 0.25)) {
            items[index].category = category
            items[index].needsClarification = false
            items[index].clarificationQuestion = nil
        }
    }
}

/// Lightweight editor for a single extracted item before it's saved.
struct ExtractedItemEditor: View {
    @Environment(\.dismiss) private var dismiss

    @State private var draft: ExtractedItem
    @State private var hasDate: Bool
    @State private var date: Date

    let onSave: (ExtractedItem) -> Void
    let onDelete: (UUID) -> Void

    init(item: ExtractedItem, onSave: @escaping (ExtractedItem) -> Void, onDelete: @escaping (UUID) -> Void) {
        _draft = State(initialValue: item)
        _hasDate = State(initialValue: item.dueDate != nil)
        _date = State(initialValue: item.dueDate ?? Date())
        self.onSave = onSave
        self.onDelete = onDelete
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("What") {
                    TextField("Title", text: $draft.title, axis: .vertical)
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
                }

                Section("How urgent") {
                    Picker("Priority", selection: $draft.priority) {
                        ForEach(Priority.allCases) { priority in
                            Text(priority.label).tag(priority)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section {
                    Button("Remove this item", role: .destructive) {
                        onDelete(draft.id)
                        dismiss()
                    }
                }
            }
            .navigationTitle("Edit item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        draft.dueDate = hasDate ? date : nil
                        if !hasDate { draft.hasTime = false }
                        onSave(draft)
                        dismiss()
                    }
                    .disabled(draft.title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
