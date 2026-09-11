describe('TypesetBridge', function () {
	it('posts typeset.fill with sections and optional images', function () {
		const posted: { [key: string]: any }[] = [];
		const origBridgeGetInstance = TypesetBridge.getInstance;
		const origNativeGetInstance = NativeBridge.getInstance;
		NativeBridge.getInstance = function () {
			return {
				isAvailable() {
					return true;
				},
				getDocumentSessionId() {
					return 'sess-1';
				},
				postMessage(message: { [key: string]: any }) {
					posted.push(message);
					return true;
				},
				subscribe() {
					return function () {};
				},
			} as any;
		};
		TypesetBridge.getInstance = function () {
			return new (TypesetBridge as any)(NativeBridge.getInstance());
		};
		const bridge = TypesetBridge.getInstance();
		bridge.fillTemplate(
			'paper',
			{ title: '标题', body: '正文' },
			'test.docx',
			{
				图1: {
					base64: 'abc',
					mimeType: 'image/png',
					extension: 'png',
				},
			},
		);
		assert.equal(posted.length, 1);
		assert.equal(posted[0].type, 'typeset.fill');
		assert.equal(posted[0].payload.typesetType, 'paper');
		assert.ok(posted[0].payload.images);
		assert.ok(posted[0].payload.images['图1']);
		TypesetBridge.getInstance = origBridgeGetInstance;
		NativeBridge.getInstance = origNativeGetInstance;
	});

	it('reports unavailable when NativeBridge is missing', function () {
		const origBridgeGetInstance = TypesetBridge.getInstance;
		const origNativeGetInstance = NativeBridge.getInstance;
		NativeBridge.getInstance = function () {
			return {
				isAvailable() {
					return false;
				},
				subscribe() {
					return function () {};
				},
			} as any;
		};
		TypesetBridge.getInstance = function () {
			return new (TypesetBridge as any)(NativeBridge.getInstance());
		};
		const bridge = TypesetBridge.getInstance();
		assert.equal(bridge.isAvailable(), false);
		TypesetBridge.getInstance = origBridgeGetInstance;
		NativeBridge.getInstance = origNativeGetInstance;
	});
});
