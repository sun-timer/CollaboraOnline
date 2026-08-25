import Foundation
import UIKit

/// Cloud AI service for Writer P0 tasks (ticket 06).
///
/// Consumes `ai.request` envelopes handled by NativeBridgeHandler, streams
/// deltas back as `ai.stream`, and finalizes with `ai.done` / `ai.error`,
/// mirroring the Android `AiRequestManager` protocol (OpenAI-compatible SSE).
/// Prompt table is frozen in `docs/ios-writer-p0-catalog.md` §3.
@objc final class WriterAIService: NSObject {
    @objc static let shared = WriterAIService()

    enum ConfigKey {
        static let endpoint = "AI_MODEL_BASE_url"
        static let apiKey = "AI_MODEL_BASE_api_key"
        // Android AiModelConfigStore: modelKey(MODEL_BASE, FIELD_MODEL_NAME).
        static let model = "AI_MODEL_BASE_model_name"
    }

    private static let maxTokensCap = 8192
    private static let maxSeed = 999_999
    private static let maxTokensRatio = 0.8
    private static let seedRatio = 0.8

    private var tasks: [String: Task<Void, Never>] = [:]
    private let lock = NSLock()

    /// (type, requestId, payload) — requestId rides in the envelope header.
    @objc var eventEmitter: ((String, String, NSDictionary) -> Void)?


    /// Local consumer (host VC drives panels/modals for native-initiated
    /// requests); receives the same events as `eventEmitter`.
    @objc var localEventEmitter: ((String, String, NSDictionary) -> Void)?

    @objc func execute(_ requestId: String, payload: NSDictionary) {
        let task = Task<Void, Never> { [weak self] in
            _ = await self?.runRequest(requestId: requestId, payload: payload)
        }
        lock.lock()
        tasks[requestId] = task
        lock.unlock()
    }

    @objc func cancel(_ requestId: String) {
        lock.lock()
        let task = tasks.removeValue(forKey: requestId)
        lock.unlock()
        task?.cancel()
    }

    // MARK: - Request execution

    private func runRequest(requestId: String, payload: NSDictionary) async {
        // Idempotent: removes the task on every exit path.
        defer { finish(requestId: requestId) }

        emit(requestId: requestId, type: "ai.state", payload: ["state": "loading", "message": "AI 请求中"])

        guard let taskType = payload["taskType"] as? String, !taskType.isEmpty else {
            emit(requestId: requestId, type: "ai.error",
                 payload: ["code": "invalid_payload", "message": "taskType is required"])
            return
        }
        let selection = (payload["selection"] as? String) ?? ""
        let context = (payload["context"] as? NSDictionary) ?? [:]

        let defaults = UserDefaults.standard
        let endpoint = (defaults.string(forKey: WriterAIService.ConfigKey.endpoint) ?? "").trimmingCharacters(in: .whitespaces)
        let apiKey = (defaults.string(forKey: WriterAIService.ConfigKey.apiKey) ?? "").trimmingCharacters(in: .whitespaces)
        let model = (defaults.string(forKey: WriterAIService.ConfigKey.model) ?? "").trimmingCharacters(in: .whitespaces)

        // First-run condition: report unconfigured before anything else.
        guard !endpoint.isEmpty else {
            emit(requestId: requestId, type: "ai.state", payload: ["state": "unconfigured", "message": "请先在设置中配置基础模型的接口地址。"])
            emit(requestId: requestId, type: "ai.error", payload: ["code": "config_missing", "message": "请先在设置中配置基础模型的接口地址。"])
            return
        }
        guard !apiKey.isEmpty else {
            emit(requestId: requestId, type: "ai.state", payload: ["state": "unconfigured", "message": "请先在设置中配置基础模型的 API Key。"])
            emit(requestId: requestId, type: "ai.error", payload: ["code": "config_missing", "message": "请先在设置中配置基础模型的 API Key。"])
            return
        }
        guard let endpointURL = URL(string: endpoint) else {
            emit(requestId: requestId, type: "ai.error", payload: ["code": "request_failed", "message": "Invalid endpoint URL"])
            return
        }

        guard let messages = WriterAIPromptCatalog.buildMessages(taskType: taskType, selection: selection, context: context) else {
            emit(requestId: requestId, type: "ai.error", payload: ["code": "unsupported_task", "message": "Unknown taskType: \(taskType)"])
            return
        }

        // Sampling mirrors Android AiModelConfigStore.loadSamplingParams
        // defaults (catalog §4.5): max_tokens = round(0.8 × 8192), seed = round(0.8 × 999999).
        let maxTokens = Int((Double(WriterAIService.maxTokensCap) * WriterAIService.maxTokensRatio).rounded())
        let seed = Int((Double(WriterAIService.maxSeed) * WriterAIService.seedRatio).rounded())
        let body: [String: Any] = [
            "model": model,
            "stream": true,
            "messages": messages,
            "top_p": 0.5,
            "temperature": 0.9,
            "presence_penalty": 0.0,
            "frequency_penalty": 0.8,
            "max_tokens": maxTokens,
            "seed": seed,
        ]

        var request = URLRequest(url: endpointURL)
        request.httpMethod = "POST"
        request.timeoutInterval = 120
        request.setValue("application/json; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        guard request.httpBody != nil else {
            emit(requestId: requestId, type: "ai.error", payload: ["code": "request_failed", "message": "Failed to build request body"])
            return
        }

        let session = URLSession(configuration: .ephemeral)
        do {
            let (bytes, response) = try await session.bytes(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode < 200 || http.statusCode >= 300 {
                emit(requestId: requestId, type: "ai.error",
                     payload: ["code": "http_\(http.statusCode)", "message": "AI request failed (HTTP \(http.statusCode))"])
                return
            }

            var fullText = ""
            var streamingStateSent = false
            for try await line in bytes.lines {
                try Task.checkCancellation()
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                guard trimmed.hasPrefix("data:") else { continue }
                let data = trimmed.dropFirst("data:".count).trimmingCharacters(in: .whitespaces)
                if data.isEmpty { continue }
                if data == "[DONE]" { break }

                guard let chunk = try? JSONSerialization.jsonObject(with: Data(data.utf8)) as? [String: Any],
                      let choices = chunk["choices"] as? [[String: Any]],
                      let choice = choices.first else { continue }

                let delta: String
                if let deltaObj = choice["delta"] as? [String: Any], let content = deltaObj["content"] as? String {
                    delta = content
                } else if let text = choice["text"] as? String {
                    delta = text
                } else {
                    delta = ""
                }
                if delta.isEmpty { continue }

                if !streamingStateSent {
                    streamingStateSent = true
                    emit(requestId: requestId, type: "ai.state", payload: ["state": "streaming", "message": "AI response streaming"])
                }
                fullText += delta
                emit(requestId: requestId, type: "ai.stream", payload: ["state": "streaming", "delta": delta])
            }
            try Task.checkCancellation()
            emit(requestId: requestId, type: "ai.done", payload: ["state": "ready", "fullText": fullText])
        } catch is CancellationError {
            // Cancel ack is emitted by NativeBridgeHandler (ai.cancel branch).
        } catch let error as URLError where error.code == .cancelled {
            // URLSession surfaces task cancellation as URLError(.cancelled).
        } catch {
            emit(requestId: requestId, type: "ai.error",
                 payload: ["code": "request_failed", "message": error.localizedDescription])
        }
    }

    private func emit(requestId: String, type: String, payload: [String: Any]) {
        let dict = payload as NSDictionary
        DispatchQueue.main.async { [weak self] in
            self?.eventEmitter?(type, requestId, dict)
            self?.localEventEmitter?(type, requestId, dict)
        }
    }

    private func finish(requestId: String) {
        lock.lock()
        tasks.removeValue(forKey: requestId)
        lock.unlock()
    }
}

/// Prompt catalog mirroring Android `AiChatCoordinator.buildOperateMessages`
/// (frozen in ios-writer-p0-catalog.md §3). Prompts are byte-identical to
/// Android; `context` is reserved for future style/language expansion
/// (Android's buildOperateMessages ignores it today — parity by design).
enum WriterAIPromptCatalog {
    static func buildMessages(taskType: String, selection: String, context: NSDictionary) -> [[String: String]]? {
        let text = selection.trimmingCharacters(in: .whitespacesAndNewlines)
        switch taskType {
        case "continue_write":
            return messages(system: "You are a creative Chinese writer. Continue naturally in the same style and tone. Return only the continuation.",
                            user: "请自然流畅地接续以下文本，保持一致的风格和语气：\n\n---\n\(text)\n---")
        case "expand":
            return messages(system: "You are a detailed Chinese writer. Expand text with rich detail, examples, and arguments.",
                            user: "请将以下内容扩展得更详细丰富，增加细节、例证和论述：\n\n---\n\(text)\n---")
        case "polish":
            return messages(system: "You are a professional Chinese editor. Fix grammar, improve fluency and clarity. Return only the polished full text.",
                            user: "请优化以下文本的表达，修正语法错误，提升流畅度和专业性。直接返回润色后的全文：\n\n---\n\(text)\n---")
        case "summarize":
            return messages(system: "You are a concise summarizer. Extract key points precisely. Return only the summary.",
                            user: "请用简洁的语言概括以下内容的核心要点：\n\n---\n\(text)\n---")
        case "condense":
            return messages(system: "You are a text condenser. Reduce length while preserving key meaning.",
                            user: "请压缩以下文本，保留关键信息，去除冗余，缩减至原长度的一半左右：\n\n---\n\(text)\n---")
        case "rewrite":
            return messages(system: "You are a versatile Chinese writer. Rewrite in a fresh way while preserving original meaning.",
                            user: "请用不同的表达方式和句式重写以下内容，保持原意不变：\n\n---\n\(text)\n---")
        case "translate":
            return messages(system: "You are a professional Chinese-English translator. Translate naturally and accurately. Return only the translation.",
                            user: "请将以下中文翻译成自然流畅的英文：\n\n---\n\(text)\n---")
        default:
            return nil
        }
    }

    private static func messages(system: String, user: String) -> [[String: String]] {
        [
            ["role": "system", "content": system],
            ["role": "user", "content": user],
        ]
    }
}