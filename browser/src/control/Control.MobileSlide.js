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
 * window.L.Control.MobileSlide is used to add new slide button on the Impress document.
 * 对齐 Figma:缩略图条右侧 40px 竖条(白底 + 左分割线 + 居中 + 图标),替代独立浮动按钮。
 */

window.L.Control.MobileSlide = window.L.Control.extend({
	options: {
		position: 'bottomright'
	},

	/*
	 * 不用 leaflet 的 addTo/onAdd:addTo 会把 container 移到 map 角落,
	 * 覆盖掉我们挂到 #presentation-controls-wrapper 的行为。
	 * 改为由 ImpressTileLayer 直接调 attach/detach 操作 DOM。
	 */
	attach: function (map) {
		if (map) {
			this._map = map;
		}
		if (this._attached) {
			return;
		}
		if (!this._container) {
			this._initLayout();
		}
		var wrapper = window.L.DomUtil.get('presentation-controls-wrapper');
		if (wrapper) {
			wrapper.appendChild(this._container);
		}
		this._attached = true;
	},

	detach: function () {
		if (!this._attached) {
			return;
		}
		if (this._container && this._container.parentNode) {
			this._container.parentNode.removeChild(this._container);
		}
		this._attached = false;
	},

	_onAddSlide: function () {
		this._map.insertPage();
	},

	_initLayout: function () {
		this._container = window.L.DomUtil.create('div', 'mobile-slide-strip');
		this._button = window.L.DomUtil.create('a', 'mobile-slide-strip-btn', this._container);
		this._button.href = '#';
		this._button.title = '';
		// 设计稿 + 图标 32px ÷ 2 = 16px,垂直水平居中
		this._button.innerHTML = '<svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">'
			+ '<path d="M10 3.5V16.5M3.5 10H16.5" stroke="#333333" stroke-width="2.5" stroke-linecap="round"/>'
			+ '</svg>';

		window.L.DomEvent
		    .on(this._button, 'click', window.L.DomEvent.stopPropagation)
		    .on(this._button, 'mousedown', window.L.DomEvent.stopPropagation)
		    .on(this._button, 'click', window.L.DomEvent.preventDefault)
		    .on(this._button, 'click', this._map.focus, this._map)
		    .on(this._button, 'click', this._onAddSlide, this);

		return this._container;
	},
});

window.L.control.mobileSlide = function (options) {
	return new window.L.Control.MobileSlide(options);
};
