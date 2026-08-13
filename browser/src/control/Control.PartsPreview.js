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
 * window.L.Control.PartsPreview
 */

/* global _ app $ Hammer _UNO cool */
window.L.Control.PartsPreview = window.L.Control.extend({
	options: {
		fetchThumbnail: true,
		autoUpdate: true,
		imageClass: '',
		frameClass: '',
		axis: '',
		allowOrientation: true,
		maxWidth: window.mode.isDesktop() ? 180: (window.mode.isTablet() ? 120: (window.ThisIsTheAndroidApp ? 98: 60)),
		maxHeight: window.mode.isDesktop() ? 180: (window.mode.isTablet() ? 120: (window.ThisIsTheAndroidApp ? 55: 60))
	},
	partsFocused: false,

	initialize: function (container, preview, options) {
		window.L.setOptions(this, options);

		if (!container) {
			container = window.L.DomUtil.get('presentation-controls-wrapper');
		}

		if (!preview) {
			preview = window.L.DomUtil.get('slide-sorter');
		}

		this._container = container;
		this._partsPreviewCont = preview;
		this._partsPreviewCont.onscroll = this._onScroll.bind(this);
		this._idNum = 0;
		this._width = 0;
		this._height = 0;
		this.scrollTimer = null;

		document.body.addEventListener('click', (e) => {
			if (!e.partsFocusedApplied && this.partsFocused)
				this.partsFocused = false;
		});
	},

	onAdd: function (map) {
		this._previewInitialized = false;
		this._previewTiles = [];
		this._direction = this.options.allowOrientation ?
			(!window.mode.isDesktop() && window.L.DomUtil.isPortrait() ? 'x' : 'y') :
			this.options.axis;

		map.on('updateparts', this._updateDisabled, this);
		map.on('updatepart', this._updatePart, this);
		map.on('invalidateparts', this._invalidateParts, this);
		map.on('tilepreview', this._updatePreview, this);
		map.on('insertpage', this._insertPreview, this);
		map.on('deletepage', this._deletePreview, this);
		map.on('scrolllimit', this._invalidateCurrentPart, this);
		map.on('scrolllimits', this._invalidateParts, this);
		map.on('scrolltopart', this._scrollToPart, this);
		map.on('beforerequestpreview', this._beforeRequestPreview, this);

		window.addEventListener('resize', window.L.bind(this._resize, this));

		if (window.ThisIsTheAndroidApp) {
			this._bindAndroidSlideSorterGestures();
		}
	},

	_bindAndroidSlideSorterGestures: function () {
		if (this._slideSorterGesturesBound)
			return;
		var root = this._container || this._partsPreviewCont;
		if (!root)
			return;
		this._slideSorterGesturesBound = true;
		var notify = function (on) {
			if (typeof window.postMobileMessage === 'function')
				window.postMobileMessage('SLIDE_SORTER_GESTURE ' + (on ? 'on' : 'off'));
		};
		var end = function () { notify(false); };
		root.addEventListener('touchstart', function () { notify(true); }, {passive: true, capture: true});
		root.addEventListener('touchend', end, {passive: true, capture: true});
		root.addEventListener('touchcancel', end, {passive: true, capture: true});
	},

	createScrollbar: function () {
		this._partsPreviewCont.style.whiteSpace = 'nowrap';
	},

	_updateDisabled: function () {
		const selectedPart = app.map._docLayer._selectedPart;

		const docType = app.map._docLayer._docType;

		if (docType === 'presentation' || docType === 'drawing') {
			if (!this._previewInitialized)
			{
				// make room for the preview
				var docContainer = this._map.options.documentContainer;

				if (!window.L.DomUtil.hasClass(docContainer, 'parts-preview-document'))
					window.L.DomUtil.addClass(docContainer, 'parts-preview-document');

				// Add a special frame just as a drop-site for reordering.
				var frameClass = 'preview-frame ' + this.options.frameClass;
				var frame = window.L.DomUtil.create('div', frameClass, this._partsPreviewCont);
				this._addDnDHandlers(frame);
				frame.setAttribute('draggable', false);
				frame.setAttribute('id', 'first-drop-site');

				if (window.mode.isDesktop()) {
					window.L.DomUtil.setStyle(frame, 'height', '20px');
					window.L.DomUtil.setStyle(frame, 'margin', '0em');
				}

				// Create the preview parts
				for (var i = 0; i < app.impress.partList.length; i++) {
					this._previewTiles.push(this._createPreview(i, app.impress.partList[i].hash));
				}
				if (!app.file.fileBasedView)
					window.L.DomUtil.addClass(this._previewTiles[selectedPart], 'preview-img-currentpart');
				this._onScroll(); // Load previews.
				this._updatePageBadges();
				this._previewInitialized = true;
			}
			else
			{
				this._syncPreviews();

				if (!app.file.fileBasedView) {
					// change the border style of the selected preview.
					for (let j = 0; j < app.impress.partList.length; j++) {
						window.L.DomUtil.removeClass(this._previewTiles[j], 'preview-img-currentpart');
						window.L.DomUtil.removeClass(this._previewTiles[j], 'preview-img-selectedpart');
						if (j === selectedPart)
							window.L.DomUtil.addClass(this._previewTiles[j], 'preview-img-currentpart');
						else if (app.impress.partList[j].selected)
							window.L.DomUtil.addClass(this._previewTiles[j], 'preview-img-selectedpart');
					}
				}
			}

			if (!this.options.allowOrientation) {
				return;
			}

			// update portrait / landscape
			var removePreviewImg = 'preview-img-portrait';
			var addPreviewImg = 'preview-img-landscape';
			var removePreviewFrame = 'preview-frame-portrait';
			var addPreviewFrame = 'preview-frame-landscape';
			if (window.L.DomUtil.isPortrait()) {
				removePreviewImg = 'preview-img-landscape';
				addPreviewImg = 'preview-img-portrait';
				removePreviewFrame = 'preview-frame-landscape';
				addPreviewFrame = 'preview-frame-portrait';
			}

			for (i = 0; i < app.impress.partList.length; i++) {
				window.L.DomUtil.removeClass(this._previewTiles[i], removePreviewImg);
				window.L.DomUtil.addClass(this._previewTiles[i], addPreviewImg);
				if (app.impress.isSlideHidden(i))
					window.L.DomUtil.addClass(this._previewTiles[i], 'hidden-slide');
				else
					window.L.DomUtil.removeClass(this._previewTiles[i], 'hidden-slide');
			}

			var previewFrame = $(this._partsPreviewCont).find('.preview-frame');
			previewFrame.removeClass(removePreviewFrame);
			previewFrame.addClass(addPreviewFrame);

			// re-create scrollbar with new direction
			this._direction = !window.mode.isDesktop() && !window.mode.isTablet() && window.L.DomUtil.isPortrait() ? 'x' : 'y';
		}
	},

	isPaddingClick: function (element, e, part) {
		var style = window.getComputedStyle(element, null);
		var nTop = parseInt(style.getPropertyValue('padding-top'));
		var nRight = parseFloat(style.getPropertyValue('padding-right'));
		var nLeft = parseFloat(style.getPropertyValue('padding-left'));
		var nBottom = parseFloat(style.getPropertyValue('padding-bottom'));
		var width = element.offsetWidth;
		var height = element.offsetHeight;
		var x = parseFloat(e.offsetX);
		var y = parseFloat(e.offsetY);

		if (part === 'top')         // Clicked on top padding?
			return !(y > nTop);
		else if (part === 'bottom') // Clicked on bottom padding?
			return !(y < height - nBottom);
		else                        // Clicked on any padding?
			return !((x > nLeft && x < width - nRight) && (y > nTop && y < height - nBottom));
	},

	_createPreview: function (i, hashCode) {
		var frameClass = 'preview-frame ' + this.options.frameClass;
		var frame = window.L.DomUtil.create('div', frameClass, this._partsPreviewCont);
		frame.id = 'preview-frame-part-' + this._idNum;
		this._addDnDHandlers(frame);
		window.L.DomUtil.create('span', 'preview-helper', frame);

		var imgClassName = 'preview-img ' + this.options.imageClass;
		var img = window.L.DomUtil.create('img', imgClassName, frame);
		img.setAttribute('alt', _('preview of page %1').replace('%1', String(i + 1)));
		img.setAttribute('tabindex', '0');
		img.setAttribute('data-cooltip', _('Slide %1').replace('%1', String(i + 1)));
		window.L.control.attachTooltipEventListener(img, this._map);
		img.id = 'preview-img-part-' + this._idNum;
		img.hash = hashCode;
		img.src = document.querySelector('meta[name="previewSmile"]').content;
		img.fetched = false;
		img.setAttribute('draggable', 'false');
		window.L.DomEvent.on(img, 'contextmenu', window.L.DomEvent.preventDefault);
		// Android 缩略图右下角页码角标(Figma:未选中黑40% / 选中橙底,白字)
		if (window.ThisIsTheAndroidApp) {
			window.L.DomUtil.create('span', 'preview-page-badge', frame).textContent = String(i + 1);
		}
		if (!window.mode.isDesktop()) {
			if (window.ThisIsTheAndroidApp) {
				this._bindAndroidLongPressDnD(img);
			} else {
				// touchAction 必须允许 pan,否则 Hammer 默认 touch-action:none 会阻止列表滚动
				(new Hammer(img, {
					touchAction: 'pan-x pan-y',
					recognizers: [[Hammer.Press, {time: 450}]]
				}))
					.on('press', function (e) {
						if (this._map.isEditMode()) {
							this._addDnDTouchHandlers(e);
						}
					}.bind(this));
			}
		}
		window.L.DomEvent.on(img, 'click', function (e) {
			if (this._touchDnDJustEnded) {
				this._touchDnDJustEnded = false;
				return;
			}
			if (this._suppressSlideClick) {
				this._suppressSlideClick = false;
				return;
			}
			window.L.DomEvent.stopPropagation(e);
			window.L.DomEvent.stop(e);
			var part = this._findClickedPart(e.target.parentNode);
			if (part !== null)
				var partId = parseInt(part) - 1; // The first part is just a drop-site for reordering.
			if (!window.mode.isDesktop() && partId === this._map._docLayer._selectedPart && !app.file.fileBasedView) {
				if (this._map._permission === 'edit') {
					if (window.ThisIsTheAndroidApp) {
						this._showAndroidSlidePopup(img);
						return;
					}
					// if mobile or tab then second tap will open the mobile wizard
					app.socket.sendMessage('resetselection');
					setTimeout(function () {
						app.dispatcher.dispatch('mobile_wizard');
					}, 0);
				}
			} else {
				this._setPart(e);
				if (!window.mode.isDesktop()) {
					// needed so on-screen keyboard doesn't pop up when switching slides,
					// but would cause PgUp/Down to not work on desktop in slide sorter
					document.activeElement.blur();
				}
			}
			if (app.file.fileBasedView)
				this._map._docLayer._checkSelectedPart();
			img.focus();
		}, this);

		var that = this;
		img.onfocus = function (e) {
			that._map._clip.clearSelection();
			that._map._clip.setTextSelectionType('slide');
			that.partsFocused = true;
			e.partsFocusedApplied = true;
		};

		var that = this;
		window.L.DomEvent.on(frame, 'contextmenu', function(e) {
			var isMasterView = this._map['stateChangeHandler'].getItemValue('.uno:SlideMasterPage');
			var pcw = document.getElementById('presentation-controls-wrapper');
			var $trigger = $(pcw);
			if (isMasterView === 'true' || app.isReadOnly()) {
				$trigger.contextMenu(false);
				return;
			}

			var nPos = undefined;
			if (this.isPaddingClick(frame, e, 'top'))
				nPos = that._findClickedPart(frame) - 1;
			else if (this.isPaddingClick(frame, e, 'bottom'))
				nPos = that._findClickedPart(frame);
			else if (this.isPaddingClick(frame, e, 'right') || this.isPaddingClick(frame, e, 'left'))
				nPos = that._findClickedPart(frame);

			$trigger.contextMenu(true);
			if (!that._isSelected(e))
				that._setPart(e);
			$.contextMenu({
				selector: '#'+frame.id,
				className: 'cool-font',
				items: {
					paste: {
						name: app.IconUtil.createMenuItemLink(_('Paste Slide'), 'Paste'),
						isHtmlName: true,
						callback: function(key, options) {
								if (!nPos)
									nPos = that._findClickedPart(options.$trigger[0]);
								that._setPart(that.copiedSlide);
								that._map.duplicatePage(nPos);
						},
						visible: function() {
							return that.copiedSlide;
						}
					},
					newslide: {
						name: app.IconUtil.createMenuItemLink( _UNO(that._map._docLayer._docType == 'presentation' ? '.uno:InsertSlide' : '.uno:InsertPage', 'presentation'), 'InsertPage'),
						isHtmlName: true,
						callback: function() { that._map.insertPage(nPos); }
					}
				},
				events: {
					hide: function() {
						img.focus();
					}
				}
			});
		}, this);

		window.L.DomEvent.on(img, 'contextmenu', function(e) {
			var isMasterView = this._map['stateChangeHandler'].getItemValue('.uno:SlideMasterPage');
			var $trigger = $('#' + img.id);
			if (isMasterView === 'true' || app.isReadOnly()) {
				$trigger.contextMenu(false);
				return;
			}
			$trigger.contextMenu(true);
			if (!that._isSelected(e))
				that._setPart(e);

			$.contextMenu({
				selector: '#' + img.id,
				className: 'cool-font',
				items: {
					copy: {
						name: app.IconUtil.createMenuItemLink(_('Copy'), 'Copy'),
						isHtmlName: true,
						callback: function() {
							that.copiedSlide = e;
							that._map._clip.clearSelection();
							that._map._clip.setTextSelectionType('slide');
							that._map._clip._execCopyCutPaste('copy', '.uno:CopySlide');
						},
						visible: function() {
							return !(app.impress.hasOverviewPage && that._map._docLayer._selectedPart === 0);
						}
					},
					paste: {
						name: app.IconUtil.createMenuItemLink(_('Paste'), 'Paste'),
						isHtmlName: true,
						callback: function() {
							that._map._clip._execCopyCutPaste('paste', ".uno:Paste")
						},
					},
					newslide: {
						name: app.IconUtil.createMenuItemLink(_UNO(that._map._docLayer._docType == 'presentation' ? '.uno:InsertSlide' : '.uno:InsertPage', 'presentation'), 'InsertPage'),
						isHtmlName: true,
						callback: function() { that._map.insertPage(); }
					},
					duplicateslide: {
						name: app.IconUtil.createMenuItemLink(_UNO(that._map._docLayer._docType == 'presentation' ? '.uno:DuplicateSlide' : '.uno:DuplicatePage', 'presentation'), 'DuplicatePage'),
						isHtmlName: true,
						callback: function() { that._map.duplicatePage(); }
					},
					delete: {
						name: app.IconUtil.createMenuItemLink(_UNO(that._map._docLayer._docType == 'presentation' ? '.uno:DeleteSlide' : '.uno:DeletePage', 'presentation'), 'DeletePage'),
						isHtmlName: true,
						callback: function() { app.dispatcher.dispatch('deletepage'); },
						visible: function() {
							return that._map._docLayer._parts > 1;
						}
					},
					slideproperties: {
						name: app.IconUtil.createMenuItemLink(_UNO(that._map._docLayer._docType == 'presentation' ? '.uno:SlideSetup' : '.uno:PageSetup', 'presentation'), 'PageSetup'),
						isHtmlName: true,
						callback: function() {
							app.socket.sendMessage('uno .uno:PageSetup');
						}
					},
					showslide: {
						name: app.IconUtil.createMenuItemLink(_UNO('.uno:ShowSlide', 'presentation'), 'ShowSlide'),
						isHtmlName: true,
						callback: function(key, options) {
							var part = that._findClickedPart(options.$trigger[0].parentNode);
							if (part !== null) {
								that._map.showSlide();
							}
						},
						visible: function(key, options) {
							var part = that._findClickedPart(options.$trigger[0].parentNode);
							return that._map._docLayer._docType === 'presentation' && app.impress.isSlideHidden(parseInt(part) - 1);
						}
					},
					hideslide: {
						name: app.IconUtil.createMenuItemLink(_UNO('.uno:HideSlide', 'presentation'), 'Hideslide'),
						isHtmlName: true,
						callback: function(key, options) {
							var part = that._findClickedPart(options.$trigger[0].parentNode);
							if (part !== null) {
								that._map.hideSlide();
							}
						},
						visible: function(key, options) {
							var part = that._findClickedPart(options.$trigger[0].parentNode);
							return that._map._docLayer._docType === 'presentation' && !app.impress.isSlideHidden(parseInt(part) - 1);
						}
					}
				},
				events: {
					hide: function() {
						// Restore focus to the element that opened the menu
						img.focus();
					}
				}
			});
		}, this);

		var imgSize = this._map.getPreview(i, i,
						   this.options.maxWidth,
						   this.options.maxHeight,
						   {autoUpdate: this.options.autoUpdate,
						    fetchThumbnail: false});

		window.L.DomUtil.setStyle(img, 'width', imgSize.width + 'px');
		window.L.DomUtil.setStyle(img, 'height', imgSize.height + 'px');

		this._idNum++;

		return img;
	},

	_scrollToPart: function() {
		var partNo = this._map.getCurrentPartNumber();
		//var sliderSize, nodePos, nodeOffset, nodeMargin;
		var node = this._partsPreviewCont.children[partNo];

		if (node && (!this._previewTiles[partNo] || !this._isPreviewVisible(partNo))) {
			if (this.scrollTimer) clearTimeout(this.scrollTimer);

			 this.scrollTimer = setTimeout(() => {
				node.scrollIntoView();
				this.scrollTimer = null;
			}, 50);
		}
	},

	// We will use this function because IE doesn't support "Array.from" feature.
	_findClickedPart: function (element) {
		for (var i = 0; i < this._partsPreviewCont.children.length; i++) {
			if (this._partsPreviewCont.children[i] === element || this._partsPreviewCont.children[i] === element.parentNode) {
				return i;
			}
		}
		return -1;
	},

	// This is used with fileBasedView.
	_scrollViewToPartPosition: function (partNumber, fromBottom) {
		if (this._map._docLayer && this._map._docLayer._isZooming)
			return;

		if (partNumber < 0) partNumber = 0;
		if (partNumber >= this._map._docLayer._parts) partNumber = this._map._docLayer._parts - 1;

		var partHeightPixels = Math.round((this._map._docLayer._partHeightTwips + this._map._docLayer._spaceBetweenParts) * app.twipsToPixels);
		var scrollTop = partHeightPixels * partNumber;
		var viewHeight = app.sectionContainer.getViewSize()[1];
		var currentScrollX = app.activeDocument.activeLayout.viewedRectangle.pX1;

		if (viewHeight > partHeightPixels && partNumber > 0)
			scrollTop -= Math.round((viewHeight - partHeightPixels) * 0.5);

		// scroll to the bottom of the selected part/page instead of its top px
		if (fromBottom)
			scrollTop += partHeightPixels - viewHeight;

		app.activeDocument.activeLayout.scrollTo(currentScrollX, scrollTop);
	},

	_scrollViewByDirection: function(buttonType) {
		if (this._map._docLayer && this._map._docLayer._isZooming)
			return;
		var viewHeight = Math.floor(app.sectionContainer.getViewSize()[1]);
		var viewHeightScaled = Math.round(Math.floor(viewHeight) / app.dpiScale);
		var scrollBySize = Math.floor(viewHeightScaled * 0.75);
		var currentScrollX = app.activeDocument.activeLayout.viewedRectangle.cX1;

		app.sectionContainer.getSectionWithName(app.CSections.Scroll.name).onScrollBy({x: currentScrollX, y: buttonType === 'prev' ? -scrollBySize : scrollBySize});
	},

	_isSelected: function (e) {
		var part = this._findClickedPart(e.target.parentNode);
		var partId = parseInt(part) - 1; // The first part is just a drop-site for reordering.
		if (partId < 0)
			return false;
		else
			return app.impress.isSlideSelected(partId);
	},

	_setPart: function (e) {
		const editingComment = cool.Comment.isAnyEdit();
		if (editingComment) {
			const commentSection = app.sectionContainer.getSectionWithName(app.CSections.CommentList.name);
			if (commentSection) {
				commentSection.navigateAndFocusComment(editingComment);
			}
			return;
		}

		var part = this._findClickedPart(e.target.parentNode);
		if (part !== -1) {
			var partId = parseInt(part) - 1; // The first part is just a drop-site for reordering.

			if (app.file.fileBasedView) {
				this._map.setPart(partId);
				this._scrollViewToPartPosition(partId);
				return;
			}

			if (e.ctrlKey) {
				this._map.selectPart(partId, 2, false); // Toggle selection on ctrl+click.
				if (this.firstSelection === undefined)
					this.firstSelection = this._map._docLayer._selectedPart;
			} else if (e.altKey) {
				window.app.console.log('alt');
			} else if (e.shiftKey) {
				if (this.firstSelection === undefined)
					this.firstSelection = this._map._docLayer._selectedPart;

				//deselect all slides
				this._map.deselectAll();

				//reselect the first original selection
				this._map.setPart(this.firstSelection);
				this._map.selectPart(this.firstSelection, 1, false);

				if (this.firstSelection < partId) {
					for (var id = this.firstSelection + 1; id <= partId; ++id) {
						this._map.selectPart(id, 2, false);
					}
				} else if (this.firstSelection > partId) {
					for (id = this.firstSelection - 1; id >= partId; --id) {
						this._map.selectPart(id, 2, false);
					}
				}
			} else {
				this._map.deselectAll();
				this._map.setPart(partId);
				this._map.selectPart(partId, 1, false); // And select.
				this.firstSelection = partId;
			}
		}
	},

	_updatePart: function (e) {
		if ((e.docType === 'presentation' || e.docType === 'drawing') && e.part >= 0) {
			this._map.getPreview(e.part, e.part, this.options.maxWidth, this.options.maxHeight, {autoUpdate: this.options.autoUpdate});
		}
	},

	_syncPreviews: function () {
		var it = 0;

		if (app.impress.partList.length !== this._previewTiles.length) {
			if (Math.abs(app.impress.partList.length - this._previewTiles.length) === 1) {
				if (app.impress.partList.length > this._previewTiles.length) {
					for (it = 0; it < app.impress.partList.length; it++) {
						if (it === this._previewTiles.length) {
							this._insertPreview({selectedPart: it - 1, hashCode: app.impress.partList[it].hash});
							break;
						}
						if (this._previewTiles[it].hash !== app.impress.partList[it].hash) {
							this._insertPreview({selectedPart: it, hashCode: app.impress.partList[it].hash});
							break;
						}
					}
				}
				else {
					for (it = 0; it < this._previewTiles.length; it++) {
						if (it === app.impress.partList.length ||
						    this._previewTiles[it].hash !== app.impress.partList[it].hash) {
							this._deletePreview({selectedPart: it});
							break;
						}
					}
				}
			}
			else {
				// sync all, should never happen
				while (this._previewTiles.length < app.impress.partList.length) {
					this._insertPreview({selectedPart: this._previewTiles.length - 1,
							     hashCode: app.impress.partList[this._previewTiles.length].hash});
				}

				while (this._previewTiles.length > app.impress.partList.length) {
					this._deletePreview({selectedPart: this._previewTiles.length - 1});
				}

				for (it = 0; it < app.impress.partList.length; it++) {
					this._previewTiles[it].hash = app.impress.partList[it].hash;
					this._previewTiles[it].src = document.querySelector('meta[name="previewSmile"]').content;
					this._previewTiles[it].fetched = false;
				}
			}
		}
		else {
			// update hash code when user click insert slide.
			for (it = 0; it < app.impress.partList.length; it++) {
				if (this._previewTiles[it].hash !== app.impress.partList[it].hash) {
					this._previewTiles[it].hash = app.impress.partList[it].hash;
					this._map.getPreview(it, it, this.options.maxWidth, this.options.maxHeight, {autoUpdate: this.options.autoUpdate});
				}
			}
		}
		this._updatePageBadges();
	},

	_updatePageBadges: function () {
		if (!window.ThisIsTheAndroidApp || !this._previewTiles)
			return;
		for (var i = 0; i < this._previewTiles.length; i++) {
			var frame = this._previewTiles[i].parentNode;
			if (!frame)
				continue;
			var badge = frame.querySelector('.preview-page-badge');
			if (badge)
				badge.textContent = String(i + 1);
		}
	},

	_rebuildPreviewTilesFromDom: function () {
		var tiles = [];
		for (var i = 0; i < this._partsPreviewCont.children.length; i++) {
			var child = this._partsPreviewCont.children[i];
			if (child.id === 'first-drop-site')
				continue;
			var img = child.querySelector('.preview-img');
			if (img)
				tiles.push(img);
		}
		this._previewTiles = tiles;
	},

	_movePreviewInDom: function (sourceImg, targetFrame) {
		var sourceFrame = sourceImg.parentNode;
		if (!sourceFrame || !targetFrame || sourceFrame === targetFrame)
			return false;

		var sourceIdx = this._findClickedPart(sourceFrame);
		var targetIdx = this._findClickedPart(targetFrame);
		if (sourceIdx < 1 || targetIdx < 0 || sourceIdx === targetIdx)
			return false;

		if (sourceIdx < targetIdx)
			targetFrame.parentNode.insertBefore(sourceFrame, targetFrame.nextSibling);
		else
			targetFrame.parentNode.insertBefore(sourceFrame, targetFrame);

		this._rebuildPreviewTilesFromDom();
		this._updatePageBadges();
		return true;
	},

	_resize: function () {
		if (this._height == window.innerHeight &&
		    this._width == window.innerWidth)
			return;

		if (this._previewInitialized) {
			clearTimeout(this._resizeTimer);
			this._resizeTimer = setTimeout(window.L.bind(this._onScroll, this), 50);
		}

		this._height = window.innerHeight;
		this._width = window.innerWidth;
	},

	_beforeRequestPreview: function (e) {
		if (e.part !== undefined && e.part >= 0 && e.part < this._previewTiles.length &&
		   this._previewTiles[e.part].src === document.querySelector('meta[name="previewSmile"]').content)
			this._previewTiles[e.part].src = document.querySelector('meta[name="previewImg"]').content;
	},

	_updatePreview: function (e) {
		if (this._map.isPresentationOrDrawing()) {
			this._map._previewRequestsOnFly--;
			if (this._map._previewRequestsOnFly < 0) {
				this._map._previewRequestsOnFly = 0;
				this._map._timeToEmptyQueue = new Date();
			}
			this._map._processPreviewQueue();
			if (!this._previewInitialized)
				return;
			if (this._previewTiles[e.id]) {
				this._previewTiles[e.id].src = e.tile.src;
				this._previewTiles[e.id].fetched = true;
				window.app.console.debug('PREVIEW: part fetched : ' + e.id);
			}
		}
	},

	_insertPreview: function (e) {
		if (this._map.isPresentationOrDrawing()) {
			var newIndex = e.selectedPart + 1;
			var newPreview = this._createPreview(newIndex, (e.hashCode === undefined ? null : e.hashCode));

			// insert newPreview to newIndex position
			this._previewTiles.splice(newIndex, 0, newPreview);

			var selectedFrame = this._previewTiles[e.selectedPart].parentNode;
			var newFrame = newPreview.parentNode;

			// insert after selectedFrame
			selectedFrame.parentNode.insertBefore(newFrame, selectedFrame.nextSibling);

			this._ensureVisiblePreviews();
			this._scrollToPart();
			this._updatePageBadges();
		}
	},

	_deletePreview: function (e) {
		if (this._map.isPresentationOrDrawing()) {
			var selectedFrame = this._previewTiles[e.selectedPart].parentNode;
			window.L.DomUtil.remove(selectedFrame);

			this._previewTiles.splice(e.selectedPart, 1);
			this.focusCurrentSlide();
			this._updatePageBadges();
		}
	},

	_onScroll: function () {
		setTimeout(window.L.bind(function () {
			for (var i = 0; i < this._previewTiles.length; ++i) {
				if (this._isPreviewVisible(i)) {
					var img = this._previewTiles[i];
					if (img && !img.fetched) {
						this._map.getPreview(i, i, this.options.maxWidth, this.options.maxHeight, {autoUpdate: this.options.autoUpdate});
					}
				}
			}
		}, this), 0);
	},

	_isPreviewVisible: function(part) {
		var el = this._previewTiles[part];
		if (!el)
			return false;

		var elemRect = el.getBoundingClientRect();
		var viewRect = new DOMRect(0, 0, window.innerWidth, window.innerHeight);

		return (elemRect.left <= viewRect.right &&
			viewRect.left <= elemRect.right &&
			elemRect.top <= viewRect.bottom &&
			viewRect.top <= elemRect.bottom)
	},

	_addDnDHandlers: function (elem) {
		if (app.file.fileBasedView) // No drag & drop for pdf files and the like.
			return;

		// Android WebView 上 HTML5 drag 会抢占 touch,导致无法滚动/拖动
		if (window.ThisIsTheAndroidApp)
			return;

		if (elem) {
			elem.setAttribute('draggable', true);
			elem.addEventListener('dragstart', this._handleDragStart, false);
			elem.addEventListener('dragenter', this._handleDragEnter, false);
			elem.addEventListener('dragover', this._handleDragOver, false);
			elem.addEventListener('dragleave', this._handleDragLeave, false);
			elem.addEventListener('drop', this._handleDrop, false);
			elem.addEventListener('dragend', this._handleDragEnd, false);
			elem.partsPreview = this;
		}
	},

	_findDropFrame: function (node) {
		while (node && node !== this._partsPreviewCont) {
			if (node.classList && node.classList.contains('preview-frame'))
				return node;
			node = node.parentNode;
		}
		return null;
	},

	_showAndroidSlidePopup: function (img) {
		if (!window.ThisIsTheAndroidApp || typeof window.postMobileMessage !== 'function')
			return;
		if (app.isReadOnly())
			return;
		var isMasterView = this._map['stateChangeHandler'].getItemValue('.uno:SlideMasterPage');
		if (isMasterView === 'true')
			return;

		var part = this._findClickedPart(img.parentNode);
		if (part === null || part < 1)
			return;
		var partIndex = parseInt(part) - 1;
		var rect = img.getBoundingClientRect();
		var canDelete = this._map._docLayer._parts > 1;
		var hasOverview = app.impress.hasOverviewPage && partIndex === 0;

		window.postMobileMessage('SLIDE_THUMBNAIL_POPUP show ' + JSON.stringify({
			partIndex: partIndex,
			slideNumber: partIndex + 1,
			canCopy: !hasOverview,
			canDelete: canDelete && !hasOverview,
			anchorX: rect ? rect.left + rect.width * 0.5 : 0,
			anchorY: rect ? rect.top : 0,
			anchorBottom: rect ? rect.bottom : 0
		}));
	},

	_bindAndroidLongPressDnD: function (img) {
		var that = this;
		var pressTimer = null;
		var dragStarted = false;
		var tracking = false;
		var startX = 0;
		var startY = 0;
		var lastX = 0;
		var lastY = 0;
		var activeImg = null;

		var clearPress = function () {
			if (pressTimer) {
				clearTimeout(pressTimer);
				pressTimer = null;
			}
		};

		var resetPress = function () {
			clearPress();
			dragStarted = false;
		};

		var stopTracking = function () {
			document.removeEventListener('touchmove', onDocumentTouchMove, true);
			document.removeEventListener('touchend', onDocumentTouchEnd, true);
			document.removeEventListener('touchcancel', onDocumentTouchCancel, true);
			tracking = false;
			activeImg = null;
		};

		var beginDrag = function () {
			if (dragStarted || that._touchDnDActive || !activeImg)
				return;
			var dragTarget = activeImg;
			dragStarted = true;
			stopTracking();
			that._addDnDTouchHandlers({
				target: dragTarget,
				center: { x: lastX, y: lastY }
			});
		};

		var onDocumentTouchMove = function (e) {
			if (!tracking || that._touchDnDActive)
				return;
			var touch = e.touches[0];
			if (!touch)
				return;
			lastX = touch.clientX;
			lastY = touch.clientY;
			var dx = Math.abs(touch.clientX - startX);
			var dy = Math.abs(touch.clientY - startY);
			if (pressTimer && (dx > 8 || dy > 8)) {
				clearPress();
				return;
			}
			if (dragStarted) {
				if (e.cancelable)
					e.preventDefault();
				that._handleTouchMove(e);
			}
		};

		var onDocumentTouchEnd = function () {
			if (!tracking)
				return;
			resetPress();
			stopTracking();
		};

		var onDocumentTouchCancel = function () {
			resetPress();
			stopTracking();
		};

		window.L.DomEvent.on(img, 'touchstart', function (e) {
			if (!that._map.isEditMode() || that._touchDnDActive)
				return;
			var touch = e.touches[0];
			if (!touch)
				return;
			stopTracking();
			resetPress();
			activeImg = img;
			tracking = true;
			startX = touch.clientX;
			startY = touch.clientY;
			lastX = startX;
			lastY = startY;
			document.addEventListener('touchmove', onDocumentTouchMove, {passive: false, capture: true});
			document.addEventListener('touchend', onDocumentTouchEnd, true);
			document.addEventListener('touchcancel', onDocumentTouchCancel, true);
			pressTimer = setTimeout(function () {
				pressTimer = null;
				beginDrag();
			}, 400);
		}, this);
	},

	_cleanupTouchDnD: function () {
		if (this._boundTouchMove) {
			document.removeEventListener('touchmove', this._boundTouchMove);
			document.removeEventListener('touchcancel', this._boundTouchCancel);
			document.removeEventListener('touchend', this._boundTouchEnd);
		}
		$('.preview-frame').removeClass('preview-img-dropsite');
		window.L.DomUtil.removeClass(document.body, 'slide-dnd-active');
		if (this.draggedSlide) {
			$(this.draggedSlide).remove();
			this.draggedSlide = null;
		}
		this.currentNode = null;
		this.previousNode = null;
		this._touchDnDActive = false;
		this._touchDnDJustEnded = true;
		this._touchDnDImg = null;
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function')
			window.postMobileMessage('SLIDE_SORTER_GESTURE off');
	},

	_addDnDTouchHandlers: function (e) {
		if (this._touchDnDActive)
			return;
		this._touchDnDActive = true;
		this._touchDnDImg = e.target;
		this._touchDnDWidth = e.target.width || e.target.offsetWidth;

		this._boundTouchMove = this._handleTouchMove.bind(this);
		this._boundTouchCancel = this._handleTouchCancel.bind(this);
		this._boundTouchEnd = this._handleTouchEnd.bind(this);
		document.addEventListener('touchmove', this._boundTouchMove, {passive: false});
		document.addEventListener('touchcancel', this._boundTouchCancel, {passive: false});
		document.addEventListener('touchend', this._boundTouchEnd, {passive: false});

		// To avoid having to add a new message to move an arbitrary part, let's select the
		// slide that is being dragged.
		var part = this._findClickedPart(e.target.parentNode);
		if (part !== null) {
			var partId = parseInt(part) - 1; // The first part is just a drop-site for reordering.
			this._map.setPart(partId);
			this._map.selectPart(partId, 1, false); // And select.
		}
		this.draggedSlide = window.L.DomUtil.create('img', '', document.body);
		this.draggedSlide.setAttribute('src', e.target.currentSrc);
		$(this.draggedSlide).css('position', 'absolute');
		$(this.draggedSlide).css('height', e.target.height || e.target.offsetHeight);
		$(this.draggedSlide).css('width', this._touchDnDWidth);
		$(this.draggedSlide).css('left', e.center.x - (this._touchDnDWidth / 2));
		$(this.draggedSlide).css('top', e.center.y - (e.target.height || e.target.offsetHeight));
		$(this.draggedSlide).css('z-index', '10001');
		$(this.draggedSlide).css('opacity', '75%');
		$(this.draggedSlide).css('pointer-events', 'none');
		window.L.DomUtil.addClass(document.body, 'slide-dnd-active');
		if (window.ThisIsTheAndroidApp && typeof window.postMobileMessage === 'function')
			window.postMobileMessage('SLIDE_SORTER_GESTURE on');

		this.currentNode = null;
		this.previousNode = null;
	},

	_handleTouchMove: function (e) {
		if (!this._touchDnDActive)
			return;

		if (e.preventDefault)
			e.preventDefault();

		var touch = e.touches[0];
		if (!touch)
			return false;

		this.currentNode = this._findDropFrame(document.elementFromPoint(touch.clientX, touch.clientY));

		if (this.currentNode !== this.previousNode && this.previousNode !== null) {
			$('.preview-frame').removeClass('preview-img-dropsite');
		}

		if (this.currentNode &&
		    (this.currentNode.classList.contains('preview-frame') || this.currentNode.id === 'first-drop-site')) {
			this.currentNode.classList.add('preview-img-dropsite');
		}

		this.previousNode = this.currentNode;

		if (this.draggedSlide) {
			$(this.draggedSlide).css('left', touch.clientX - (this._touchDnDWidth / 2));
			$(this.draggedSlide).css('top', touch.clientY - (this._touchDnDImg ? this._touchDnDImg.offsetHeight : 0));
		}
		return false;
	},

	_handleTouchCancel: function() {
		this._cleanupTouchDnD();
	},

	_handleTouchEnd: function (e) {
		if (!this._touchDnDActive) {
			return false;
		}
		if (e.stopPropagation) {
			e.stopPropagation();
		}
		if (this.currentNode) {
			var part = this._findClickedPart(this.currentNode);
			if (part !== null) {
				var partId = parseInt(part) - 1; // First frame is a drop-site for reordering.
				if (partId < 0)
					partId = -1; // First item is -1.
				if (this._touchDnDImg)
					this._movePreviewInDom(this._touchDnDImg, this.currentNode);
				app.socket.sendMessage('moveselectedclientparts position=' + partId);
			}
		}
		this._cleanupTouchDnD();
		return false;
	},

	_handleDragStart: function (e) {
		// To avoid having to add a new message to move an arbitrary part, let's select the
		// slide that is being dragged.
		const targetNode = (e.target.id.startsWith('preview') ? e.target : e.target.parentNode);
		var part = this.partsPreview._findClickedPart(targetNode);
		if (part !== null) {
			var partId = parseInt(part) - 1; // The first part is just a drop-site for reordering.
			if (this.partsPreview._map._docLayer && !app.impress.isSlideSelected(partId))
			{
				this.partsPreview._map.setPart(partId);
				this.partsPreview._map.selectPart(partId, 1, false); // And select.
			}
		}
		// By default we move when dragging, but can
		// support duplication with ctrl in the future.
		e.dataTransfer.effectAllowed = 'move';
	},

	_handleDragOver: function (e) {
		if (e.preventDefault) {
			e.preventDefault();
		}

		// By default we move when dragging, but can
		// support duplication with ctrl in the future.
		e.dataTransfer.dropEffect = 'move';

		this.classList.add('preview-img-dropsite');
		return false;
	},

	_handleDragEnter: function () {
	},

	_handleDragLeave: function () {
		this.classList.remove('preview-img-dropsite');
	},

	_handleDrop: function (e) {
		if (e.stopPropagation) {
			e.stopPropagation();
		}

		// When dropping on a thumbnail we get an `img` tag as a target, so we need to get the
		// parent.
		// Otherwise dropping between slides doesn't work.
		// See https://github.com/CollaboraOnline/online/issues/6941
		var target = e.target.classList.contains('preview-img') ? e.target.parentNode : e.target;

		var part = this.partsPreview._findClickedPart(target);
		if (part !== null) {
			var partId = parseInt(part) - 1; // First frame is a drop-site for reordering.
			if (partId < 0)
				partId = -1; // First item is -1.
			app.socket.sendMessage('moveselectedclientparts position=' + partId);
		}

		this.classList.remove('preview-img-dropsite');
		return false;
	},

	_handleDragEnd: function () {
		this.classList.remove('preview-img-dropsite');
	},

	_invalidateParts: function () {
		if (!this._container ||
		    !this._partsPreviewCont ||
		    !this._previewInitialized ||
		    !this._previewTiles)
			return;

		for (var part = 0; part < this._previewTiles.length; part++) {
			this._previewTiles[part].fetched = false;
			this._map.getPreview(part, part,
					     this.options.maxWidth,
					     this.options.maxHeight,
					     {autoUpdate: this.options.autoUpdate,
					      fetchThumbnail: this.options.fetchThumbnail});
		}

	},

	_invalidateCurrentPart: function () {
		if (!this._container ||
		    !this._partsPreviewCont ||
		    !this._previewInitialized ||
		    !this._previewTiles)
			return;

		// When a new slide is inserted
		if (this._previewTiles[this._map._docLayer._selectedPart] === undefined) {
			this._invalidateParts();
			return;
		}
		this._previewTiles[this._map._docLayer._selectedPart].fetched = false;
		this._map.getPreview(this._map._docLayer._selectedPart, this._map._docLayer._selectedPart,
				     this.options.maxWidth,
				     this.options.maxHeight,
				     {autoUpdate: this.options.autoUpdate,
				      fetchThumbnail: this.options.fetchThumbnail});
	},

	focusCurrentSlide: function () {
		if (this._previewTiles[this._map._docLayer._selectedPart])
			this._previewTiles[this._map._docLayer._selectedPart].focus();
	},
});

window.L.control.partsPreview = function (container, preview, options) {
	return new window.L.Control.PartsPreview(container, preview, options);
};
