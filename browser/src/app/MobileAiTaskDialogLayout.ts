/*
 * Shared Android-style AI task dialog body (input / result stages).
 */

interface MobileAiTaskDialogLayoutOptions {
	generateLabel?: string;
	/** Keep input slot visible after result (e.g. translate). */
	keepInputOnResult?: boolean;
}

interface MobileAiTaskDialogControls {
	root: HTMLDivElement;
	inputSlot: HTMLDivElement;
	resultScroll: HTMLDivElement;
	resultPreview: HTMLDivElement;
	status: HTMLDivElement;
	generateButton: HTMLButtonElement;
	stopButton: HTMLButtonElement;
	copyRow: HTMLButtonElement;
	regenerateButton: HTMLButtonElement;
	applyButton: HTMLButtonElement;
	setStage: (stage: 'input' | 'result' | 'generating') => void;
}

class MobileAiTaskDialogLayout {
	private static setVisible(el: HTMLElement, visible: boolean): void {
		if (visible) {
			el.removeAttribute('hidden');
		} else {
			el.setAttribute('hidden', '');
		}
	}

	static create(
		options: MobileAiTaskDialogLayoutOptions = {},
	): MobileAiTaskDialogControls {
		const generateLabel = options.generateLabel || '生成';
		const keepInputOnResult = options.keepInputOnResult === true;

		const root = document.createElement('div');
		root.className = 'mobile-ai-task-dialog';

		const inputSlot = document.createElement('div');
		inputSlot.className = 'mobile-ai-task-dialog__input';
		root.appendChild(inputSlot);

		const resultScroll = document.createElement('div');
		resultScroll.className = 'mobile-ai-task-dialog__result';
		resultScroll.setAttribute('hidden', '');
		const resultPreview = document.createElement('div');
		resultPreview.className = 'mobile-ai-task-dialog__result-body';
		resultPreview.setAttribute('aria-live', 'polite');
		resultScroll.appendChild(resultPreview);
		root.appendChild(resultScroll);

		const status = document.createElement('div');
		status.className = 'mobile-ai-task-dialog__status';
		status.setAttribute('role', 'status');
		root.appendChild(status);

		const copyRow = document.createElement('button');
		copyRow.type = 'button';
		copyRow.className = 'mobile-ai-task-dialog__copy-row';
		copyRow.setAttribute('hidden', '');
		copyRow.innerHTML =
			'<span class="mobile-ai-task-dialog__copy-icon" aria-hidden="true"></span>' +
			'<span>复制</span>';
		copyRow.setAttribute('aria-label', '复制结果');
		root.appendChild(copyRow);

		const ctaBar = document.createElement('div');
		ctaBar.className = 'mobile-ai-task-dialog__cta-bar';
		const generateButton = document.createElement('button');
		generateButton.type = 'button';
		generateButton.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--primary mobile-ai-task-dialog__generate';
		generateButton.textContent = generateLabel;
		ctaBar.appendChild(generateButton);

		const stopButton = document.createElement('button');
		stopButton.type = 'button';
		stopButton.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--secondary mobile-ai-task-dialog__stop';
		stopButton.textContent = '停止生成';
		stopButton.setAttribute('hidden', '');
		ctaBar.appendChild(stopButton);
		root.appendChild(ctaBar);

		const resultBar = document.createElement('div');
		resultBar.className = 'mobile-ai-task-dialog__result-bar';
		resultBar.setAttribute('hidden', '');
		const regenerateButton = document.createElement('button');
		regenerateButton.type = 'button';
		regenerateButton.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--secondary';
		regenerateButton.textContent = '重新生成';
		resultBar.appendChild(regenerateButton);
		const applyButton = document.createElement('button');
		applyButton.type = 'button';
		applyButton.className =
			'mobile-ai-task-dialog__btn mobile-ai-task-dialog__btn--primary';
		applyButton.textContent = '插入文档';
		resultBar.appendChild(applyButton);
		root.appendChild(resultBar);

		const setStage = (stage: 'input' | 'result' | 'generating'): void => {
			const showResult = stage === 'result';
			const generating = stage === 'generating';
			const showInput =
				stage === 'input' ||
				(keepInputOnResult && (stage === 'result' || generating));
			MobileAiTaskDialogLayout.setVisible(inputSlot, showInput);
			MobileAiTaskDialogLayout.setVisible(
				resultScroll,
				showResult || generating,
			);
			MobileAiTaskDialogLayout.setVisible(copyRow, showResult);
			MobileAiTaskDialogLayout.setVisible(ctaBar, !showResult);
			MobileAiTaskDialogLayout.setVisible(generateButton, !showResult && !generating);
			MobileAiTaskDialogLayout.setVisible(stopButton, generating);
			MobileAiTaskDialogLayout.setVisible(resultBar, showResult);
			root.classList.toggle('mobile-ai-task-dialog--result', showResult);
			root.classList.toggle('mobile-ai-task-dialog--generating', generating);
		};

		setStage('input');

		return {
			root,
			inputSlot,
			resultScroll,
			resultPreview,
			status,
			generateButton,
			stopButton,
			copyRow,
			regenerateButton,
			applyButton,
			setStage,
		};
	}

	static cardField(label: string, control: HTMLElement): HTMLDivElement {
		const card = document.createElement('div');
		card.className = 'mobile-ai-task-dialog__card';
		if (label) {
			const caption = document.createElement('span');
			caption.className = 'mobile-ai-task-dialog__card-label';
			caption.textContent = label;
			card.appendChild(caption);
		}
		control.classList.add('mobile-ai-task-dialog__control');
		card.appendChild(control);
		return card;
	}

	static selectCard(labelText: string, onClick: () => void): HTMLButtonElement {
		const card = document.createElement('button');
		card.type = 'button';
		card.className = 'mobile-ai-task-dialog__select-card';
		const label = document.createElement('span');
		label.className = 'mobile-ai-task-dialog__select-card-label';
		label.textContent = labelText;
		card.appendChild(label);
		const chevron = document.createElement('span');
		chevron.className = 'mobile-ai-task-dialog__picker-card-chevron';
		chevron.setAttribute('aria-hidden', 'true');
		chevron.textContent = '›';
		card.appendChild(chevron);
		card.onclick = onClick;
		return card;
	}

	static fieldInput(placeholder: string, ariaLabel: string): HTMLInputElement {
		const input = document.createElement('input');
		input.type = 'text';
		input.className = 'mobile-ai-task-dialog__field-input';
		input.placeholder = placeholder;
		input.setAttribute('aria-label', ariaLabel);
		return input;
	}

	static pickerCard(
		label: string,
		valueText: string,
		onClick: () => void,
	): HTMLButtonElement {
		const card = document.createElement('button');
		card.type = 'button';
		card.className = 'mobile-ai-task-dialog__picker-card';
		const left = document.createElement('span');
		left.className = 'mobile-ai-task-dialog__picker-card-label';
		left.textContent = label;
		card.appendChild(left);
		const value = document.createElement('span');
		value.className = 'mobile-ai-task-dialog__picker-card-value';
		value.textContent = valueText;
		card.appendChild(value);
		const chevron = document.createElement('span');
		chevron.className = 'mobile-ai-task-dialog__picker-card-chevron';
		chevron.setAttribute('aria-hidden', 'true');
		chevron.textContent = '▾';
		card.appendChild(chevron);
		card.onclick = onClick;
		return card;
	}

	static multilineInput(
		placeholder: string,
		ariaLabel: string,
		minRows = 4,
	): HTMLTextAreaElement {
		const area = document.createElement('textarea');
		area.className = 'mobile-ai-task-dialog__textarea';
		area.rows = minRows;
		area.placeholder = placeholder;
		area.setAttribute('aria-label', ariaLabel);
		return area;
	}
}
