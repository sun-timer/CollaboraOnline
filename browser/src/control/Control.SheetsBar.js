/* -*- js-indent-level: 8 -*- */
/*
 * Copyright the Collabora Online contributors.
 *
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
/*
 * Control.SheetsBar - toolbar with buttons for scrolling tabs
 */

/* global _ JSDialog app */
class SheetsBar {

	constructor(map, showNavigation = true) {
		this.showNavigation = showNavigation;
		this.onAdd(map);
	}

	onAdd(map) {
		this.map = map;
		this.parentContainer = window.L.DomUtil.get('spreadsheet-toolbar');
		this.builder = new window.L.control.jsDialogBuilder(
			{
				mobileWizard: this,
				map: this.map,
				cssClass: 'jsdialog',
				suffix: 'spreadsheet-toolbar',
				callback: this.onSelectSheet.bind(this),
			});

		this.create();

		map.on('doclayerinit', this.onDocLayerInit, this);
		map.on('updateparts', this.onUpdateParts, this);
		app.events.on('updatepermission', this.onUpdatePermission.bind(this));
	}

	create() {
		var data = [
			{
				id: 'sheets-buttons-toolbox',
				type: 'toolbox',
				children: [
					{
						id: 'firstrecord',
						type: 'customtoolitem',
						text: _('Scroll to the first sheet'),
						command: 'firstrecord',
						visible: this.showNavigation
					},
					{
						id: 'prevrecord',
						type: 'customtoolitem',
						text: _('Scroll left'),
						command: 'prevrecord',
						visible: this.showNavigation,
						pressAndHold: true
					},
					{
						id: 'nextrecord',
						type: 'customtoolitem',
						text: _('Scroll right'),
						command: 'nextrecord',
						visible: this.showNavigation,
						pressAndHold: true
					},
					{
						id: 'lastrecord',
						type: 'customtoolitem',
						text: _('Scroll to the last sheet'),
						command: 'lastrecord',
						visible: this.showNavigation
					},
					{
						id: 'insertsheet',
						type: 'customtoolitem',
						text: _('Insert sheet'),
						command: 'insertsheet'
					},
					{
						id: 'sheetlist:SheetListMenu',
						type: 'menubutton',
						noLabel: true,
						text: _('Show sheet list'),
						command: 'sheetlist',
					},
				]
			}
		];

		this.parentContainer.replaceChildren();
		this.builder.build(this.parentContainer, data);
		this.bindInsertSheetButton();
		this.updateSheetListMenu();
	}

	bindInsertSheetButton() {
		var plus = this.parentContainer.querySelector('#insertsheet');
		if (!plus || plus.dataset.insertBound === '1') {
			return;
		}
		plus.dataset.insertBound = '1';
		// 捕获阶段截住点击。否则会冒泡成 toolbox 回调，core 把 insertsheet 当成未知控件弹出菜单。
		plus.addEventListener('click', (ev) => {
			ev.preventDefault();
			ev.stopPropagation();
			if (plus.hasAttribute('disabled')) {
				return;
			}
			this.insertSheetAtEnd();
		}, true);
	}

	insertSheetAtEnd() {
		var docLayer = this.map && this.map._docLayer;
		var nPos = docLayer && typeof docLayer._parts === 'number'
			? docLayer._parts
			: (typeof this.parts === 'number' ? this.parts : 0);
		this.map.insertPage.scrollToEnd = true;
		if (docLayer && docLayer._sheetSwitch) {
			this.map.insertPage(nPos);
			return;
		}
		if (window.app && app.socket && typeof app.socket.sendMessage === 'function') {
			var cmd = {Name: {type: 'string', value: ''}, Index: {type: 'long', value: nPos + 1}};
			app.socket.sendMessage('uno .uno:Insert ' + JSON.stringify(cmd));
		}
	}

	onDocLayerInit() {
		var docType = this.map.getDocType();
		if (docType == 'spreadsheet') {
			if (!window.mode.isMobile()) {
				this.show();
			}
		}
	}

	onUpdateParts(e) {
		this.partNames = e.partNames || [];
		this.selectedPart = e.selectedPart;
		this.parts = e.parts;
		this.updateSheetListMenu();
	}

	onUpdatePermission(e) {
		if (e.detail.perm === 'edit') {
			this.enableInsertion(true);
		} else {
			this.enableInsertion(false);
		}
	}

	updateSheetListMenu() {
		let sheetEntries = [];

		if (!this.partNames || this.partNames.length === 0) {
			return sheetEntries;
		}
		
		for (let i = 0; i < this.partNames.length; i++) {
			if (app.calc.isPartHidden(i)) {
				continue;
			}

			if (app.calc.isPartSheetView(i)) {
				continue;
			}

			let displayText = this.partNames[i];

			sheetEntries.push({
				id: 'selectsheet-' + i,
				text: displayText,
				selected: (i === this.selectedPart || app.calc.isDefaultPartOfSelectedSheetView(i))
			});
		}
		
		JSDialog.MenuDefinitions.set('SheetListMenu', sheetEntries);

	}

	onSelectSheet(objectType, eventType, object, data, builder) {
		if (object.id === 'sheetlist') {

			if (eventType === 'select') {
				const partIndex = parseInt(data.replace('selectsheet-', ''));
				if (!isNaN(partIndex) && partIndex !== this.map._docLayer._selectedPart) {
					this.map._docLayer._clearReferences();
					this.map.setPart(partIndex, false, true);
				}
			}
			return;
		}
		// toolbox 回调里 object 是工具条容器，命令在 data 里，不能只看 object.id。
		if ((object && object.id === 'insertsheet') || data === 'insertsheet') {
			if (eventType === 'click') {
				this.insertSheetAtEnd();
			}
			return;
		}
		builder._defaultCallbackHandler(objectType, eventType, object, data, builder);
  }

	enableInsertion(enable) {
		this.builder.executeAction(this.parentContainer, {
			'control_id': 'insertsheet',
			'action_type': enable ? 'enable' : 'disable'
		});
	}

	show() {
		this.parentContainer.style.display = 'grid';
	}

	hide() {
		this.parentContainer.style.display = 'none';
	}
}

JSDialog.SheetsBar = function (map, showNavigation) {
	return new SheetsBar(map, showNavigation);
};
