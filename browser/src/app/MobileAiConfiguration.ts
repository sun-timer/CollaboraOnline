/*
 * Dedicated iOS AI configuration channel.
 *
 * API keys are sent only to the native Keychain-backed configuration handler,
 * never through NativeBridge ai.request payloads.
 */

interface MobileAiConfigurationValue {
	configured: boolean;
	endpoint: string;
	model: string;
}

class MobileAiConfiguration {
	static get(): Promise<MobileAiConfigurationValue> {
		return MobileAiConfiguration.post({ action: 'get' }) as Promise<MobileAiConfigurationValue>;
	}

	static save(
		endpoint: string,
		model: string,
		apiKey: string,
	): Promise<{ configured: boolean }> {
		return MobileAiConfiguration.post({
			action: 'save',
			endpoint,
			model,
			apiKey,
		}) as Promise<{ configured: boolean }>;
	}

	private static post(payload: { [key: string]: string }): Promise<any> {
		const handler = (window as any).webkit?.messageHandlers?.aiConfiguration;
		if (!handler || typeof handler.postMessage !== 'function') {
			return Promise.reject(new Error('iOS AI configuration channel unavailable'));
		}
		return handler.postMessage(payload);
	}
}
