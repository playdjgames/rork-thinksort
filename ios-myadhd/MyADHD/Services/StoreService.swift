import Foundation
import Observation
import OSLog
import RevenueCat

/// Owns purchase state for the single lifetime unlock, plus the 7-day trial
/// clock. ThinkSort has no account, so the trial start date lives on device and
/// the purchase itself is restored from the App Store.
@Observable
@MainActor
final class StoreService {
    static let shared = StoreService()

    static let trialDays = 7
    static let lifetimeProductID = "app.rork.49yzh2pn5gqv6dilmmz4x.lifetime"
    private static let entitlementID = "thinksort_pro"

    /// True once the lifetime unlock is owned. Seeded from the cached flag so
    /// the app opens unlocked offline, then confirmed by RevenueCat.
    private(set) var isUnlocked = false
    private(set) var lifetimePriceText: String?
    private(set) var isReady = false
    var isPurchasing = false
    var isRestoring = false
    var error: String?

    private var lifetimePackage: Package?
    private var lifetimeProduct: StoreProduct?
    private var onUnlockChange: ((Bool) -> Void)?

    /// Call once from the app entry point, before any UI reads purchase state.
    func configure() {
        guard !Purchases.isConfigured else { return }
        let key = Config.EXPO_PUBLIC_REVENUECAT_IOS_API_KEY
        guard !key.isEmpty else {
            appLogger.notice("Purchases not configured: missing RevenueCat key.")
            return
        }
        Purchases.configure(withAPIKey: key)
    }

    /// Wires the cached entitlement so launch is instant and offline-safe.
    func attach(cachedUnlock: Bool, onChange: @escaping (Bool) -> Void) {
        onUnlockChange = onChange
        if cachedUnlock { isUnlocked = true }
        guard Purchases.isConfigured else {
            isReady = true
            return
        }
        Task { await listenForUpdates() }
        Task { await loadProducts() }
        Task { await refresh() }
    }

    // MARK: - Loading

    private func listenForUpdates() async {
        for await info in Purchases.shared.customerInfoStream {
            apply(info)
        }
    }

    func loadProducts() async {
        guard Purchases.isConfigured else { return }
        do {
            let offerings = try await Purchases.shared.offerings()
            if let package = offerings.current?.lifetime ?? offerings.current?.availablePackages.first {
                lifetimePackage = package
                lifetimeProduct = package.storeProduct
                lifetimePriceText = package.storeProduct.localizedPriceString
            } else {
                let products = await Purchases.shared.products([Self.lifetimeProductID])
                lifetimeProduct = products.first
                lifetimePriceText = products.first?.localizedPriceString
            }
        } catch {
            appLogger.error("Could not load products: \(error.localizedDescription, privacy: .public)")
        }
        isReady = true
    }

    func refresh() async {
        guard Purchases.isConfigured else { return }
        do {
            apply(try await Purchases.shared.customerInfo())
        } catch {
            appLogger.error("Could not read purchase status: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func apply(_ info: CustomerInfo) {
        let unlocked = info.entitlements[Self.entitlementID]?.isActive == true
            || info.nonSubscriptions.contains { $0.productIdentifier == Self.lifetimeProductID }
        setUnlocked(unlocked)
    }

    private func setUnlocked(_ value: Bool) {
        // Never revoke a cached unlock on a failed/empty refresh.
        guard value != isUnlocked, value || !isUnlocked else { return }
        isUnlocked = value
        onUnlockChange?(value)
    }

    // MARK: - Actions

    func purchaseLifetime() async {
        guard Purchases.isConfigured else {
            error = "The App Store isn't available right now. Please try again later."
            return
        }
        isPurchasing = true
        defer { isPurchasing = false }
        do {
            let result: PurchaseResultData
            if let lifetimePackage {
                result = try await Purchases.shared.purchase(package: lifetimePackage)
            } else if let lifetimeProduct {
                result = try await Purchases.shared.purchase(product: lifetimeProduct)
            } else {
                error = "Couldn't reach the App Store. Check your connection and try again."
                return
            }
            guard !result.userCancelled else { return }
            apply(result.customerInfo)
        } catch ErrorCode.purchaseCancelledError {
            // Not an error — the user backed out of the App Store sheet.
        } catch ErrorCode.paymentPendingError {
            error = "Your purchase is waiting for approval. ThinkSort unlocks as soon as it goes through."
        } catch let purchaseError {
            error = purchaseError.localizedDescription
        }
    }

    func restore() async {
        guard Purchases.isConfigured else {
            error = "The App Store isn't available right now. Please try again later."
            return
        }
        isRestoring = true
        defer { isRestoring = false }
        do {
            let info = try await Purchases.shared.restorePurchases()
            apply(info)
            if !isUnlocked {
                error = "We couldn't find a previous purchase on this Apple Account."
            }
        } catch let restoreError {
            error = restoreError.localizedDescription
        }
    }

    // MARK: - Access

    func accessState(trialStartedAt: Date?, now: Date = Date()) -> AccessState {
        if isUnlocked { return .unlocked }
        guard let trialStartedAt else { return .trialNotStarted }
        guard let end = Calendar.current.date(byAdding: .day, value: Self.trialDays, to: trialStartedAt) else {
            return .expired
        }
        guard now < end else { return .expired }
        let remaining = Int(ceil(end.timeIntervalSince(now) / 86_400))
        return .trial(daysRemaining: max(1, remaining))
    }
}
