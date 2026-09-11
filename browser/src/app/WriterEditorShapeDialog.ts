/*
 * Writer shape picker dialog (iOS).
 *
 * Full ImpressShapeCatalog parity: grouped 6-column scrollable grid aligned with
 * Android ImpressShapePickerController + ImpressShapeCatalog.
 */

class WriterEditorShapeDialog {
	private static readonly GRID_COLS = 6;

	private readonly sheet: MobileAiSheet;
	private readonly controller: WriterEditorController;

	constructor(controller: WriterEditorController) {
		this.controller = controller;
		this.sheet = new MobileAiSheet({ title: '插入形状' });
		this.sheet.setBody(this.buildContent());
	}

	open(): void {
		this.sheet.open();
	}

	close(): void {
		this.sheet.close();
	}

	private buildContent(): HTMLElement {
		const scroll = document.createElement('div');
		scroll.className = 'writer-shape-picker';

		for (const section of WRITER_EDITOR_SHAPE_SECTIONS) {
			scroll.appendChild(this.sectionTitle(section.title));
			scroll.appendChild(this.shapeGrid(section.entries));
		}
		return scroll;
	}

	private sectionTitle(title: string): HTMLElement {
		const label = document.createElement('h3');
		label.className = 'writer-shape-picker__section-title';
		label.textContent = title;
		return label;
	}

	private shapeGrid(entries: (WriterEditorShapeEntry | null)[]): HTMLElement {
		const card = document.createElement('div');
		card.className = 'writer-shape-picker__grid-card';

		let row: HTMLElement | null = null;
		for (let i = 0; i < entries.length; i++) {
			if (i % WriterEditorShapeDialog.GRID_COLS === 0) {
				row = document.createElement('div');
				row.className = 'writer-shape-picker__row';
				card.appendChild(row);
			}
			const cell = this.shapeCell(entries[i]);
			if (row) {
				row.appendChild(cell);
			}
		}
		return card;
	}

	private shapeCell(entry: WriterEditorShapeEntry | null): HTMLElement {
		const cell = document.createElement('div');
		cell.className = 'writer-shape-picker__cell';
		if (entry === null) {
			cell.setAttribute('aria-hidden', 'true');
			return cell;
		}

		const button = document.createElement('button');
		button.type = 'button';
		button.className = 'writer-shape-picker__btn';
		button.setAttribute('aria-label', '插入形状 ' + entry.index);

		const icon = document.createElement('span');
		icon.className = 'writer-shape-picker__icon';
		icon.innerHTML = getWriterEditorShapeIcon(entry.index);
		button.appendChild(icon);

		button.onclick = () => {
			this.controller.insertShapeUno(entry.unoCommand);
			this.sheet.close();
		};
		cell.appendChild(button);
		return cell;
	}
}
