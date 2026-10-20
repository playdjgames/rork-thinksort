import Foundation
import Speech
import AVFoundation
import OSLog

/// Live on-device speech-to-text for brain dumps and quick capture.
/// If permission is denied the UI falls back to typing — no fake transcription.
@Observable
final class SpeechService {
    enum State: Equatable {
        case idle
        case recording
        case denied(String)
        case unavailable(String)
    }

    private let recognizer = SFSpeechRecognizer(locale: Locale.current)
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    private(set) var state: State = .idle
    private(set) var transcript: String = ""
    private(set) var audioLevel: Double = 0

    var isRecording: Bool { state == .recording }

    var permissionDescription: String {
        switch SFSpeechRecognizer.authorizationStatus() {
        case .authorized: "On"
        case .denied, .restricted: "Off"
        case .notDetermined: "Not set up yet"
        @unknown default: "Unknown"
        }
    }

    func requestPermissions() async -> Bool {
        let speechGranted = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status == .authorized)
            }
        }
        guard speechGranted else { return false }
        return await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    func start(seed: String = "") async {
        guard !isRecording else { return }
        transcript = seed

        guard let recognizer, recognizer.isAvailable else {
            state = .unavailable("Speech recognition isn't available right now. You can type instead.")
            return
        }

        let granted = await requestPermissions()
        guard granted else {
            state = .denied("Microphone or speech access is off. Turn it on in Settings, or type your dump instead.")
            return
        }

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: [.duckOthers])
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let request = SFSpeechAudioBufferRecognitionRequest()
            request.shouldReportPartialResults = true
            request.requiresOnDeviceRecognition = recognizer.supportsOnDeviceRecognition
            self.request = request

            let inputNode = audioEngine.inputNode
            let format = inputNode.outputFormat(forBus: 0)
            inputNode.removeTap(onBus: 0)
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                request.append(buffer)
                guard let channel = buffer.floatChannelData?[0] else { return }
                let frames = Int(buffer.frameLength)
                var sum: Float = 0
                for index in 0..<frames { sum += abs(channel[index]) }
                let average = frames > 0 ? sum / Float(frames) : 0
                Task { @MainActor [weak self] in
                    self?.audioLevel = min(1, Double(average) * 12)
                }
            }

            audioEngine.prepare()
            try audioEngine.start()
            state = .recording

            let base = seed
            task = recognizer.recognitionTask(with: request) { [weak self] result, error in
                guard let self else { return }
                if let result {
                    let text = result.bestTranscription.formattedString
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        self.transcript = base.isEmpty ? text : base + " " + text
                    }
                }
                if error != nil || (result?.isFinal ?? false) {
                    Task { @MainActor [weak self] in
                        self?.stop()
                    }
                }
            }
        } catch {
            appLogger.error("Could not start recording: \(error.localizedDescription, privacy: .public)")
            state = .unavailable("Couldn't start the microphone. You can type instead.")
            cleanUp()
        }
    }

    func stop() {
        guard isRecording || audioEngine.isRunning else {
            state = .idle
            return
        }
        cleanUp()
        state = .idle
        audioLevel = 0
    }

    func reset() {
        stop()
        transcript = ""
    }

    func setTranscript(_ text: String) {
        transcript = text
    }

    private func cleanUp() {
        if audioEngine.isRunning {
            audioEngine.stop()
        }
        audioEngine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
