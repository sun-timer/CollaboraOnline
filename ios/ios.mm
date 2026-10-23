// -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

#include "ios.h"
#include <algorithm>
#include <vector>
#include <comphelper/solarmutex.hxx>
#include <sfx2/lokhelper.hxx>
#include <sfx2/viewsh.hxx>
#include <sfx2/objsh.hxx>
#include <com/sun/star/beans/XMultiPropertySet.hpp>
#include <com/sun/star/beans/XPropertySet.hpp>
#include <com/sun/star/container/XEnumeration.hpp>
#include <com/sun/star/container/XEnumerationAccess.hpp>
#include <com/sun/star/document/XUndoManagerSupplier.hpp>
#include <com/sun/star/document/XUndoManager.hpp>
#include <com/sun/star/frame/XModel.hpp>
#include <com/sun/star/frame/XDispatch.hpp>
#include <com/sun/star/frame/XDispatchProvider.hpp>
#include <com/sun/star/frame/XStatusListener.hpp>
#include <com/sun/star/lang/XMultiComponentFactory.hpp>
#include <com/sun/star/uno/XComponentContext.hpp>
#include <com/sun/star/util/XURLTransformer.hpp>
#include <com/sun/star/util/URL.hpp>
#include <com/sun/star/sheet/CellFlags.hpp>
#include <com/sun/star/sheet/XCellRangeAddressable.hpp>
#include <com/sun/star/sheet/XCellAddressable.hpp>
#include <com/sun/star/sheet/XCellRangesQuery.hpp>
#include <com/sun/star/sheet/XSheetCellRanges.hpp>
#include <com/sun/star/sheet/XSheetCellRange.hpp>
#include <com/sun/star/sheet/XSpreadsheet.hpp>
#include <com/sun/star/util/CellProtection.hpp>
#include <com/sun/star/util/XProtectable.hpp>
#include <com/sun/star/table/XCell.hpp>
#include <com/sun/star/text/XText.hpp>
#include <com/sun/star/text/XTextCursor.hpp>
#include <com/sun/star/uno/RuntimeException.hpp>
#include <com/sun/star/view/XSelectionSupplier.hpp>
#include <cstring>
#include <cppuhelper/implbase.hxx>
#include <comphelper/processfactory.hxx>
#include <premac.h>

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#include <postmac.h>

extern "C" {
#import <native-code.h>
}

const char *user_name = nullptr;

static BOOL isLokPlaceholderUserName(NSString *name)
{
    if (name.length == 0) {
        return YES;
    }
    // LocalStorage fills in LocalUser#<id> when the LOK user_name is empty.
    if (![name hasPrefix:@"LocalUser#"]) {
        return NO;
    }
    NSString *suffix = [name substringFromIndex:[@"LocalUser#" length]];
    if (suffix.length == 0) {
        return YES;
    }
    NSCharacterSet *nonDigits = [[NSCharacterSet decimalDigitCharacterSet] invertedSet];
    return [suffix rangeOfCharacterFromSet:nonDigits].location == NSNotFound;
}

NSString *iosSettingsUserName(void)
{
    NSUserDefaults *defaults = NSUserDefaults.standardUserDefaults;
    NSString *name = [defaults stringForKey:@"USER_PROFILE_NAME"];
    if (isLokPlaceholderUserName(name)) {
        id managedName = [defaults dictionaryForKey:@"com.apple.configuration.managed"][@"userName"];
        if ([managedName isKindOfClass:[NSString class]]) {
            name = managedName;
        }
    }
    if (isLokPlaceholderUserName(name)) {
        name = [defaults stringForKey:@"userName"];
    }
    if (isLokPlaceholderUserName(name)) {
        name = @"orangepi";
    }
    return [name copy];
}

void refreshIOSUserName()
{
    // Keep the UTF-8 storage alive for LocalStorage while the document is open.
    static NSString *currentUserName;
    // Publish the Settings nickname, never the LOK LocalUser#N placeholder.
    currentUserName = iosSettingsUserName();
    user_name = currentUserName.UTF8String;

    // Push the nickname into any open document's web layer (comment author).
    // Must match kCOIOSUserNameDidChangeNotification in DocumentViewController.mm.
    [[NSNotificationCenter defaultCenter] postNotificationName:@"COIOSUserNameDidChange"
                                                        object:nil
                                                      userInfo:@{@"name": currentUserName}];
}

bool setIOSViewAuthor(lok::Document *document, int viewId, const char *name)
{
    auto *mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0 || !name || !*name) return false;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size()) || std::find(ids.begin(), ids.end(), viewId) == ids.end()) return false;
    SfxViewShell *view = SfxLokHelper::getViewOfId(viewId);
    if (!view) return false;
    // Impress persists the view's author, ignoring InsertAnnotation.Author.
    view->SetLOKAuthor(OUString::fromUtf8(name));
    return true;
}

IOSCalcTextFormatResult applyIOSCalcTextFormat(lok::Document *document, int viewId,
    const IOSCalcTextRange &expected, const char *operation, int value, bool toggle)
{
    using namespace com::sun::star;
    IOSCalcTextFormatResult result;
    const bool highlight = operation && std::strcmp(operation, "highlight") == 0;
    const bool superscript = operation && std::strcmp(operation, "superscript") == 0;
    const bool subscript = operation && std::strcmp(operation, "subscript") == 0;
    auto *mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0 || (!highlight && !superscript && !subscript)
        || (highlight ? value < -1 || value > 0xffffff : value != 0 && value != 1))
        return result;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (document->getDocumentType() != LOK_DOCTYPE_SPREADSHEET)
        return result;
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size())
        || std::find(ids.begin(), ids.end(), viewId) == ids.end()) {
        result.message = "文档视图已失效，请重新选择单元格";
        return result;
    }
    SfxViewShell *view = SfxLokHelper::getViewOfId(viewId);
    if (!view || !view->GetObjectShell() || view->GetObjectShell()->IsReadOnly()) {
        result.message = "请先进入编辑模式";
        return result;
    }
    struct RestoreView {
        lok::Document *doc;
        int previous;
        ~RestoreView() { doc->setView(previous); }
    } restore{document, document->getView()};
    document->setView(viewId);
    uno::Reference<com::sun::star::document::XUndoManager> undo;
    uno::Reference<frame::XModel> model;
    bool undoContext = false;
    bool controllersLocked = false;
    sal_Int32 undoCountBefore = -1;
    static sal_Int64 operationSerial = 0;
    const OUString undoTitle = (highlight ? u"文字高亮"_ustr : superscript ? u"上标"_ustr : u"下标"_ustr)
        + u" #"_ustr + OUString::number(++operationSerial);
    bool allHaveRequestedScript = true;
    bool highlightReadbackFailed = false;
    try {
        uno::Reference<com::sun::star::view::XSelectionSupplier> supplier(view->GetController(), uno::UNO_QUERY_THROW);
        uno::Any selection = supplier->getSelection();
        uno::Reference<sheet::XCellRangeAddressable> addressable(selection, uno::UNO_QUERY);
        if (!addressable.is()) {
            result.message = "请选择连续的文字单元格";
            return result;
        }
        const auto actual = addressable->getRangeAddress();
        if (actual.Sheet != expected.sheet || actual.StartColumn != expected.col1
            || actual.StartRow != expected.row1 || actual.EndColumn != expected.col2
            || actual.EndRow != expected.row2) {
            result.message = "选区已变化，请重新选择后操作";
            return result;
        }
        uno::Reference<sheet::XCellRangesQuery> query(selection, uno::UNO_QUERY_THROW);
        uno::Reference<sheet::XSheetCellRange> selectedRange(selection, uno::UNO_QUERY_THROW);
        uno::Reference<util::XProtectable> protection(selectedRange->getSpreadsheet(), uno::UNO_QUERY_THROW);
        const bool sheetProtected = protection->isProtected();
        auto textRanges = query->queryContentCells(sheet::CellFlags::STRING);
        auto cells = textRanges->getCells()->createEnumeration();
        std::vector<uno::Reference<beans::XMultiPropertySet>> targets;
        std::vector<table::CellAddress> targetAddresses;
        while (cells->hasMoreElements()) {
            auto entry = cells->nextElement();
            uno::Reference<table::XCell> cell(entry, uno::UNO_QUERY_THROW);
            // ScCellTextData::UpdateData writes rich text. Never run it for a
            // formula or numeric value: doing so would change the cell's type.
            if (cell->getType() != table::CellContentType_TEXT)
                continue;
            if (sheetProtected) {
                uno::Reference<beans::XPropertySet> cellProperties(cell, uno::UNO_QUERY_THROW);
                util::CellProtection cellProtection;
                if (!(cellProperties->getPropertyValue(u"CellProtection"_ustr) >>= cellProtection)
                    || cellProtection.IsLocked) {
                    result.message = "选区包含受保护的单元格";
                    return result;
                }
            }
            uno::Reference<text::XText> text(cell, uno::UNO_QUERY_THROW);
            if (text->getString().isEmpty())
                continue;
            auto cursor = text->createTextCursor();
            cursor->gotoStart(false);
            cursor->gotoEnd(true);
            uno::Reference<beans::XPropertySet> properties(cursor, uno::UNO_QUERY_THROW);
            const auto info = properties->getPropertySetInfo();
            if (!info->hasPropertyByName(highlight ? u"CharBackColor"_ustr : u"CharEscapement"_ustr)
                || !info->hasPropertyByName(highlight ? u"CharBackTransparent"_ustr : u"CharEscapementHeight"_ustr)) {
                result.message = "当前单元格不支持此文字格式";
                return result;
            }
            if (!highlight && toggle) {
                sal_Int16 escapement = 0;
                if (!(properties->getPropertyValue(u"CharEscapement"_ustr) >>= escapement)
                    || (superscript ? escapement <= 0 : escapement >= 0))
                    allHaveRequestedScript = false;
            }
            targets.emplace_back(cursor, uno::UNO_QUERY_THROW);
            uno::Reference<sheet::XCellAddressable> cellAddress(cell, uno::UNO_QUERY_THROW);
            targetAddresses.push_back(cellAddress->getCellAddress());
            if (targets.size() > 10000) {
                result.message = "请选择不超过一万个文字单元格后操作";
                return result;
            }
        }
        if (targets.empty()) {
            result.message = highlight ? "请选择非空的文字单元格；数字和公式暂不支持字符高亮"
                : "请选择含文字的单元格；公式和数值不会转换为文字";
            return result;
        }
        model = view->GetObjectShell()->GetModel();
        uno::Reference<com::sun::star::document::XUndoManagerSupplier> undoSupplier(model, uno::UNO_QUERY_THROW);
        undo = undoSupplier->getUndoManager();
        if (!undo.is() || undo->isLocked()) {
            result.message = "当前无法撤销此操作，请稍后重试";
            return result;
        }
        undoCountBefore = undo->getAllUndoActionTitles().getLength();
        if (!highlight && toggle) value = allHaveRequestedScript ? 0 : 1;
        undo->enterUndoContext(undoTitle);
        undoContext = true;
        model->lockControllers();
        controllersLocked = true;
        uno::Sequence<OUString> names(2);
        uno::Sequence<uno::Any> values(2);
        auto *propertyNames = names.getArray();
        auto *propertyValues = values.getArray();
        if (highlight) {
            propertyNames[0] = u"CharBackColor"_ustr;
            propertyNames[1] = u"CharBackTransparent"_ustr;
            propertyValues[0] <<= sal_Int32(value < 0 ? 0 : value);
            propertyValues[1] <<= bool(value < 0);
        } else {
            propertyNames[0] = u"CharEscapement"_ustr;
            propertyNames[1] = u"CharEscapementHeight"_ustr;
            propertyValues[0] <<= sal_Int16(value ? (superscript ? 101 : -101) : 0);
            propertyValues[1] <<= sal_Int8(value ? 58 : 100);
        }
        for (const auto &target : targets) {
            target->setPropertyValues(names, values);
            ++result.changed;
        }
        if (highlight) {
            // Read through new cell/text objects: the cursor used for writing
            // retains its EditEngine cache even if PutData simplified the model
            // to a plain string and discarded the character background.
            auto sheet = selectedRange->getSpreadsheet();
            for (const auto &address : targetAddresses) {
                uno::Reference<text::XText> savedText(
                    sheet->getCellByPosition(address.Column, address.Row), uno::UNO_QUERY_THROW);
                auto savedCursor = savedText->createTextCursor();
                savedCursor->gotoStart(false);
                savedCursor->gotoEnd(true);
                uno::Reference<beans::XPropertySet> savedProperties(savedCursor, uno::UNO_QUERY_THROW);
                sal_Int32 savedColor = -1;
                bool savedTransparent = true;
                const bool readable = (savedProperties->getPropertyValue(u"CharBackColor"_ustr) >>= savedColor)
                    && (savedProperties->getPropertyValue(u"CharBackTransparent"_ustr) >>= savedTransparent);
                const bool matches = readable && (value < 0 ? savedTransparent
                    : !savedTransparent && savedColor == value);
                if (!matches) {
                    NSLog(@"calc_text_format readback sheet=%d col=%d row=%d expectedColor=%d color=%d transparent=%d matches=0",
                        int(address.Sheet), int(address.Column), int(address.Row), value, int(savedColor), savedTransparent);
                    highlightReadbackFailed = true;
                    throw uno::RuntimeException(u"Calc character background was not retained in the model"_ustr);
                }
            }
            NSLog(@"calc_text_format readback verified=%lu expectedColor=%d",
                static_cast<unsigned long>(targetAddresses.size()), value);
        }
        model->unlockControllers();
        controllersLocked = false;
        undo->leaveUndoContext();
        undoContext = false;
        result.success = true;
        const sal_Int64 cellCount = (sal_Int64(actual.EndColumn) - actual.StartColumn + 1)
            * (sal_Int64(actual.EndRow) - actual.StartRow + 1);
        result.message = cellCount == result.changed ? ""
            : "已更新文字单元格；公式、数值和空白单元格未修改";
    } catch (const uno::Exception &) {
        // Do not leave an undo group or controller lock behind after an error.
        if (controllersLocked) {
            try { model->unlockControllers(); } catch (const uno::Exception &) {}
        }
        if (undoContext) {
            try { undo->leaveUndoContext(); } catch (const uno::Exception &) {}
        }
        try {
            if (undo.is() && undoCountBefore >= 0
                && undo->isUndoPossible() && undo->getCurrentUndoActionTitle() == undoTitle) {
                undo->undo();
                result.changed = 0;
            }
        } catch (const uno::Exception &) {}
        result.message = highlightReadbackFailed && result.changed == 0
            ? "文字高亮未能写入，已撤销本次操作"
            : result.changed > 0
            ? "部分文字格式已变化，请撤销后重新选择单元格"
            : "未能应用文字格式，请确认工作表和单元格未受保护";
    }
    return result;
}

namespace {
class IOSCalcFormatStatusListener final
    : public cppu::WeakImplHelper<com::sun::star::frame::XStatusListener> {
public:
    com::sun::star::uno::Any state;
    bool received = false;

    void SAL_CALL statusChanged(const com::sun::star::frame::FeatureStateEvent& event) override
    {
        state = event.State;
        received = true;
    }
    void SAL_CALL disposing(const com::sun::star::lang::EventObject&) override {}
};

static bool readCalcSlotState(
    const com::sun::star::uno::Reference<com::sun::star::frame::XDispatchProvider>& provider,
    const com::sun::star::uno::Reference<com::sun::star::util::XURLTransformer>& transformer,
    const OUString& command, com::sun::star::uno::Any& result)
{
    using namespace com::sun::star;
    util::URL url;
    url.Complete = command;
    transformer->parseStrict(url);
    uno::Reference<frame::XDispatch> dispatch = provider->queryDispatch(url, OUString(), 0);
    if (!dispatch.is()) return false;
    uno::Reference<IOSCalcFormatStatusListener> listener(new IOSCalcFormatStatusListener());
    dispatch->addStatusListener(listener, url);
    const bool received = listener->received;
    result = listener->state;
    dispatch->removeStatusListener(listener, url);
    return received && result.hasValue();
}
}

IOSCalcFormatStates getIOSCalcFormatStates(lok::Document *document, int viewId,
    const IOSCalcTextRange& expected)
{
    using namespace com::sun::star;
    IOSCalcFormatStates result;
    auto *mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0) return result;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (document->getDocumentType() != LOK_DOCTYPE_SPREADSHEET) return result;
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size())
        || std::find(ids.begin(), ids.end(), viewId) == ids.end()) return result;
    SfxViewShell *view = SfxLokHelper::getViewOfId(viewId);
    if (!view || !view->GetController()) return result;
    struct RestoreView {
        lok::Document *doc;
        int previous;
        ~RestoreView() { doc->setView(previous); }
    } restore{document, document->getView()};
    document->setView(viewId);
    try {
        uno::Reference<view::XSelectionSupplier> supplier(view->GetController(), uno::UNO_QUERY_THROW);
        uno::Reference<sheet::XCellRangeAddressable> addressable(supplier->getSelection(), uno::UNO_QUERY);
        if (!addressable.is()) return result;
        const auto actual = addressable->getRangeAddress();
        if (actual.Sheet != expected.sheet || actual.StartColumn != expected.col1
            || actual.StartRow != expected.row1 || actual.EndColumn != expected.col2
            || actual.EndRow != expected.row2) return result;
        uno::Reference<frame::XDispatchProvider> provider(view->GetController(), uno::UNO_QUERY_THROW);
        uno::Reference<util::XURLTransformer> transformer(
            comphelper::getProcessComponentContext()->getServiceManager()->createInstanceWithContext(
                u"com.sun.star.util.URLTransformer"_ustr, comphelper::getProcessComponentContext()),
            uno::UNO_QUERY_THROW);
        uno::Any state;
        OUString numberFormat;
        if (readCalcSlotState(provider, transformer, u".uno:NumberFormat"_ustr, state)
            && (state >>= numberFormat)) {
            result.numberFormat = numberFormat.toUtf8().getStr();
            result.hasNumberFormat = true;
        }
        state.clear();
        sal_Int32 rotation = 0;
        if (readCalcSlotState(provider, transformer, u".uno:AlignmentRotation"_ustr, state)
            && (state >>= rotation)) {
            result.rotation = rotation;
            result.hasRotation = true;
        }
        state.clear();
        sal_Bool stacked = false;
        if (readCalcSlotState(provider, transformer, u".uno:AlignmentStacked"_ustr, state)
            && (state >>= stacked)) {
            result.stacked = stacked;
            result.hasStacked = true;
        }
        result.success = true;
    } catch (...) {
        result.success = false;
    }
    return result;
}

int coolwsd_server_socket_fd = -1;

LibreOfficeKit *lo_kit;

// vim:set shiftwidth=4 softtabstop=4 expandtab:
