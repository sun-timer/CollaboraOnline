describe('WriterEditorShapeCatalog', function () {
	it('loads nine sections matching Android ImpressShapeCatalog', function () {
		assert.equal(WRITER_EDITOR_SHAPE_SECTIONS.length, 9);
		assert.equal(WRITER_EDITOR_SHAPE_SECTIONS[0].title, '线条和箭头');
		assert.equal(WRITER_EDITOR_SHAPE_SECTIONS[3].title, '基本形状');
	});

	it('exposes 148 selectable shape entries (Android catalog parity)', function () {
		const count = countWriterEditorShapeEntries();
		assert.equal(count, 148);
	});

	it('maps Line, BasicShapes.rectangle, and ArrowShapes.right-arrow UNO commands', function () {
		const commands = new Set<string>();
		for (const section of WRITER_EDITOR_SHAPE_SECTIONS) {
			for (const entry of section.entries) {
				if (entry !== null) {
					commands.add(entry.unoCommand);
				}
			}
		}
		assert.ok(commands.has('.uno:Line'));
		assert.ok(commands.has('.uno:BasicShapes.rectangle'));
		assert.ok(commands.has('.uno:ArrowShapes.right-arrow'));
	});

	it('provides SVG icons for catalog indices', function () {
		assert.ok(getWriterEditorShapeIcon(36).indexOf('<svg') >= 0);
		assert.ok(getWriterEditorShapeIcon(78).indexOf('<svg') >= 0);
		assert.equal(getWriterEditorShapeIcon(999), '');
	});
});
