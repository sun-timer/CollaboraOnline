/*
 * Writer insert-image dialog (iOS).
 *
 * Opens the native PHPicker on iOS via WRITER_OPEN_IMAGE_PICKER; falls back
 * to a hidden file input when native bridge is unavailable (tests / dev).
 */

class WriterEditorImageDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly controller: WriterEditorController;

	constructor(
		controller: WriterEditorController,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.controller = controller;

		const content = document.createElement('div');
		content.className = 'writer-image-sheet';

		const album = document.createElement('button');
		album.type = 'button';
		album.className = 'writer-image-option';
		album.setAttribute('aria-label', '从相册选择');
		const albumIcon = document.createElement('span');
		albumIcon.className = 'writer-image-option__icon';
		const icon = WriterEditorIcons.get('image');
		if (icon) {
			albumIcon.innerHTML = icon;
		}
		album.appendChild(albumIcon);
		const albumLabel = document.createElement('span');
		albumLabel.textContent = '从相册选择';
		album.appendChild(albumLabel);
		album.onclick = () => this.pickFromAlbum();
		content.appendChild(album);

		if ((window as any).ThisIsTheiOSApp) {
			const camera = document.createElement('button');
			camera.type = 'button';
			camera.className = 'writer-image-option';
			camera.setAttribute('aria-label', '拍摄照片');
			const cameraIcon = document.createElement('span');
			cameraIcon.className = 'writer-image-option__icon';
			const cameraSvg = WriterEditorIcons.get('camera');
			if (cameraSvg) cameraIcon.innerHTML = cameraSvg;
			camera.appendChild(cameraIcon);
			const cameraLabel = document.createElement('span');
			cameraLabel.textContent = '拍摄照片';
			camera.appendChild(cameraLabel);
			camera.onclick = () => this.pickFromCamera();
			content.appendChild(camera);
		}

		this.subpage = writerEditorMountSubpageDialog('插入图片', content, host);
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}

	private pickFromAlbum(): void {
		this.subpage.close();
		if ((window as any).ThisIsTheiOSApp) {
			this.controller.requestNativeImagePicker();
			return;
		}
		this.openFileInput();
	}

	private pickFromCamera(): void {
		this.subpage.close();
		this.controller.requestNativeCamera();
	}

	private openFileInput(): void {
		const input = document.createElement('input');
		input.type = 'file';
		input.accept = 'image/*';
		input.onchange = () => {
			const file = input.files && input.files[0];
			if (!file) {
				return;
			}
			const reader = new FileReader();
			reader.onload = () => {
				const bytes = new Uint8Array(reader.result as ArrayBuffer);
				let str = '';
				for (let i = 0; i < bytes.length; i++) {
					str += String.fromCharCode(bytes[i]);
				}
				this.controller.insertImage(file.name, window.btoa(str));
			};
			reader.readAsArrayBuffer(file);
		};
		input.click();
	}
}

/** Selection tools for an existing Writer image. Core owns size constraints and crop handles. */
class WriterEditorImageEditDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly selectionId: string;
	private readonly viewId: number;
	private readonly documentId: string;

	constructor(controller: WriterEditorController, host?: WriterEditorInlineSubpageHost | null) {
		const content = document.createElement('div');
		content.className = 'writer-image-sheet';
		this.selectionId = String(GraphicSelection.extraInfo.id);
		this.viewId = app.map._docLayer._viewId;
		this.documentId = app.map.options.doc;
		const selectionHandles = GraphicSelection.extraInfo.handles.kinds.rectangle;
		const initialWidth = Math.abs(new cool.SimplePoint(
			parseInt(selectionHandles['4'][0].point.x), parseInt(selectionHandles['4'][0].point.y),
		).distanceTo([parseInt(selectionHandles['5'][0].point.x), parseInt(selectionHandles['5'][0].point.y)]));
		const initialHeight = Math.abs(new cool.SimplePoint(
			parseInt(selectionHandles['2'][0].point.x), parseInt(selectionHandles['2'][0].point.y),
		).distanceTo([parseInt(selectionHandles['7'][0].point.x), parseInt(selectionHandles['7'][0].point.y)]));
		const width = document.createElement('input');
		width.type = 'number'; width.min = '0.01'; width.max = '100000'; width.step = '0.1';
		width.setAttribute('aria-label', '宽度（厘米）');
		width.value = (initialWidth / 567).toFixed(1);
		const height = document.createElement('input');
		height.type = 'number'; height.min = '0.01'; height.max = '100000'; height.step = '0.1';
		height.setAttribute('aria-label', '高度（厘米）');
		height.value = (initialHeight / 567).toFixed(1);
		const keepRatio = document.createElement('input');
		keepRatio.type = 'checkbox'; keepRatio.checked = true;
		keepRatio.setAttribute('aria-label', '锁定纵横比');
		const dimensions = document.createElement('div');
		dimensions.className = 'writer-image-dimensions';
		const widthLabel = document.createElement('label'); widthLabel.textContent = '宽度（厘米）'; widthLabel.appendChild(width);
		const heightLabel = document.createElement('label'); heightLabel.textContent = '高度（厘米）'; heightLabel.appendChild(height);
		const ratioLabel = document.createElement('label'); ratioLabel.appendChild(keepRatio); ratioLabel.append(' 锁定纵横比');
		dimensions.append(widthLabel, heightLabel, ratioLabel);
		const initialRatio = initialHeight / initialWidth;
		let lastEdited: 'width' | 'height' | null = null;
		const validation = document.createElement('div');
		validation.className = 'writer-image-validation';
		validation.setAttribute('role', 'alert');
		const applySize = document.createElement('button');
		applySize.type = 'button'; applySize.className = 'writer-image-option'; applySize.textContent = '应用大小';
		applySize.disabled = true;
		const updateLockedDimension = (source: 'width' | 'height') => {
			lastEdited = source;
			applySize.disabled = false;
			validation.textContent = '';
			if (!keepRatio.checked) return;
			const value = Number(source === 'width' ? width.value : height.value);
			if (!Number.isFinite(value) || value <= 0) return;
			if (source === 'width') height.value = (value * initialRatio).toFixed(1);
			else width.value = (value / initialRatio).toFixed(1);
		};
		width.oninput = () => updateLockedDimension('width');
		height.oninput = () => updateLockedDimension('height');
		applySize.onclick = () => {
			const widthCm = Number(width.value), heightCm = Number(height.value);
			if (!Number.isFinite(widthCm) || !Number.isFinite(heightCm) || widthCm <= 0 || heightCm <= 0 ||
				widthCm > 100000 || heightCm > 100000 || lastEdited === null) {
				validation.textContent = '请输入有效的宽度和高度，然后应用更改。';
				return;
			}
			let widthTwips = Math.round(widthCm * 567);
			let heightTwips = Math.round(heightCm * 567);
			if (keepRatio.checked) {
				if (lastEdited === 'width') heightTwips = Math.round(widthTwips * initialHeight / initialWidth);
				else widthTwips = Math.round(heightTwips * initialWidth / initialHeight);
			}
			const result = controller.editImage('size', widthTwips, heightTwips, this.selectionId, this.viewId, this.documentId);
			if (result.dispatched === 'none') {
				validation.textContent = '图片选择已更改或无法调整大小，请关闭后重试。';
				return;
			}
			this.subpage.close();
		};
		content.append(dimensions, validation, applySize);
		const addAction = (label: string, action: 'rotateLeft' | 'rotateRight' | 'crop') => {
			const button = document.createElement('button');
			button.type = 'button';
			button.className = 'writer-image-option';
			button.textContent = label;
			button.disabled =
				(action === 'crop' && GraphicSelection.extraInfo.isResizable === false) ||
				((action === 'rotateLeft' || action === 'rotateRight') && GraphicSelection.extraInfo.isRotatable === false);
			button.onclick = () => {
				const result = controller.editImage(action, undefined, undefined, this.selectionId, this.viewId, this.documentId);
				if (result.dispatched === 'none') {
					validation.textContent = '图片选择已更改或当前图片不支持此操作。';
					return;
				}
				this.subpage.close();
			};
			content.appendChild(button);
		};
		addAction('向左旋转 90°', 'rotateLeft');
		addAction('向右旋转 90°', 'rotateRight');
		addAction('裁剪', 'crop');
		this.subpage = writerEditorMountSubpageDialog('图片编辑', content, host);
	}

	open(): void { this.subpage.open(); }
	close(): void { this.subpage.close(); }
}

// Android's native selection popup asks the browser to open the same image
// editor used by the iOS Writer panel. Keep this listener independent of the
// iOS-only WriterEditorPanel mount path.
if (typeof window !== 'undefined' && (window as any).ThisIsTheAndroidApp) {
	window.addEventListener('fabric:image:edit', () => {
		const info = GraphicSelection.extraInfo || {};
		const rectHandles = info.handles?.kinds?.rectangle;
		const point = (kind: string) => rectHandles?.[kind]?.[0]?.point;
		const points = ['2', '4', '5', '7'].map(point);
		if (typeof app === 'undefined' || app.map.getDocType() !== 'text' || !app.map.isEditMode() ||
			!GraphicSelection.hasActiveSelection() || !info.isWriterGraphic || !info.id ||
			!rectHandles || !GraphicSelection.handlesSection ||
			points.some(value => !value || !Number.isFinite(Number(value.x)) || !Number.isFinite(Number(value.y)))) {
			return;
		}
		const width = Math.hypot(Number(point('5').x) - Number(point('4').x), Number(point('5').y) - Number(point('4').y));
		const height = Math.hypot(Number(point('7').x) - Number(point('2').x), Number(point('7').y) - Number(point('2').y));
		if (info.isResizable === false || width <= 0 || height <= 0) return;
		new WriterEditorImageEditDialog(WriterEditorController.getInstance()).open();
	});
}
