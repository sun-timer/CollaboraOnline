/* NativeBridge v1 contract fixtures. */

var NativeBridgeTestData = {
	validRequest: {
		protocolVersion: 1,
		channel: 'native',
		type: 'ai.request',
		requestId: 'request-1',
		documentSessionId: 'document-1',
		targetPlatform: 'any',
		payload: {
			taskType: 'polish',
			selection: 'text',
		},
	},
	unknownType: {
		protocolVersion: 1,
		channel: 'native',
		type: 'native.unknown',
	},
	missingRequestId: {
		protocolVersion: 1,
		channel: 'native',
		type: 'ai.request',
		payload: {},
	},
};
