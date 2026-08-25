/*
 * Reusable single-select list dialog for the Writer editor function panel.
 *
 * Used for font-name and font-size pickers. Wraps the shared MobileAiSheet
 * surface and emits the chosen option so the panel can dispatch the UNO
 * command through WriterEditorController.
 */

interface WriterChooseOption {
	label: string;
	value: string;
}

class WriterEditorChooseDialog {
	private readonly sheet: MobileAiSheet;
	private readonly onSelect: (option: WriterChooseOption) => void;

	constructor(
		title: string,
		options: WriterChooseOption[],
		onSelect: (option: WriterChooseOption) => void,
	) {
		this.onSelect = onSelect;
		this.sheet = new MobileAiSheet({ title });

		const list = document.createElement('div');
		list.style.cssText = 'display:flex;flex-direction:column;';

		options.forEach((option) => {
			const button = document.createElement('button');
			button.type = 'button';
			button.textContent = option.label;
			button.setAttribute('aria-label', option.label);
			button.style.cssText =
				'text-align:left;padding:14px 16px;background:none;border:none;' +
				'border-bottom:1px solid #eceff1;font:inherit;font-size:16px;cursor:pointer;';
			button.onclick = () => {
				this.onSelect(option);
				this.sheet.close();
			};
			list.appendChild(button);
		});

		this.sheet.setBody(list);
	}

	open(): void {
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}
}
