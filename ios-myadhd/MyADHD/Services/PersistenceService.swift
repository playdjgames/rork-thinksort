import Foundation
import OSLog

nonisolated let appLogger = Logger(subsystem: "app.rork.myadhd", category: "ThinkSort")

/// Loads and saves the app's data as a single JSON document in Application Support.
nonisolated struct PersistenceService: Sendable {
    private let fileURL: URL

    init(filename: String = "myadhd-data.json") {
        let directory = (try? FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )) ?? URL.documentsDirectory
        fileURL = directory.appendingPathComponent(filename)
    }

    func load() -> AppData? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        do {
            let data = try Data(contentsOf: fileURL)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(AppData.self, from: data)
        } catch {
            appLogger.error("Could not load saved data: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    func save(_ appData: AppData) {
        do {
            let encoder = JSONEncoder()
            encoder.dateEncodingStrategy = .iso8601
            encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
            let data = try encoder.encode(appData)
            try data.write(to: fileURL, options: [.atomic])
        } catch {
            appLogger.error("Could not save data: \(error.localizedDescription, privacy: .public)")
        }
    }

    func exportJSON(_ appData: AppData) -> String {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        guard let data = try? encoder.encode(appData), let text = String(data: data, encoding: .utf8) else {
            return "{}"
        }
        return text
    }

    func deleteAll() {
        try? FileManager.default.removeItem(at: fileURL)
    }
}
