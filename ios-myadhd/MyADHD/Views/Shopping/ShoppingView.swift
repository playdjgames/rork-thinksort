import SwiftUI

struct ShoppingView: View {
    @Environment(BrainStore.self) private var store

    @State private var newItem: String = ""
    @State private var newStore: String = ""
    @FocusState private var isFieldFocused: Bool

    private var groups: [(store: String, items: [ShoppingItem])] { store.shoppingByStore }
    private var hasPurchased: Bool { store.shopping.contains(where: \.isPurchased) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                addCard

                if groups.isEmpty {
                    emptyState
                } else {
                    ForEach(groups, id: \.store) { group in
                        SectionCard(title: group.store) {
                            ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                                row(item)
                                if index < group.items.count - 1 { RowDivider() }
                            }
                        }
                    }

                    if hasPurchased {
                        Button("Clear picked-up items") {
                            store.clearPurchasedShopping()
                        }
                        .buttonStyle(SoftButtonStyle(tint: Theme.inkSecondary, isCompact: true))
                    }
                }
            }
            .padding(.horizontal, Theme.gutter)
            .padding(.top, 8)
            .padding(.bottom, 28)
        }
        .canvas()
        .scrollIndicators(.hidden)
        .navigationTitle("Shopping")
    }

    private var addCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "cart.badge.plus")
                    .foregroundStyle(Theme.accent)
                TextField("Add an item", text: $newItem)
                    .focused($isFieldFocused)
                    .submitLabel(.done)
                    .onSubmit(add)
            }
            HStack(spacing: 10) {
                Image(systemName: "mappin.and.ellipse")
                    .foregroundStyle(Theme.inkTertiary)
                TextField("Store (optional)", text: $newStore)
                    .submitLabel(.done)
                    .onSubmit(add)
                Button("Add", action: add)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Theme.accent)
                    .disabled(newItem.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .padding(16)
        .card()
    }

    private func row(_ item: ShoppingItem) -> some View {
        Button {
            store.toggleShoppingItem(item.id)
        } label: {
            HStack(spacing: 14) {
                CheckCircle(isDone: item.isPurchased)
                Text(item.name)
                    .font(.body)
                    .foregroundStyle(item.isPurchased ? Theme.inkTertiary : Theme.ink)
                    .strikethrough(item.isPurchased, color: Theme.inkTertiary)
                Spacer(minLength: 4)
                if item.isPurchased {
                    Text("Got it")
                        .font(.caption)
                        .foregroundStyle(Theme.inkTertiary)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                store.deleteShoppingItem(item.id)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 10) {
            IconWell(symbol: "cart", size: 56)
            Text("Nothing on the list")
                .font(.title3.bold())
                .foregroundStyle(Theme.ink)
            Text("Say “I need batteries, dog food and paper towels” in a brain dump and they'll show up here.")
                .font(.subheadline)
                .foregroundStyle(Theme.inkSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card()
    }

    private func add() {
        let name = newItem.trimmingCharacters(in: .whitespaces)
        guard !name.isEmpty else { return }
        let storeName = newStore.trimmingCharacters(in: .whitespaces)
        store.addShoppingItem(name, store: storeName.isEmpty ? nil : storeName.capitalizedWords)
        newItem = ""
        isFieldFocused = true
    }
}

#Preview {
    NavigationStack { ShoppingView() }
        .environment(BrainStore())
}
