/*
 * Writer insert-image dialog (iOS).
 *
 * Opens the native PHPicker on iOS via WRITER_OPEN_IMAGE_PICKER; falls back
 * to a hidden file input when native bridge is unavailable (tests / dev).
 */

class WriterEditorImageDialog {
	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '插入图片' });

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

		this.sheet.setBody(content);
	}

	open(): void {
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}

	private pickFromAlbum(): void {
		this.sheet.close();
		if ((window as any).ThisIsTheiOSApp) {
			this.controller.requestNativeImagePicker();
			return;
		}
		this.openFileInput();
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
