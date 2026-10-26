/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */

type ImpressBackgroundKind =
	'none' | 'color' | 'gradient' | 'hatch' | 'bitmap' | 'pattern' | 'master';

/** Both mobile panels use the same page-fill commands and image destination. */
class ImpressBackgroundBridge {
	private static serial = 0;
	private static pendingImage: {
		token: string;
		layer: any;
		part: number;
	} | null = null;

	static apply(kind: ImpressBackgroundKind, rgb = 0xffffff): void {
		const map = (window as any).app?.map;
		if (map?.getDocType?.() !== 'presentation' || !map.isEditMode?.()) return;
		// A later background choice invalidates any outstanding image picker.
		this.pendingImage = null;
		if (kind === 'bitmap') {
			const token = 'background-' + Date.now() + '-' + ++this.serial;
			this.pendingImage = {
				token,
				layer: map._docLayer,
				part: map._docLayer._selectedPart,
			};
			(window as any).postMobileMessage(
				'IMPRESS_BACKGROUND_PICK_IMAGE ' + token,
			);
			return;
		}
		if (kind === 'pattern') {
			// Render a repeating pattern at the slide's aspect ratio. The normal
			// background import owns undo, persistence and thumbnail invalidation.
			const layer = map._docLayer;
			const width =
				layer.getPartWidth?.(layer._selectedPart) ||
				layer._partWidthTwips ||
				16000;
			const height =
				layer.getPartHeight?.(layer._selectedPart) ||
				layer._partHeightTwips ||
				9000;
			const canvas = document.createElement('canvas');
			const scale = 1024 / Math.max(width, height);
			canvas.width = Math.max(1, Math.round(width * scale));
			canvas.height = Math.max(1, Math.round(height * scale));
			const ctx = canvas.getContext('2d');
			if (!ctx) return;
			ctx.fillStyle = '#ffffff';
			ctx.fillRect(0, 0, canvas.width, canvas.height);
			ctx.fillStyle = '#d9e2f3';
			for (let y = 0; y < canvas.height; y += 16) {
				for (let x = 0; x < canvas.width; x += 16) {
					ctx.fillRect(x + (y % 32 ? 8 : 0), y, 8, 8);
				}
			}
			this.insertImage(canvas.toDataURL('image/png').split(',')[1]);
			return;
		}
		if (kind === 'none' || kind === 'master') {
			// Clear the overriding fill and set visibility in one native operation.
			(window as any).postMobileMessage(
				'IMPRESS_MASTER_BACKGROUND ' +
					JSON.stringify({
						viewId: map._docLayer._viewId,
						part: map._docLayer._selectedPart,
						visible: kind === 'master',
						documentSessionId:
							NativeBridge.getInstance().getDocumentSessionId(),
					}),
			);
			return;
		}
		if (kind === 'color') {
			map.sendUnoCommand(ImpressEditorCatalog.buildBackgroundColorCommand(rgb));
		} else if (kind === 'gradient') {
			map.sendUnoCommand(
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
		} else if (kind === 'hatch') {
			map.sendUnoCommand(
				'.uno:FillPageHatch ' +
					JSON.stringify({
						'FillPageHatch.Style': { type: 'short', value: 0 },
						'FillPageHatch.Color': { type: 'long', value: 0x729fcf },
						'FillPageHatch.Distance': { type: 'long', value: 200 },
						'FillPageHatch.Angle': { type: 'long', value: 450 },
					}),
			);
		}
	}

	static completeImagePicker(token: string, base64: string): void {
		const pending = this.pendingImage;
		if (!pending || pending.token !== token) return;
		this.pendingImage = null;
		const map = (window as any).app?.map;
		if (
			map?._docLayer !== pending.layer ||
			map._docLayer._selectedPart !== pending.part ||
			map.getDocType?.() !== 'presentation' ||
			!map.isEditMode?.() ||
			!base64
		)
			return;
		this.insertImage(base64);
	}

	private static insertImage(base64: string): void {
		(window as any).postMobileMessage(
			'insertfile name=slide_background.png type=selectbackground data=' +
				base64,
		);
	}
}

(window as any).ImpressBackgroundBridge = ImpressBackgroundBridge;
