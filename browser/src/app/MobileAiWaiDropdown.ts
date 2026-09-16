/*
 * Anchored single-choice menu (Android WaiDropdownPopup subset).
 */

interface MobileAiWaiDropdownOptions {
	anchor: HTMLElement;
	labels: string[];
	selectedIndex?: number;
	onPick: (index: number, label: string) => void;
	onDismiss?: () => void;
}

class MobileAiWaiDropdown {
	private static activeRoot: HTMLDivElement | null = null;

	static show(options: MobileAiWaiDropdownOptions): void {
		MobileAiWaiDropdown.dismiss();
		const labels = options.labels;
		if (!labels.length) {
			return;
		}
		const selectedIndex = Math.max(
			0,
			Math.min(options.selectedIndex || 0, labels.length - 1),
		);

		const root = document.createElement('div');
		root.className = 'mobile-ai-wai-dropdown-root';
		root.setAttribute('role', 'presentation');
		root.onclick = (event) => {
			if (event.target === root) {
				MobileAiWaiDropdown.dismiss(options.onDismiss);
			}
		};

		const panel = document.createElement('div');
		panel.className = 'mobile-ai-wai-dropdown-panel';
		panel.setAttribute('role', 'listbox');
		panel.onclick = (event) => event.stopPropagation();

		const rect = options.anchor.getBoundingClientRect();
		const maxWidth = Math.min(window.innerWidth - 24, 420);
		const left = Math.max(
			12,
			Math.min(rect.left, window.innerWidth - maxWidth - 12),
		);
		panel.style.top = Math.round(rect.bottom + 6) + 'px';
		panel.style.left = Math.round(left) + 'px';
		panel.style.width = Math.round(Math.max(rect.width, maxWidth)) + 'px';

		labels.forEach((label, index) => {
			const row = document.createElement('button');
			row.type = 'button';
			row.className =
				'mobile-ai-wai-dropdown-row' +
				(index === selectedIndex ? ' mobile-ai-wai-dropdown-row--selected' : '');
			row.setAttribute('role', 'option');
			row.textContent = label;
			row.onclick = () => {
				options.onPick(index, label);
				MobileAiWaiDropdown.dismiss(options.onDismiss);
			};
			panel.appendChild(row);
		});

		root.appendChild(panel);
		document.body.appendChild(root);
		MobileAiWaiDropdown.activeRoot = root;
	}

	static dismiss(onDismiss?: () => void): void {
		if (MobileAiWaiDropdown.activeRoot) {
			MobileAiWaiDropdown.activeRoot.remove();
			MobileAiWaiDropdown.activeRoot = null;
		}
		if (onDismiss) {
			onDismiss();
		}
	}
}
