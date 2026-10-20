import Foundation
import CoreLocation
import OSLog

/// Location-based reminders. When permission is missing, ThinkSort falls back to a
/// plain reminder — the feature degrades, it never breaks.
@Observable
final class LocationService: NSObject, CLLocationManagerDelegate {
    static let shared = LocationService()

    private let manager = CLLocationManager()

    private(set) var authorizationStatus: CLAuthorizationStatus
    private(set) var monitoredPlaces: [String] = []
    /// Place name the user most recently arrived at or left, if any.
    private(set) var lastTriggeredPlace: String?

    override private init() {
        authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var isAuthorized: Bool {
        authorizationStatus == .authorizedAlways || authorizationStatus == .authorizedWhenInUse
    }

    var supportsGeofencing: Bool {
        authorizationStatus == .authorizedAlways &&
        CLLocationManager.isMonitoringAvailable(for: CLCircularRegion.self)
    }

    func requestWhenInUse() {
        manager.requestWhenInUseAuthorization()
    }

    func requestAlways() {
        manager.requestAlwaysAuthorization()
    }

    func startMonitoring(place: String, latitude: Double, longitude: Double, identifier: String) {
        guard supportsGeofencing else { return }
        let region = CLCircularRegion(
            center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            radius: 150,
            identifier: identifier
        )
        region.notifyOnEntry = true
        region.notifyOnExit = true
        manager.startMonitoring(for: region)
        if !monitoredPlaces.contains(place) { monitoredPlaces.append(place) }
    }

    func stopMonitoringAll() {
        for region in manager.monitoredRegions {
            manager.stopMonitoring(for: region)
        }
        monitoredPlaces.removeAll()
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorizationStatus = status
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        let identifier = region.identifier
        Task { @MainActor in
            self.lastTriggeredPlace = identifier
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        let identifier = region.identifier
        Task { @MainActor in
            self.lastTriggeredPlace = identifier
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        appLogger.error("Location error: \(error.localizedDescription, privacy: .public)")
    }
}
