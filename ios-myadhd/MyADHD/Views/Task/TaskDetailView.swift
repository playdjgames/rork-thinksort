import SwiftUI

struct TaskDetailView: View {
    let taskID: UUID

    @Environment(BrainStore.self) private var store
    @Environment(AppRouter.self) private var router
    @Environment(\.dismiss) private var dismiss

    @State private var newPrepItem: String = ""
    @State private var newStep: String = ""
    @State private var showingEditor = false
    @State private var showingDependencyPicker = false
    @State private var showDeleteConfirm = false

    private var task: TaskItem? { store.task(id: taskID) }

    var body: some View {
        Group {
            if let task {
                content(task)
            } else {
                ContentUnavailableView("Task not found", systemImage: "tray", description: Text("It may have been deleted."))
                    .canvas()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            if let task {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button { showingEditor = true } label: { Label("Edit details", systemImage: "pencil") }
                        Button { store.breakDown(taskID: task.id) } label: { Label("Break into steps", systemImage: "list.bullet.indent") }
                            .disabled(!task.subtasks.isEmpty)
                        Button { showingDependencyPicker = true } label: { Label("Add a prerequisite", systemImage: "arrow.triangle.branch") }
                        Button { router.show(sheet: .stuck(task.id)) } label: { Label("I'm stuck", systemImage: "exclamationmark.circle") }
                        Divider()
                        Button { store.postpone(taskID: task.id) } label: { Label("Move to tomorrow", systemImage: "calendar.badge.clock") }
                        Button { store.setStatus(.waiting, taskID: task.id) } label: { Label("Mark as waiting", systemImage: "hourglass") }
                        Divider()
                        Button(role: .destructive) { showDeleteConfirm = true } label: { Label("Delete", systemImage: "trash") }
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.subheadline.weight(.bold))
                            .frame(width: 34, height: 34)
                            .background(Theme.accentSoft, in: .circle)
                    }
                    .accessibilityLabel("Task options")
                }
            }
        }
        .sheet(isPresented: $showingEditor) {
            if let task {
                TaskEditorView(task: task) { store.update($0) }
            }
        }
        .sheet(isPresented: $showingDependencyPicker) {
            if let task {
                DependencyPickerView(task: task)
            }
        }
        .confirmationDialog("Delete this task?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                store.delete(taskID: taskID)
                dismiss()
            }
            Button("Keep it", role: .cancel) {}
        }
    }

    // MARK: - Content

    private func content(_ task: TaskItem) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header(task)

                if !task.details.isEmpty || !task.notes.isEmpty {
                    notesCard(task)
                }

                if !task.subtasks.isEmpty {
                    stepsSection(task)
                } else {
                    breakDownPrompt(task)
                }

                if !task.preparationItems.isEmpty || !TaskBreakdown.preparationSuggestions(for: task.title).isEmpty {
                    prepSection(task)
                }

                let dependencies = store.dependencies(of: task)
                if !dependencies.isEmpty {
                    dependencySection(dependencies)
                }

                let dependents = store.dependents(of: task)
                if !dependents.isEmpty {
                    dependentsSection(dependents)
                }

                attributesSection(task)
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.bottom, 24)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .safeAreaInset(edge: .bottom) {
            bottomAction(task)
        }
    }

    private func header(_ task: TaskItem) -> some View {
        HStack(alignment: .top, spacing: 14) {
            IconWell(symbol: task.category.symbol, size: 62)
            VStack(alignment: .leading, spacing: 6) {
                Text(task.title)
                    .font(.title.bold())
                    .foregroundStyle(Theme.ink)
                    .fixedSize(horizontal: false, vertical: true)
                Text(task.whenDescription())
                    .font(.subheadline)
                    .foregroundStyle(task.isOverdue() ? Theme.coral : Theme.inkSecondary)
                if task.isOverdue() {
                    Label("Overdue", systemImage: "exclamationmark.circle.fill")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.coral)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.top, 4)
    }

    private func notesCard(_ task: TaskItem) -> some View {
        SectionCard(title: "Notes") {
            Text([task.details, task.notes].filter { !$0.isEmpty }.joined(separator: "\n\n"))
                .font(.body)
                .foregroundStyle(Theme.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
        }
    }

    private func stepsSection(_ task: TaskItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Eyebrow(text: "Steps")
                Spacer()
                Text("\(task.completedSubtaskCount) of \(task.subtasks.count)")
                    .font(.caption)
                    .foregroundStyle(Theme.inkTertiary)
            }
            .padding(.horizontal, 4)

            VStack(spacing: 0) {
                ForEach(Array(task.subtasks.enumerated()), id: \.element.id) { index, subtask in
                    Button {
                        store.toggleSubtask(taskID: task.id, subtaskID: subtask.id)
                    } label: {
                        HStack(spacing: 14) {
                            CheckCircle(isDone: subtask.isDone)
                            Text(subtask.title)
                                .font(.body)
                                .foregroundStyle(subtask.isDone ? Theme.inkTertiary : Theme.ink)
                                .strikethrough(subtask.isDone, color: Theme.inkTertiary)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .padding(.horizontal, 18)
                        .padding(.vertical, 14)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    RowDivider()
                }

                HStack(spacing: 12) {
                    Image(systemName: "plus.circle")
                        .foregroundStyle(Theme.accent)
                    TextField("Add a step", text: $newStep)
                        .submitLabel(.done)
                        .onSubmit {
                            store.addSubtask(newStep, taskID: task.id)
                            newStep = ""
                        }
                }
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
            }
            .card()
        }
    }

    private func breakDownPrompt(_ task: TaskItem) -> some View {
        Button {
            store.breakDown(taskID: task.id)
        } label: {
            HStack(spacing: 14) {
                IconWell(symbol: "list.bullet.indent", size: 42)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Feels too big?")
                        .font(.headline)
                        .foregroundStyle(Theme.ink)
                    Text("Break it into smaller steps")
                        .font(.footnote)
                        .foregroundStyle(Theme.inkSecondary)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Theme.inkTertiary)
            }
            .padding(14)
            .frame(maxWidth: .infinity)
            .card()
        }
        .buttonStyle(.plain)
    }

    private func prepSection(_ task: TaskItem) -> some View {
        SectionCard(title: "Before you leave") {
            ForEach(Array(task.preparationItems.enumerated()), id: \.element.id) { index, item in
                Button {
                    store.togglePreparationItem(taskID: task.id, itemID: item.id)
                } label: {
                    HStack(spacing: 14) {
                        CheckCircle(isDone: item.isDone)
                        Text(item.title)
                            .font(.body)
                            .foregroundStyle(item.isDone ? Theme.inkTertiary : Theme.ink)
                            .strikethrough(item.isDone, color: Theme.inkTertiary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        store.removePreparationItem(item.id, taskID: task.id)
                    } label: {
                        Label("Remove", systemImage: "trash")
                    }
                }
                RowDivider()
            }

            if task.preparationItems.isEmpty {
                suggestionRow(task)
            }

            HStack(spacing: 12) {
                Image(systemName: "plus.circle")
                    .foregroundStyle(Theme.accent)
                TextField("Add something to bring", text: $newPrepItem)
                    .submitLabel(.done)
                    .onSubmit {
                        store.addPreparationItem(newPrepItem, taskID: task.id)
                        newPrepItem = ""
                    }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
        }
    }

    private func suggestionRow(_ task: TaskItem) -> some View {
        let suggestions = TaskBreakdown.preparationSuggestions(for: task.title)
        return Group {
            if !suggestions.isEmpty {
                Button {
                    for suggestion in suggestions {
                        store.addPreparationItem(suggestion, taskID: task.id)
                    }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "sparkles")
                            .foregroundStyle(Theme.accent)
                        Text("Add the usual: \(suggestions.joined(separator: ", "))")
                            .font(.footnote)
                            .foregroundStyle(Theme.accent)
                            .multilineTextAlignment(.leading)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                RowDivider()
            }
        }
    }

    private func dependencySection(_ dependencies: [TaskItem]) -> some View {
        SectionCard(title: "Depends on") {
            ForEach(Array(dependencies.enumerated()), id: \.element.id) { index, dependency in
                Button {
                    router.openTask(dependency.id)
                } label: {
                    HStack(spacing: 14) {
                        Button {
                            if dependency.status == .completed {
                                store.reopen(taskID: dependency.id)
                            } else {
                                store.complete(taskID: dependency.id)
                            }
                        } label: {
                            CheckCircle(isDone: dependency.status == .completed)
                        }
                        .buttonStyle(.plain)

                        VStack(alignment: .leading, spacing: 3) {
                            Text(dependency.title)
                                .font(.body)
                                .foregroundStyle(Theme.ink)
                            Text(dependency.status == .completed ? "Done" : dependency.whenDescription())
                                .font(.footnote)
                                .foregroundStyle(Theme.inkSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)

                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(Theme.inkTertiary)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if index < dependencies.count - 1 { RowDivider() }
            }
        }
    }

    private func dependentsSection(_ dependents: [TaskItem]) -> some View {
        SectionCard(title: "Needed for") {
            ForEach(Array(dependents.enumerated()), id: \.element.id) { index, dependent in
                Button {
                    router.openTask(dependent.id)
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "arrow.turn.down.right")
                            .font(.footnote)
                            .foregroundStyle(Theme.accent)
                        Text(dependent.title)
                            .font(.body)
                            .foregroundStyle(Theme.ink)
                        Spacer(minLength: 4)
                        Text(dependent.timeLabel())
                            .font(.footnote)
                            .foregroundStyle(Theme.inkSecondary)
                    }
                    .padding(.horizontal, 18)
                    .padding(.vertical, 14)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                if index < dependents.count - 1 { RowDivider() }
            }
        }
    }

    private func attributesSection(_ task: TaskItem) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 14) {
                IconWell(symbol: task.priority.symbol, size: 38)
                Text("Priority")
                    .font(.body)
                    .foregroundStyle(Theme.inkSecondary)
                Spacer(minLength: 4)
                Picker("Priority", selection: Binding(
                    get: { task.priority },
                    set: { store.setPriority($0, taskID: task.id) }
                )) {
                    ForEach(Priority.allCases) { priority in
                        Text(priority.label).tag(priority)
                    }
                }
                .tint(Theme.ink)
                .labelsHidden()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            RowDivider(leading: 16)

            HStack(spacing: 14) {
                IconWell(symbol: task.status.symbol, size: 38)
                Text("Status")
                    .font(.body)
                    .foregroundStyle(Theme.inkSecondary)
                Spacer(minLength: 4)
                Picker("Status", selection: Binding(
                    get: { task.status },
                    set: { store.setStatus($0, taskID: task.id) }
                )) {
                    ForEach(TaskStatus.allCases) { status in
                        Text(status.label).tag(status)
                    }
                }
                .tint(Theme.ink)
                .labelsHidden()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            if let trigger = task.locationTrigger {
                RowDivider(leading: 16)
                HStack(spacing: 14) {
                    IconWell(symbol: "mappin.and.ellipse", size: 38)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(trigger.kind.label) \(trigger.placeName)")
                            .font(.subheadline)
                            .foregroundStyle(Theme.ink)
                        if !LocationService.shared.supportsGeofencing {
                            Text("Location alerts are off — you'll get a normal reminder instead.")
                                .font(.caption)
                                .foregroundStyle(Theme.inkSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .card()
    }

    private func bottomAction(_ task: TaskItem) -> some View {
        VStack(spacing: 10) {
            if task.status == .completed {
                Button {
                    store.reopen(taskID: task.id)
                } label: {
                    Label("Mark as not done", systemImage: "arrow.uturn.backward")
                }
                .buttonStyle(SoftButtonStyle())
            } else {
                Button {
                    store.start(taskID: task.id)
                    router.show(fullScreen: .oneThing(task.id))
                } label: {
                    Label(task.status == .inProgress ? "Resume This Task" : "Start This Task", systemImage: "play.fill")
                }
                .buttonStyle(PrimaryButtonStyle())

                HStack(spacing: 10) {
                    Button {
                        store.complete(taskID: task.id)
                        dismiss()
                    } label: {
                        Label("Done", systemImage: "checkmark")
                    }
                    .buttonStyle(SoftButtonStyle(isCompact: true))

                    Button {
                        store.postpone(taskID: task.id)
                    } label: {
                        Label("Tomorrow", systemImage: "clock")
                    }
                    .buttonStyle(SoftButtonStyle(isCompact: true))
                }
            }
        }
        .padding(.horizontal, Theme.gutter)
        .padding(.top, 12)
        .padding(.bottom, 8)
        .background(Theme.canvas.opacity(0.98))
    }
}

/// Picker that links an existing open task as a prerequisite.
struct DependencyPickerView: View {
    let task: TaskItem

    @Environment(BrainStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var newTitle: String = ""

    private var candidates: [TaskItem] {
        store.openTasks.filter { $0.id != task.id && !task.dependencyIDs.contains($0.id) }
    }

    var body: some View {
        NavigationStack {
            List {
                Section("Add a new prerequisite") {
                    HStack {
                        TextField("What has to happen first?", text: $newTitle)
                            .submitLabel(.done)
                            .onSubmit(addNew)
                        Button("Add", action: addNew)
                            .disabled(newTitle.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }

                if !candidates.isEmpty {
                    Section("Or pick something you already have") {
                        ForEach(candidates) { candidate in
                            Button {
                                store.addDependency(candidate.id, to: task.id)
                                dismiss()
                            } label: {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(candidate.title).foregroundStyle(Theme.ink)
                                    Text(candidate.whenDescription())
                                        .font(.footnote)
                                        .foregroundStyle(Theme.inkSecondary)
                                }
                            }
                        }
                    }
                }

                if !store.dependencies(of: task).isEmpty {
                    Section("Current prerequisites") {
                        ForEach(store.dependencies(of: task)) { dependency in
                            HStack {
                                Text(dependency.title)
                                Spacer()
                                Button("Remove", role: .destructive) {
                                    store.removeDependency(dependency.id, from: task.id)
                                }
                                .font(.footnote)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Prerequisites")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private func addNew() {
        let title = newTitle.trimmingCharacters(in: .whitespaces)
        guard !title.isEmpty else { return }
        var prerequisite = TaskItem(title: title.capitalizedFirst)
        prerequisite.priority = .soon
        if let due = task.dueDate {
            prerequisite.dueDate = Calendar.current.date(byAdding: .day, value: -1, to: due).map {
                max($0, Calendar.current.startOfDay(for: Date()))
            }
        }
        store.add(prerequisite)
        store.addDependency(prerequisite.id, to: task.id)
        newTitle = ""
        dismiss()
    }
}

#Preview {
    NavigationStack {
        TaskDetailView(taskID: BrainStore().tasks.first?.id ?? UUID())
    }
    .environment(BrainStore())
    .environment(AppRouter())
}
