/*
 * Reusable single-select list dialog for the Writer editor function panel.
 *
 * Uses WriterEditorSheet (function-panel chrome), not MobileAiSheet, to match
 * Android FunctionPanelController option picker pages.
 */

interface WriterChooseOption {
	label: string;
	value: string;
}

class WriterEditorChooseDialog {
	private readonly subpage: { open(): void; close(): void };
	private readonly onSelect: (option: WriterChooseOption) => void;

	constructor(
		title: string,
		options: WriterChooseOption[],
		onSelect: (option: WriterChooseOption) => void,
		selectedLabel?: string,
		host?: WriterEditorInlineSubpageHost | null,
	) {
		this.onSelect = onSelect;

		const list = document.createElement('div');
		list.className = 'writer-editor-option-list';
		const current =
			selectedLabel || (options.length > 0 ? options[0].label : '');

		options.forEach((option, index) => {
			const row = document.createElement('button');
			row.type = 'button';
			row.className = 'writer-editor-option-row';
			row.setAttribute('aria-label', option.label);
			const selected =
				option.label === current || option.value === current;
			if (selected) {
				row.classList.add('writer-editor-option-row--selected');
			}
			const name = document.createElement('span');
			name.className = 'writer-editor-option-row__label';
			name.textContent = option.label;
			row.appendChild(name);
			const check = document.createElement('span');
			check.className = 'writer-editor-option-row__check';
			check.setAttribute('aria-hidden', 'true');
			check.innerHTML =
				'<svg viewBox="0 0 24 24" width="20" height="20"><path fill="#1278d9" d="M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>';
			row.appendChild(check);
			row.onclick = () => {
				this.onSelect(option);
				this.close();
			};
			list.appendChild(row);
			if (index + 1 < options.length) {
				const divider = document.createElement('div');
				divider.className = 'writer-editor-option-list__divider';
				divider.setAttribute('aria-hidden', 'true');
				list.appendChild(divider);
			}
		});

		this.subpage = writerEditorMountSubpageDialog(title, list, host);
	}

	open(): void {
		this.subpage.open();
	}

	close(): void {
		this.subpage.close();
	}
}
