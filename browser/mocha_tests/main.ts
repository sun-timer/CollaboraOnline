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

/// <reference path="./refs/globals.ts" />
/// <reference path="./sources.ts" />
/// <reference path="./helper/canvasContainerSetup.ts" />
/// <reference path="./helper/rectUtil.ts" />
/// <reference path="helper/Events.ts"/>
/// <reference path="./helper/util.ts"/>
/// <reference path="./data/LOUtilTestData.ts"/>
/// <reference path="./data/SheetGeometryTestData.ts" />
/// <reference path="./data/NativeBridgeTestData.ts" />
/// <reference path="../src/app/NativeBridge.ts" />
/// <reference path="../src/app/WriterAiCatalog.ts" />
/// <reference path="../src/app/CalcAiCatalog.ts" />
/// <reference path="../src/app/CalcAiContext.ts" />
/// <reference path="../src/app/MobileAiUiCatalog.ts" />
/// <reference path="../src/app/MobileNativeToolbar.ts" />
/// <reference path="../src/app/MobileAiBridge.ts" />
/// <reference path="../src/app/WriterAiController.ts" />
/// <reference path="../src/app/CalcAiMutateApply.ts" />
/// <reference path="../src/app/CalcAiController.ts" />
/// <reference path="../src/app/FormatBatchProcessor.ts" />
/// <reference path="../src/app/MobileAiFormatBatchDialog.ts" />
/// <reference path="../src/app/MobileAiImageDialog.ts" />
/// <reference path="../src/app/MobileAiConversationController.ts" />
/// <reference path="../src/app/MobileAiSheet.ts" />
/// <reference path="../src/app/MobileAiAssistantPanel.ts" />
/// <reference path="../src/app/MobileAiOperationSheet.ts" />
/// <reference path="../src/app/MobileAiOperationDialog.ts" />
/// <reference path="../src/app/MobileAiCalcDialog.ts" />
/// <reference path="../src/app/MobileAiTranslateDialog.ts" />
/// <reference path="../src/app/MobileAiResultRenderer.ts" />
/// <reference path="../src/app/MobileAiConfiguration.ts" />
/// <reference path="../src/app/WriterAiPanel.ts" />
/// <reference path="../src/app/WriterEditorCatalog.ts" />
/// <reference path="../src/app/WriterEditorController.ts" />
/// <reference path="../src/app/WriterEditorWatermarkDialog.ts" />
/// <reference path="../src/app/WriterEditorSheet.ts" />
/// <reference path="../src/app/WriterEditorIcons.ts" />

/// <reference path="./CanvasSectionContainer.test.ts" />
/// <reference path="./CBounds.test.ts" />
/// <reference path="./CPointSet.test.ts" />
/// <reference path="./Events.test.ts" />
/// <reference path="./LOUtil.test.ts" />
/// <reference path="./Rectangle.test.ts" />
/// <reference path="./SheetGeometry.test.ts" />
/// <reference path="./Util.test.ts" />
/// <reference path="./ViewLayout.test.ts" />
/// <reference path="./ServerCommand.test.ts" />
/// <reference path="./MobileAiBridge.test.ts" />
/// <reference path="./WriterAiCatalog.test.ts" />
/// <reference path="./CalcAiCatalog.test.ts" />
/// <reference path="./MobileAiUiCatalog.test.ts" />
/// <reference path="./MobileNativeToolbar.test.ts" />
/// <reference path="./WriterAiController.test.ts" />
/// <reference path="./CalcAiController.test.ts" />
/// <reference path="./MobileAiConversationController.test.ts" />
/// <reference path="./MobileAiOperationDialog.test.ts" />
/// <reference path="./WriterEditorCatalog.test.ts" />
/// <reference path="./WriterEditorController.test.ts" />
/// <reference path="./FormatBatchProcessor.test.ts" />
/// <reference path="./MobileAiImageDialog.test.ts" />
// NOTE: reference new tests here ...
