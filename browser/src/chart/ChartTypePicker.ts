/* -*- js-indent-level: 8 -*- */
/*
 * SPDX-License-Identifier: MPL-2.0
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * window.L.Control.ChartTypePicker - chart type selection panel for mobile
 */

/* global app */

/* Chart type definitions */
interface ChartTypeInfo {
  id: string;
  name: string;
  categoryName: string;
  unoChartType: string;
}

interface ChartCategoryInfo {
  name: string;
  chartTypes: ChartTypeInfo[];
}

const CHART_CATEGORIES_INFO: ChartCategoryInfo[] = [
  {
    name: '饼图',
    chartTypes: [
      { id: 'pie-basic', name: '基础饼图', categoryName: '饼图', unoChartType: 'pie' },
      { id: 'pie-rounded', name: '基础饼图(圆角)', categoryName: '饼图', unoChartType: 'pie-rounded' },
      { id: 'pie-exploded', name: '变形饼图', categoryName: '饼图', unoChartType: 'pie-exploded' },
    ],
  },
  {
    name: '线图',
    chartTypes: [
      { id: 'line-basic', name: '折线图', categoryName: '线图', unoChartType: 'line' },
      { id: 'line-curve', name: '曲线折线图', categoryName: '线图', unoChartType: 'line-curve' },
    ],
  },
  {
    name: '柱图',
    chartTypes: [
      { id: 'column-basic', name: '基础柱状图', categoryName: '柱图', unoChartType: 'column' },
      { id: 'bar-basic', name: '基础条形图', categoryName: '柱图', unoChartType: 'bar' },
      { id: 'column-stacked', name: '堆叠柱状图', categoryName: '柱图', unoChartType: 'column-stacked' },
    ],
  },
];

// Base path for SVG icons
const ICON_PATH = 'images/chart/';

window.L.Control.ChartTypePicker = window.L.Control.extend({

    options: {
        title: '图表'
    },

    _selectedChartType: null,
    _onSelectCallback: null,
    element: null,

    initialize: function(options: any, onSelectCallback: any) {
        window.L.setOptions(this, options);
        this._onSelectCallback = onSelectCallback;
    },

    onAdd: function(map: any) {
        this.map = map;

        if (!window.mode.isMobile())
            return;

        this._buildContent();
    },

    onRemove: function() {
        if (this.element) {
            window.L.DomUtil.remove(this.element);
            this.element = undefined;
        }
    },

    _buildContent: function() {
        // Main container
        this.element = window.L.DomUtil.create('div', 'chart-type-picker', document.getElementById('mobile-wizard-content'));
        this.element.id = 'chart-type-picker-content';

        CHART_CATEGORIES_INFO.forEach((category) => {
            // Category title
            var categoryTitle = window.L.DomUtil.create('div', 'chart-category-title', this.element);
            categoryTitle.textContent = category.name;

            // Chart type grid
            var gridContainer = window.L.DomUtil.create('div', 'chart-type-grid', this.element);

            category.chartTypes.forEach((chartType) => {
                var card = window.L.DomUtil.create('div', 'chart-type-card', gridContainer);
                card.setAttribute('data-chart-id', chartType.id);

                // Icon container
                var iconContainer = window.L.DomUtil.create('div', 'chart-icon-container', card);
                var img = window.L.DomUtil.create('img', 'chart-type-icon', iconContainer);
                img.src = ICON_PATH + chartType.id + '.svg';
                img.alt = chartType.name;

                // Label
                var label = window.L.DomUtil.create('div', 'chart-type-label', card);
                label.textContent = chartType.name;

                // Click handler
                card.addEventListener('click', () => {
                    this._selectedChartType = chartType;
                    this._onChartTypeSelected(chartType);
                });
            });
        });
    },

    _onChartTypeSelected: function(chartType: any) {
        if (this._onSelectCallback && typeof this._onSelectCallback === 'function') {
            this._onSelectCallback(chartType);
        }
    },

    show: function() {
        if (this.element) {
            this.element.style.display = 'block';
        }
    },

    hide: function() {
        if (this.element) {
            this.element.style.display = 'none';
        }
    },

    getSelectedChartType: function() {
        return this._selectedChartType;
    }
});

window.L.control.chartTypePicker = function(options: any, onSelectCallback: any) {
    return new window.L.Control.ChartTypePicker(options, onSelectCallback);
};
