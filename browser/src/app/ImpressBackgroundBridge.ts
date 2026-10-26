/*
 * Slide background commands that this Core already applies.
 *
 * FillPageStyle with only a style number does not change the page here.
 * Color, gradient and hatch go out as their own UNO commands. Bitmap opens
 * the photo picker and pattern paints a tile, both inserted as
 * selectbackground. None and "使用幻灯片背景" clear the page fill in one
 * native call, because DisplayMasterBackground ignores the boolean it is given.
 */
class ImpressBackgroundBridge {
	private static serial = 0;
	private static pendingBackground: { [requestId: string]: {
		callback?: (applied: boolean) => void;
		map: any;
		layer: any;
		viewId: number;
		part: number;
		session: string;
		timer: number;
	} } = {};
	private static pendingImage: {
		token: string;
		layer: any;
		part: number;
	} | null = null;

	static apply(kind: 'none' | 'color' | 'gradient' | 'hatch' | 'bitmap' | 'pattern' | 'master', rgb = 0xffffff,
		callback?: (applied: boolean) => void): boolean {
		const map = (window as any).app?.map;
		if (map?.getDocType?.() !== 'presentation' || map.isEditMode?.() === false) return false;
		this.pendingImage = null;
		if (kind === 'bitmap') {
			const token = 'background-' + Date.now() + '-' + ++this.serial;
			this.pendingImage = {
				token,
				layer: map._docLayer,
				part: map._docLayer?._selectedPart,
			};
			(window as any).postMobileMessage?.('IMPRESS_BACKGROUND_PICK_IMAGE ' + token);
			return true;
		}
		if (kind === 'pattern') {
			const layer = map._docLayer;
			const width = layer?.getPartWidth?.(layer._selectedPart) || layer?._partWidthTwips || 16000;
			const height = layer?.getPartHeight?.(layer._selectedPart) || layer?._partHeightTwips || 9000;
			const canvas = document.createElement('canvas');
			const scale = 1024 / Math.max(width, height);
			canvas.width = Math.max(1, Math.round(width * scale));
			canvas.height = Math.max(1, Math.round(height * scale));
			const ctx = canvas.getContext('2d');
			if (!ctx) return false;
			ctx.fillStyle = '#ffffff';
			ctx.fillRect(0, 0, canvas.width, canvas.height);
			ctx.fillStyle = '#d9e2f3';
			for (let y = 0; y < canvas.height; y += 16) {
				for (let x = 0; x < canvas.width; x += 16) {
					ctx.fillRect(x + (y % 32 ? 8 : 0), y, 8, 8);
				}
			}
			this.insertImage(canvas.toDataURL('image/png').split(',')[1]);
			return true;
		}
		if (kind === 'none' || kind === 'master') return this.applyMasterBackground(kind === 'master', callback);
		if (kind === 'color') return this.applyColor(rgb);
		if (kind === 'gradient') {
			return this.send(
				'.uno:FillPageGradient ' +
					JSON.stringify({
						FillPageGradientJSON: {
							type: 'string',
							value: JSON.stringify({
								style: 'LINEAR',
								startcolor: '729fcf',
								endcolor: 'ffffff',
								angle: 900,
							}),
						},
					}),
			);
		}
		if (kind === 'hatch') {
			return this.send(
				'.uno:FillPageHatch ' +
					JSON.stringify({
						'FillPageHatch.Style': { type: 'short', value: 0 },
						'FillPageHatch.Color': { type: 'long', value: 0x729fcf },
						'FillPageHatch.Distance': { type: 'long', value: 200 },
						'FillPageHatch.Angle': { type: 'long', value: 450 },
					}),
			);
		}
		return false;
	}

	static applyColor(rgb: number, callback?: (sent: boolean) => void): boolean {
		const valid = Number.isInteger(rgb) && rgb >= 0 && rgb <= 0xffffff;
		const sent = valid && this.send(
			'.uno:FillPageColor {"FillColor.Color":{"type":"long","value":' + rgb + '}}');
		if (callback) callback(sent);
		return sent;
	}

	static applyMasterBackground(visible: boolean, callback?: (applied: boolean) => void): boolean {
		const map = (window as any).app?.map;
		const session = (window as any).NativeBridge?.getDocumentSessionId?.();
		const part = map?._docLayer?._selectedPart;
		const viewId = map?._docLayer?._viewId;
		const canSend = map?.getDocType?.() === 'presentation' && map.isEditMode?.() !== false
			&& typeof (window as any).postMobileMessage === 'function' && !!session
			&& Number.isInteger(viewId) && Number.isInteger(part);
		if (!canSend) {
			callback?.(false);
			return false;
		}
		const requestId = 'impress-background-' + (++this.serial);
		const timer = window.setTimeout(() => this.finishNativeBackground(requestId, false), 10000);
		this.pendingBackground[requestId] = { callback, map,
			layer: map._docLayer, viewId, part, session, timer };
		try {
			(window as any).postMobileMessage(
				'IMPRESS_MASTER_BACKGROUND ' +
					JSON.stringify({
						requestId,
						viewId,
						part,
						visible,
						documentSessionId: session,
					}),
			);
		} catch (_) {
			this.finishNativeBackground(requestId, false);
			return false;
		}
		return true;
	}

	static onNativeBackgroundResult(result: any): void {
		const pending = this.pendingBackground[result?.requestId];
		if (!pending) return;
		const currentSession = (window as any).NativeBridge?.getDocumentSessionId?.();
		const contextMatches = (window as any).app?.map === pending.map && pending.map._docLayer === pending.layer
			&& pending.map._docLayer?._viewId === pending.viewId && pending.map._docLayer?._selectedPart === pending.part
			&& currentSession === pending.session && result.documentSessionId === pending.session
			&& result.viewId === pending.viewId && result.part === pending.part;
		this.finishNativeBackground(result.requestId, contextMatches && result.success === true);
	}

	private static finishNativeBackground(requestId: string, applied: boolean): void {
		const pending = this.pendingBackground[requestId];
		if (!pending) return;
		delete this.pendingBackground[requestId];
		window.clearTimeout(pending.timer);
		pending.callback?.(applied);
	}

	static completeImagePicker(token: string, base64: string): void {
		const pending = this.pendingImage;
		if (!pending || pending.token !== token || !base64) return;
		this.pendingImage = null;
		const map = (window as any).app?.map;
		if (map?._docLayer !== pending.layer || map._docLayer?._selectedPart !== pending.part
			|| map.getDocType?.() !== 'presentation' || map.isEditMode?.() === false) return;
		this.insertImage(base64);
	}

	private static insertImage(base64: string): void {
		(window as any).postMobileMessage?.(
			'insertfile name=slide_background.png type=selectbackground data=' + base64,
		);
	}

	private static send(command: string): boolean {
		const map = (window as any).app?.map;
		const socket = (window as any).app?.socket;
		if (map?.getDocType?.() !== 'presentation' || map.isEditMode?.() === false) return false;
		try {
			if (typeof map.sendUnoCommand === 'function') {
				map.sendUnoCommand(command);
				return true;
			}
			if (typeof socket?.sendMessage === 'function') {
				socket.sendMessage('uno ' + command);
				return true;
			}
		} catch (_) {
			return false;
		}
		return false;
	}
}

(window as any).ImpressBackgroundBridge = ImpressBackgroundBridge;
