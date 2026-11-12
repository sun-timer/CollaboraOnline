// -*- Mode: C++; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4; fill-column: 100 -*-
//
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.

#include "ios.h"
#include "../common/MobileImpressBackground.hpp"
#include <algorithm>
#include <vector>
#include <map>
#include <iterator>
#include <comphelper/solarmutex.hxx>
#include <sfx2/lokhelper.hxx>
#include <sfx2/viewsh.hxx>
#include <sfx2/objsh.hxx>
#include <com/sun/star/beans/XMultiPropertySet.hpp>
#include <com/sun/star/beans/XPropertySet.hpp>
#include <com/sun/star/beans/XPropertyState.hpp>
#include <com/sun/star/awt/FontWeight.hpp>
#include <com/sun/star/awt/FontSlant.hpp>
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
#include <com/sun/star/sheet/XSheetCellCursor.hpp>
#include <com/sun/star/sheet/XSpreadsheet.hpp>
#include <com/sun/star/util/CellProtection.hpp>
#include <com/sun/star/util/XProtectable.hpp>
#include <com/sun/star/table/XCell.hpp>
#include <com/sun/star/table/BorderLine2.hpp>
#include <com/sun/star/table/TableBorder2.hpp>
#include <com/sun/star/util/XMergeable.hpp>
#include <com/sun/star/util/XNumberFormatsSupplier.hpp>
#include <com/sun/star/util/XNumberFormats.hpp>
#include <com/sun/star/util/NumberFormat.hpp>
#include <com/sun/star/table/CellHoriJustify.hpp>
#include <com/sun/star/sheet/XCellFormatRangesSupplier.hpp>
#include <com/sun/star/text/XText.hpp>
#include <com/sun/star/text/XTextCursor.hpp>
#include <com/sun/star/uno/RuntimeException.hpp>
#include <com/sun/star/view/XSelectionSupplier.hpp>
#include <svx/svdview.hxx>
#include <svx/svdmodel.hxx>
#include <svx/svdpagv.hxx>
#include <svx/svdpage.hxx>
#include <svx/svditer.hxx>
#include <svx/svdotext.hxx>
#include <svx/unoapi.hxx>
#include <svl/itemiter.hxx>
#include <svl/undo.hxx>
#include <editeng/eeitem.hxx>
#include <editeng/wghtitem.hxx>
#include <editeng/postitem.hxx>
#include <editeng/udlnitem.hxx>
#include <editeng/crossedoutitem.hxx>
#include <editeng/outlobj.hxx>
#include <editeng/editobj.hxx>
#include <editeng/outliner.hxx>
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

IOSImpressShapeFormatResult toggleIOSImpressShapeFormat(lok::Document *document,
    int viewId, int part, const char *command, const char *selectionId)
{
    IOSImpressShapeFormatResult result;
    const bool bold = command && std::strcmp(command, ".uno:Bold") == 0;
    const bool italic = command && std::strcmp(command, ".uno:Italic") == 0;
    const bool underline = command && std::strcmp(command, ".uno:Underline") == 0;
    const bool strikeout = command && std::strcmp(command, ".uno:Strikeout") == 0;
    if (!bold && !italic && !underline && !strikeout) return result;
    auto *mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0 || part < 0) return result;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (document->getDocumentType() != LOK_DOCTYPE_PRESENTATION) return result;
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size())
        || std::find(ids.begin(), ids.end(), viewId) == ids.end()) return result;
    SfxViewShell *view = SfxLokHelper::getViewOfId(viewId);
    if (!view || !view->GetObjectShell() || view->GetObjectShell()->IsReadOnly()) return result;
    struct RestoreView {
        lok::Document *doc;
        int previous;
        ~RestoreView() { doc->setView(previous); }
    } restore{document, document->getView()};
    document->setView(viewId);
    if (document->getPart() != part) return result;
    SdrView *draw = view->GetDrawView();
    NSLog(@"[ImpressShapeFormat] stage=selection view=%d part=%d draw=%d textEdit=%d marks=%lu undo=%d",
        viewId, part, draw != nullptr, draw && draw->IsTextEdit(),
        draw ? static_cast<unsigned long>(draw->GetMarkedObjectList().GetMarkCount()) : 0UL,
        draw && draw->GetModel().IsUndoEnabled());
    if (!draw || !draw->GetModel().IsUndoEnabled()) return result;
    if (selectionId && *selectionId) {
        const auto matches = [&](const SdrObject* object) {
            if (!object) return false;
            const OString objectId = OString::number(reinterpret_cast<sal_IntPtr>(object));
            return objectId == OString(selectionId);
        };
        // Find the ID only among live objects on this view's current slide.
        // The token is never cast back to a pointer or used on a different page.
        auto* pageView = draw->GetSdrPageView();
        if (!pageView) return result;
        try {
            namespace css = com::sun::star;
            css::uno::Reference<css::drawing::XDrawView> controller(view->GetController(), css::uno::UNO_QUERY_THROW);
            if (GetSdrPageFromXDrawPage(controller->getCurrentPage()) != pageView->GetPage()) {
                NSLog(@"[ImpressShapeFormat] stage=resolve reason=page_mismatch");
                return result;
            }
        } catch (const com::sun::star::uno::Exception&) { return result; }
        SdrObject* target = nullptr;
        SdrObjListIter objects(pageView->GetPage(), SdrIterMode::DeepWithGroups);
        while (objects.IsMore()) {
            auto* candidate = objects.Next();
            if (matches(candidate)) { target = candidate; break; }
        }
        NSLog(@"[ImpressShapeFormat] stage=resolve found=%d textObject=%d markable=%d",
            target != nullptr, target && dynamic_cast<SdrTextObj*>(target) != nullptr,
            target && draw->IsObjMarkable(target, pageView));
        if (!target || !dynamic_cast<SdrTextObj*>(target) || !draw->IsObjMarkable(target, pageView)) return result;
        const auto& marks = draw->GetMarkedObjectList();
        if (marks.GetMarkCount() && !draw->IsObjMarked(target)) return result;
        if (draw->IsTextEdit()) {
            if (draw->GetTextEditObject() != target) return result;
            // An explicit frame selection formats existing text, not just the
            // next characters at a hidden insertion caret.
            draw->SdrEndTextEdit(true);
        }
        if (!draw->IsObjMarked(target)) draw->MarkObj(target, pageView);
        NSLog(@"[ImpressShapeFormat] stage=restore marks=%lu matched=%d",
            static_cast<unsigned long>(draw->GetMarkedObjectList().GetMarkCount()), draw->IsObjMarked(target));
    }
    if (!draw->IsTextEdit() && draw->GetMarkedObjectList().GetMarkCount() == 0) return result;
    // Use the exact drawing-view path used by Impress, but include all three
    // script attributes. SetAttributes owns undo, text-run attribute cleanup,
    // object-change broadcasts and tile invalidation.
    SfxItemSet current(draw->GetModel().GetItemPool());
    draw->GetAttributes(current);
    // Ignore unused scripts when deciding the toggle. A Chinese-only text box
    // can already be bold while its unused Latin/CTL defaults are still normal.
    // Mixed (INVALID) attributes mean "apply", never read a default as a value.
    const auto scripts = draw->GetScriptType();
    const auto weightEnabled = [&](auto which) {
        return current.GetItemState(which) != SfxItemState::INVALID
            && current.Get(which).GetWeight() >= WEIGHT_BOLD;
    };
    const auto postureEnabled = [&](auto which) {
        return current.GetItemState(which) != SfxItemState::INVALID
            && current.Get(which).GetPosture() != ITALIC_NONE;
    };
    const bool allEnabled = underline
        ? current.GetItemState(EE_CHAR_UNDERLINE) != SfxItemState::INVALID
            && current.Get(EE_CHAR_UNDERLINE).GetLineStyle() != LINESTYLE_NONE
        : strikeout
        ? current.GetItemState(EE_CHAR_STRIKEOUT) != SfxItemState::INVALID
            && current.Get(EE_CHAR_STRIKEOUT).GetStrikeout() != STRIKEOUT_NONE
        : (!(scripts & SvtScriptType::LATIN) || (bold ? weightEnabled(EE_CHAR_WEIGHT) : postureEnabled(EE_CHAR_ITALIC)))
            && (!(scripts & SvtScriptType::ASIAN) || (bold ? weightEnabled(EE_CHAR_WEIGHT_CJK) : postureEnabled(EE_CHAR_ITALIC_CJK)))
            && (!(scripts & SvtScriptType::COMPLEX) || (bold ? weightEnabled(EE_CHAR_WEIGHT_CTL) : postureEnabled(EE_CHAR_ITALIC_CTL)));
    result.enabled = !allEnabled;
    SfxItemSet attributes(draw->GetModel().GetItemPool());
    if (bold) {
        const FontWeight value = result.enabled ? WEIGHT_BOLD : WEIGHT_NORMAL;
        attributes.Put(SvxWeightItem(value, EE_CHAR_WEIGHT));
        attributes.Put(SvxWeightItem(value, EE_CHAR_WEIGHT_CJK));
        attributes.Put(SvxWeightItem(value, EE_CHAR_WEIGHT_CTL));
    } else if (italic) {
        const FontItalic value = result.enabled ? ITALIC_NORMAL : ITALIC_NONE;
        attributes.Put(SvxPostureItem(value, EE_CHAR_ITALIC));
        attributes.Put(SvxPostureItem(value, EE_CHAR_ITALIC_CJK));
        attributes.Put(SvxPostureItem(value, EE_CHAR_ITALIC_CTL));
    } else if (underline) {
        attributes.Put(SvxUnderlineItem(result.enabled ? LINESTYLE_SINGLE : LINESTYLE_NONE, EE_CHAR_UNDERLINE));
    } else {
        attributes.Put(SvxCrossedOutItem(result.enabled ? STRIKEOUT_SINGLE : STRIKEOUT_NONE, EE_CHAR_STRIKEOUT));
    }
    result.success = draw->SetAttributes(attributes, false);
    result.changed = result.success ? 1 : 0;
    SfxItemSet readback(draw->GetModel().GetItemPool());
    draw->GetAttributes(readback);
    SfxItemIter written(attributes);
    for (const SfxPoolItem* item = written.GetCurItem(); item; item = written.NextItem()) {
        const SfxPoolItem* saved = nullptr;
        readback.GetItemState(item->Which(), true, &saved);
        if (!saved || !(*saved == *item)) result.success = false;
    }
    NSLog(@"[ImpressShapeFormat] stage=readback verified=%d marks=%lu textEdit=%d", result.success,
        static_cast<unsigned long>(draw->GetMarkedObjectList().GetMarkCount()), draw->IsTextEdit());
    if (result.success) {
        result.changed = 1;
        result.message = "";
    }
    return result;
}

IOSImpressShapeFormatStates getIOSImpressShapeFormatStates(lok::Document *document,
    int viewId, int part, const char *selectionId)
{
    using namespace com::sun::star;
    IOSImpressShapeFormatStates result;
    auto *mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0 || part < 0 || !selectionId || !*selectionId) return result;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (document->getDocumentType() != LOK_DOCTYPE_PRESENTATION) return result;
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size())
        || std::find(ids.begin(), ids.end(), viewId) == ids.end()) return result;
    SfxViewShell *view = SfxLokHelper::getViewOfId(viewId);
    if (!view) return result;
    struct RestoreView {
        lok::Document *doc;
        int previous;
        ~RestoreView() { doc->setView(previous); }
    } restore{document, document->getView()};
    document->setView(viewId);
    if (document->getPart() != part) return result;
    if (std::strcmp(selectionId, "text") == 0) {
        auto *draw = view->GetDrawView();
        auto *textView = draw && draw->IsTextEdit() ? draw->GetTextEditOutlinerView() : nullptr;
        if (!textView) return result;
        const SfxItemSet attrs = textView->GetAttribs();
        auto scripts = textView->GetSelectedScriptType();
        if (scripts == SvtScriptType::NONE) scripts = SvtScriptType::LATIN;
        const auto flag = [&](auto which, auto enabled) -> int {
            return attrs.GetItemState(which) == SfxItemState::INVALID ? -1 : int(enabled(attrs.Get(which)));
        };
        const auto merged = [&](bool weight) {
            int state = -2;
            const auto add = [&](int next) { state = state == -2 ? next : state == next ? state : -1; };
            const auto bold = [](const auto& item) { return item.GetWeight() >= WEIGHT_BOLD; };
            const auto italic = [](const auto& item) { return item.GetPosture() != ITALIC_NONE; };
            if (scripts & SvtScriptType::LATIN) add(weight ? flag(EE_CHAR_WEIGHT, bold) : flag(EE_CHAR_ITALIC, italic));
            if (scripts & SvtScriptType::ASIAN) add(weight ? flag(EE_CHAR_WEIGHT_CJK, bold) : flag(EE_CHAR_ITALIC_CJK, italic));
            if (scripts & SvtScriptType::COMPLEX) add(weight ? flag(EE_CHAR_WEIGHT_CTL, bold) : flag(EE_CHAR_ITALIC_CTL, italic));
            return state == -2 ? -1 : state;
        };
        result.bold = merged(true); result.italic = merged(false);
        result.underline = flag(EE_CHAR_UNDERLINE, [](const auto& item) { return item.GetLineStyle() != LINESTYLE_NONE; });
        result.strikeout = flag(EE_CHAR_STRIKEOUT, [](const auto& item) { return item.GetStrikeout() != STRIKEOUT_NONE; });
        result.success = true;
        return result;
    }
    try {
        uno::Reference<drawing::XDrawView> controller(view->GetController(), uno::UNO_QUERY_THROW);
        auto *page = GetSdrPageFromXDrawPage(controller->getCurrentPage());
        if (!page) return result;
        SdrTextObj *target = nullptr;
        SdrObjListIter objects(page, SdrIterMode::DeepWithGroups);
        while (objects.IsMore()) {
            auto *object = objects.Next();
            const OString id = OString::number(reinterpret_cast<sal_IntPtr>(object));
            if (id == OString(selectionId)) { target = dynamic_cast<SdrTextObj*>(object); break; }
        }
        if (!target) return result;
        // Query the entire persisted text, including inherited and run-level
        // attributes. Draw-shell command state can describe an empty selection
        // or an unused Latin script after reopening a Chinese presentation.
        uno::Reference<text::XText> text(target->getUnoShape(), uno::UNO_QUERY_THROW);
        auto cursor = text->createTextCursor();
        cursor->gotoStart(false);
        cursor->gotoEnd(true);
        uno::Reference<beans::XPropertySet> properties(cursor, uno::UNO_QUERY_THROW);
        uno::Reference<beans::XPropertyState> states(cursor, uno::UNO_QUERY_THROW);
        const auto read = [&](const OUString &name, auto enabled) -> int {
            if (states->getPropertyState(name) == beans::PropertyState_AMBIGUOUS_VALUE) return -1;
            return enabled(properties->getPropertyValue(name));
        };
        const auto weight = [](const uno::Any &value) -> int {
            float number = 0;
            return (value >>= number) ? int(number >= awt::FontWeight::BOLD) : -1;
        };
        const auto posture = [](const uno::Any &value) -> int {
            awt::FontSlant slant;
            return (value >>= slant) ? int(slant != awt::FontSlant_NONE) : -1;
        };
        const auto line = [](const uno::Any &value) -> int {
            sal_Int16 style = 0;
            return (value >>= style) ? int(style != 0) : -1;
        };
        auto scripts = SvtScriptType::LATIN;
        if (const auto *paragraph = target->GetOutlinerParaObject()) {
            scripts = paragraph->GetTextObject().GetScriptType();
            if (scripts == SvtScriptType::NONE) scripts = SvtScriptType::LATIN;
        }
        const auto scriptState = [&](bool bold) {
            int merged = -2;
            const auto include = [&](SvtScriptType script, const OUString &name) {
                if (!(scripts & script)) return;
                const int value = bold ? read(name, weight) : read(name, posture);
                merged = merged == -2 ? value : merged == value ? merged : -1;
            };
            include(SvtScriptType::LATIN, bold ? u"CharWeight"_ustr : u"CharPosture"_ustr);
            include(SvtScriptType::ASIAN, bold ? u"CharWeightAsian"_ustr : u"CharPostureAsian"_ustr);
            include(SvtScriptType::COMPLEX, bold ? u"CharWeightComplex"_ustr : u"CharPostureComplex"_ustr);
            return merged == -2 ? -1 : merged;
        };
        result.bold = scriptState(true);
        result.italic = scriptState(false);
        result.underline = read(u"CharUnderline"_ustr, line);
        result.strikeout = read(u"CharStrikeout"_ustr, line);
        result.success = true;
    } catch (const uno::Exception&) {}
    return result;
}

bool setIOSImpressMasterBackground(lok::Document *document, int viewId, int part, bool visible, bool *changed)
{
    return setMobileImpressMasterBackground(document, viewId, part, visible, changed);
}

// A merged cell is reported as its anchor by one side and its full area by the
// other. Compare their merged areas without moving the UI selection.
static com::sun::star::uno::Reference<com::sun::star::sheet::XSheetCellRange>
iosValidatedCalcSelection(const com::sun::star::uno::Any& selection, const IOSCalcTextRange& expected)
{
    using namespace com::sun::star;
    uno::Reference<sheet::XSheetCellRange> range(selection, uno::UNO_QUERY);
    uno::Reference<sheet::XCellRangeAddressable> addressable(range, uno::UNO_QUERY);
    if (!addressable.is()) return {};
    const auto actual = addressable->getRangeAddress();
    if (actual.Sheet != expected.sheet) return {};
    if (actual.StartColumn == expected.col1 && actual.StartRow == expected.row1
        && actual.EndColumn == expected.col2 && actual.EndRow == expected.row2) return range;
    auto sheet = range->getSpreadsheet();
    auto live = sheet->createCursorByRange(range);
    live->collapseToMergedArea();
    uno::Reference<sheet::XSheetCellRange> wanted(sheet->getCellRangeByPosition(
        expected.col1, expected.row1, expected.col2, expected.row2), uno::UNO_QUERY_THROW);
    auto requested = sheet->createCursorByRange(wanted);
    requested->collapseToMergedArea();
    uno::Reference<sheet::XCellRangeAddressable> liveAddress(live, uno::UNO_QUERY_THROW);
    uno::Reference<sheet::XCellRangeAddressable> requestedAddress(requested, uno::UNO_QUERY_THROW);
    const auto a = liveAddress->getRangeAddress(), b = requestedAddress->getRangeAddress();
    return a.Sheet == b.Sheet && a.StartColumn == b.StartColumn && a.StartRow == b.StartRow
        && a.EndColumn == b.EndColumn && a.EndRow == b.EndRow ? range : uno::Reference<sheet::XSheetCellRange>();
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
    auto *groupUndo = view->GetUndoManager();
    if (!groupUndo) return result;
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
        auto validated = iosValidatedCalcSelection(selection, expected);
        if (!validated.is()) {
            result.message = "选区已变化，请重新选择后操作";
            return result;
        }
        selection <<= validated;
        uno::Reference<sheet::XCellRangeAddressable> addressable(selection, uno::UNO_QUERY);
        if (!addressable.is()) {
            result.message = "请选择连续的文字单元格";
            return result;
        }
        const auto actual = addressable->getRangeAddress();
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
        groupUndo->EnterListAction(undoTitle, OUString(), 0, view->GetViewShellId());
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
        groupUndo->LeaveListAction();
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
            try { groupUndo->LeaveListAction(); } catch (const uno::Exception &) {}
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
    bool enabled = false;

    void SAL_CALL statusChanged(const com::sun::star::frame::FeatureStateEvent& event) override
    {
        state = event.State;
        enabled = event.IsEnabled;
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
    result = listener->enabled ? listener->state : com::sun::star::uno::Any(u"disabled"_ustr);
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
        auto selected = iosValidatedCalcSelection(supplier->getSelection(), expected);
        if (!selected.is()) return result;
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
        NSMutableDictionary *values = [NSMutableDictionary dictionary];
        // Read the selected model, not the last toolbar choice. Null means mixed/unknown.
        uno::Reference<beans::XPropertySet> props(selected, uno::UNO_QUERY_THROW);
        uno::Reference<beans::XPropertyState> propertyStates(props, uno::UNO_QUERY_THROW);
        const auto property = [&](const OUString& name) -> uno::Any {
            try {
                if (propertyStates->getPropertyState(name) == beans::PropertyState_AMBIGUOUS_VALUE)
                    return {};
                return props->getPropertyValue(name);
            } catch (const uno::Exception&) { return {}; }
        };
        const auto number = [&](const char *command, const OUString& name, double scale = 1.0) {
            uno::Any value = property(name);
            double d; float f; sal_Int32 i; sal_Int16 h;
            id output = NSNull.null;
            if (value >>= d) output = @(d * scale);
            else if (value >>= f) output = @(f * scale);
            else if (value >>= i) output = @(i * scale);
            else if (value >>= h) output = @(h * scale);
            values[@(command)] = output;
        };
        OUString font;
        values[@".uno:CharFontName"] = (property(u"CharFontName"_ustr) >>= font)
            ? [NSString stringWithUTF8String:font.toUtf8().getStr()] : (id)NSNull.null;
        number(".uno:FontHeight", u"CharHeight"_ustr);
        number(".uno:Color", u"CharColor"_ustr);
        sal_Bool transparent;
        if (property(u"IsCellBackgroundTransparent"_ustr) >>= transparent) {
            if (transparent) values[@".uno:BackgroundColor"] = @(-1);
            else number(".uno:BackgroundColor", u"CellBackColor"_ustr);
        } else values[@".uno:BackgroundColor"] = NSNull.null;
        number(".uno:ParaIndent", u"ParaIndent"_ustr, 72.0 / 2540.0);
        const auto flag = [&](const char *command, const OUString& name) {
            sal_Bool b;
            values[@(command)] = (property(name) >>= b) ? (id)@(bool(b)) : (id)NSNull.null;
        };
        flag(".uno:Shadowed", u"CharShadowed"_ustr);
        flag(".uno:WrapText", u"IsTextWrapped"_ustr);
        uno::Reference<util::XMergeable> mergeable(props, uno::UNO_QUERY);
        values[@".uno:ToggleMergeCells"] = mergeable.is() ? (id)@(mergeable->getIsMerged()) : (id)NSNull.null;
        // Dispatch states also carry disablement (protected cells / unavailable commands).
        for (const char *command : {".uno:Bold", ".uno:Italic", ".uno:Underline", ".uno:Strikeout",
            ".uno:AlignLeft", ".uno:AlignHorizontalCenter", ".uno:AlignRight", ".uno:AlignBlock",
            ".uno:AlignTop", ".uno:AlignVCenter", ".uno:AlignBottom",
            ".uno:ParaLeftToRight", ".uno:ParaRightToLeft", ".uno:IncrementIndent", ".uno:DecrementIndent",
            ".uno:InsertRowsBefore", ".uno:InsertColumnsAfter", ".uno:DeleteRows", ".uno:DeleteColumns",
            ".uno:FreezePanes", ".uno:FreezePanesRow", ".uno:FreezePanesColumn", ".uno:ToggleSheetGrid"}) {
            uno::Any slot;
            NSString *key = @(command);
            values[key] = NSNull.null;
            if (!readCalcSlotState(provider, transformer, OUString::fromUtf8(command), slot)) continue;
            sal_Bool b; sal_Int32 i; sal_Int16 h; OUString str;
            if (slot >>= b) values[key] = @(bool(b));
            else if (slot >>= i) values[key] = @(i);
            else if (slot >>= h) values[key] = @(h);
            else if (slot >>= str) values[key] = [NSString stringWithUTF8String:str.toUtf8().getStr()];
        }
        // Underline/strikeout have multiple nonzero styles, all count as enabled.
        for (const auto& item : std::initializer_list<std::pair<const char *, OUString>>{
            {".uno:Underline", u"CharUnderline"_ustr}, {".uno:Strikeout", u"CharStrikeout"_ustr}}) {
            if ([values[@(item.first)] isEqual:@"disabled"]) continue;
            sal_Int16 style = 0;
            values[@(item.first)] = (property(item.second) >>= style) ? (id)@(style != 0) : (id)NSNull.null;
        }
        // This is the persisted format key, including mixed-selection detection.
        sal_Int16 category = -1;
        sal_Int32 formatKey = 0;
        if (property(u"NumberFormat"_ustr) >>= formatKey) {
            uno::Reference<util::XNumberFormatsSupplier> formats(view->GetObjectShell()->GetModel(), uno::UNO_QUERY_THROW);
            sal_Int16 type = 0;
            formats->getNumberFormats()->getByKey(formatKey)->getPropertyValue(u"Type"_ustr) >>= type;
            type &= ~util::NumberFormat::DEFINED;
            switch (type) {
                case util::NumberFormat::NUMBER: category = formatKey % 10000 == 0 ? 0 : 1; break;
                case util::NumberFormat::PERCENT: category = 2; break;
                case util::NumberFormat::CURRENCY: category = 3; break;
                case util::NumberFormat::DATE: case util::NumberFormat::DATETIME: category = 4; break;
                case util::NumberFormat::TIME: category = 5; break;
                case util::NumberFormat::SCIENTIFIC: category = 6; break;
                case util::NumberFormat::FRACTION: category = 7; break;
                case util::NumberFormat::LOGICAL: category = 8; break;
                case util::NumberFormat::TEXT: category = 9; break;
            }
        }
        table::CellHoriJustify horizontal;
        const bool hasHorizontal = property(u"HoriJustify"_ustr) >>= horizontal;
        for (const auto& align : std::initializer_list<std::pair<const char *, table::CellHoriJustify>>{
            {".uno:AlignLeft", table::CellHoriJustify_LEFT}, {".uno:AlignHorizontalCenter", table::CellHoriJustify_CENTER},
            {".uno:AlignRight", table::CellHoriJustify_RIGHT}, {".uno:AlignBlock", table::CellHoriJustify_BLOCK}}) {
            if (![values[@(align.first)] isEqual:@"disabled"])
                values[@(align.first)] = hasHorizontal ? (id)@(horizontal == align.second) : (id)NSNull.null;
        }
        int categoryIndex = 0;
        for (const char *name : {"NumberFormatStandard", "NumberFormatDecimal", "NumberFormatPercent",
            "NumberFormatCurrency", "NumberFormatDate", "NumberFormatTime", "NumberFormatScientific",
            "NumberFormatFraction", "NumberFormatLogical", "NumberFormatText"}) {
            values[[NSString stringWithFormat:@".uno:%s", name]] = category < 0 ? (id)NSNull.null : (id)@(category == categoryIndex);
            ++categoryIndex;
        }
        // Rich-text scripts live on text cursors, not the cell format shell.
        values[@".uno:SuperScript"] = NSNull.null;
        values[@".uno:SubScript"] = NSNull.null;
        uno::Reference<sheet::XCellRangesQuery> query(selected, uno::UNO_QUERY_THROW);
        auto cells = query->queryContentCells(sheet::CellFlags::STRING)->getCells()->createEnumeration();
        int script = -2;
        while (cells->hasMoreElements()) {
            uno::Reference<text::XText> text(cells->nextElement(), uno::UNO_QUERY_THROW);
            auto cursor = text->createTextCursor();
            cursor->gotoEnd(true);
            uno::Reference<beans::XPropertySet> cp(cursor, uno::UNO_QUERY_THROW);
            uno::Reference<beans::XPropertyState> cs(cursor, uno::UNO_QUERY_THROW);
            sal_Int16 escape = 0;
            int next = cs->getPropertyState(u"CharEscapement"_ustr) != beans::PropertyState_AMBIGUOUS_VALUE
                && (cp->getPropertyValue(u"CharEscapement"_ustr) >>= escape) ? (escape > 0 ? 1 : escape < 0 ? -1 : 0) : 2;
            if (script == -2) script = next;
            else if (script != next) script = 2;
            if (script == 2) break;
        }
        if (script != 2) {
            values[@".uno:SuperScript"] = @(script == 1);
            values[@".uno:SubScript"] = @(script == -1);
        }
        table::TableBorder2 borders;
        int color = -2;
        bool mixedBorder = false;
        const auto lineColor = [&](const table::BorderLine2& line, bool valid) {
            if (!valid) { mixedBorder = true; return; }
            if (!line.LineWidth && !line.OuterLineWidth && !line.InnerLineWidth) return;
            if (color == -2) color = line.Color;
            else if (color != line.Color) mixedBorder = true;
        };
        if (props->getPropertyValue(u"TableBorder2"_ustr) >>= borders) {
            lineColor(borders.TopLine, borders.IsTopLineValid);
            lineColor(borders.BottomLine, borders.IsBottomLineValid);
            lineColor(borders.LeftLine, borders.IsLeftLineValid);
            lineColor(borders.RightLine, borders.IsRightLineValid);
            if (expected.row2 > expected.row1) lineColor(borders.HorizontalLine, borders.IsHorizontalLineValid);
            if (expected.col2 > expected.col1) lineColor(borders.VerticalLine, borders.IsVerticalLineValid);
            const table::BorderLine2 lines[] = {borders.LeftLine, borders.RightLine, borders.BottomLine,
                borders.TopLine, borders.HorizontalLine, borders.VerticalLine};
            const bool valid[] = {bool(borders.IsLeftLineValid), bool(borders.IsRightLineValid), bool(borders.IsBottomLineValid),
                bool(borders.IsTopLineValid), expected.row2 == expected.row1 || borders.IsHorizontalLineValid,
                expected.col2 == expected.col1 || borders.IsVerticalLineValid};
            const char *names[] = {"left", "right", "bottom", "top", "inner-horizontal", "inner-vertical"};
            for (int i = 0; i < 6; ++i) {
                values[[NSString stringWithFormat:@"border.%s", names[i]]] = !valid[i] ? (id)NSNull.null :
                    (id)@(lines[i].LineWidth > 0 || lines[i].OuterLineWidth > 0);
            }
            const int patterns[][6] = {{0,0,0,0,0,0}, {1,1,1,1,1,1}, {1,1,1,1,0,0}, {3,3,3,3,0,0}};
            const char *presets[] = {"all-dashed", "all-solid", "outer-solid-inner-dashed", "outer-thick"};
            for (int preset = 0; preset < 4; ++preset) {
                bool matches = true, mixed = false;
                for (int i = 0; i < 6; ++i) {
                    if ((i == 4 && expected.row2 == expected.row1) || (i == 5 && expected.col2 == expected.col1)) continue;
                    mixed |= !valid[i];
                    const auto width = lines[i].LineWidth;
                    matches &= patterns[preset][i] == 0 ? width == 0 : patterns[preset][i] == 3 ? width >= 100 : width > 0 && width < 100;
                }
                values[[NSString stringWithFormat:@"border.preset.%s", presets[preset]]] = mixed ? (id)NSNull.null : (id)@(matches);
            }
        }
        for (const char *name : {"DiagonalTLBR2", "DiagonalBLTR2"}) {
            table::BorderLine2 line;
            uno::Any value = property(OUString::fromUtf8(name));
            if (value >>= line) {
                lineColor(line, true);
                values[@(name)] = @(line.LineWidth > 0 || line.OuterLineWidth > 0);
            } else { values[@(name)] = NSNull.null; mixedBorder = true; }
        }
        values[@".uno:FrameLineColor"] = mixedBorder || color == -2 ? (id)NSNull.null : (id)@(color);
        NSData *json = [NSJSONSerialization dataWithJSONObject:values options:0 error:nil];
        if (json) result.statesJSON.assign((const char *)json.bytes, json.length);
        result.success = true;
    } catch (...) {
        result.success = false;
    }
    return result;
}

bool updateIOSCalcBorderColor(lok::Document *document, int viewId,
    const IOSCalcTextRange& expected, int color)
{
    using namespace com::sun::star;
    if (color < 0 || color > 0xffffff) return false;
    auto *mutex = comphelper::SolarMutex::get();
    if (!mutex) return false;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (!getIOSCalcFormatStates(document, viewId, expected).success) return false;
    auto *view = SfxLokHelper::getViewOfId(viewId);
    if (!view || !view->GetObjectShell() || view->GetObjectShell()->IsReadOnly()) return false;
    uno::Reference<document::XUndoManager> undo;
    uno::Reference<frame::XModel> model;
    auto *groupUndo = view->GetUndoManager();
    if (!groupUndo) return false;
    bool context = false, locked = false, changed = false;
    try {
        uno::Reference<view::XSelectionSupplier> supplier(view->GetController(), uno::UNO_QUERY_THROW);
        auto range = iosValidatedCalcSelection(supplier->getSelection(), expected);
        if (!range.is()) return false;
        auto cursor = range->getSpreadsheet()->createCursorByRange(range);
        cursor->collapseToMergedArea();
        range.set(cursor, uno::UNO_QUERY_THROW);
        uno::Reference<util::XProtectable> protection(range->getSpreadsheet(), uno::UNO_QUERY_THROW);
        // Use the existing format dispatcher for protected-sheet policy; do not bypass it.
        if (protection->isProtected()) return false;
        model.set(view->GetObjectShell()->GetModel(), uno::UNO_QUERY_THROW);
        uno::Reference<document::XUndoManagerSupplier> manager(model, uno::UNO_QUERY_THROW);
        undo = manager->getUndoManager();
        if (!undo.is() || undo->isLocked()) return false;
        struct Change { uno::Reference<beans::XPropertySet> props; OUString name; uno::Any before, after; };
        std::vector<Change> changes;
        // Format ranges let large uniform selections stay cheap; each area has identical edges.
        uno::Reference<sheet::XCellFormatRangesSupplier> formatRanges(range, uno::UNO_QUERY_THROW);
        uno::Reference<container::XEnumerationAccess> access(formatRanges->getCellFormatRanges(), uno::UNO_QUERY_THROW);
        auto areas = access->createEnumeration();
        while (areas->hasMoreElements()) {
            uno::Reference<beans::XPropertySet> props(areas->nextElement(), uno::UNO_QUERY_THROW);
            for (const char *name : {"TopBorder2", "BottomBorder2", "LeftBorder2", "RightBorder2", "DiagonalTLBR2", "DiagonalBLTR2"}) {
                const OUString property = OUString::fromUtf8(name);
                const uno::Any before = props->getPropertyValue(property);
                table::BorderLine2 line;
                if (!(before >>= line)) return false;
                if ((!line.LineWidth && !line.OuterLineWidth && !line.InnerLineWidth) || line.Color == color) continue;
                line.Color = color;
                changes.push_back({props, property, before, uno::Any(line)});
            }
        }
        if (changes.empty()) return true;
        // UNO enterUndoContext uses ViewShellId(-1); LOK cannot undo that group.
        groupUndo->EnterListAction(u"边框颜色"_ustr, OUString(), 0, view->GetViewShellId()); context = true;
        model->lockControllers(); locked = true;
        for (const auto& change : changes) {
            change.props->setPropertyValue(change.name, change.after);
            changed = true;
            table::BorderLine2 actual, expectedLine;
            if (!(change.props->getPropertyValue(change.name) >>= actual) || !(change.after >>= expectedLine)
                || actual.Color != expectedLine.Color || actual.LineWidth != expectedLine.LineWidth
                || actual.LineStyle != expectedLine.LineStyle || actual.InnerLineWidth != expectedLine.InnerLineWidth
                || actual.OuterLineWidth != expectedLine.OuterLineWidth || actual.LineDistance != expectedLine.LineDistance)
                throw uno::RuntimeException(u"Border color readback failed"_ustr);
        }
        model->unlockControllers(); locked = false;
        groupUndo->LeaveListAction(); context = false;
        return true;
    } catch (...) {
        try { if (locked) model->unlockControllers(); } catch (...) {}
        try { if (context) groupUndo->LeaveListAction(); } catch (...) {}
        try { if (changed && undo.is() && undo->isUndoPossible()) undo->undo(); } catch (...) {}
        return false;
    }
}

int mergeIOSCalcIdenticalCells(lok::Document *document, int viewId, const IOSCalcTextRange& expected)
{
    using namespace com::sun::star;
    auto *mutex = comphelper::SolarMutex::get();
    if (!mutex) return -1;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (!getIOSCalcFormatStates(document, viewId, expected).success) return -1;
    auto *view = SfxLokHelper::getViewOfId(viewId);
    if (!view || !view->GetObjectShell() || view->GetObjectShell()->IsReadOnly()) return -1;
    auto *undo = view->GetUndoManager();
    if (!undo) return -1;
    uno::Reference<frame::XModel> model;
    bool context = false, locked = false;
    int changed = 0;
    const OUString title = u"合并相同单元格"_ustr;
    try {
        uno::Reference<view::XSelectionSupplier> supplier(view->GetController(), uno::UNO_QUERY_THROW);
        auto selected = iosValidatedCalcSelection(supplier->getSelection(), expected);
        if (!selected.is()) return -1;
        auto sheet = selected->getSpreadsheet();
        uno::Reference<util::XProtectable> protection(sheet, uno::UNO_QUERY_THROW);
        if (protection->isProtected()) return -1;
        model = view->GetObjectShell()->GetModel();
        struct Value {
            table::CellContentType type;
            OUString content;
            double number;
            bool operator==(const Value& other) const {
                return type == other.type && (type == table::CellContentType_VALUE
                    ? number == other.number : content == other.content);
            }
        };
        // Enumerate content only: a whole-column selection must not scan a million empty rows.
        std::map<int, std::map<int, Value>> columns;
        uno::Reference<sheet::XCellRangesQuery> query(selected, uno::UNO_QUERY_THROW);
        auto cells = query->queryContentCells(sheet::CellFlags::STRING | sheet::CellFlags::VALUE
            | sheet::CellFlags::DATETIME | sheet::CellFlags::FORMULA)->getCells()->createEnumeration();
        while (cells->hasMoreElements()) {
            auto element = cells->nextElement();
            uno::Reference<table::XCell> cell(element, uno::UNO_QUERY_THROW);
            uno::Reference<sheet::XCellAddressable> addressable(element, uno::UNO_QUERY_THROW);
            const auto address = addressable->getCellAddress();
            Value value{cell->getType(), cell->getFormula(), cell->getValue()};
            if (value.type == table::CellContentType_EMPTY || value.content.isEmpty()) continue;
            columns[address.Column].emplace(address.Row, std::move(value));
        }
        std::vector<uno::Reference<util::XMergeable>> groups;
        for (const auto& column : columns) {
            auto start = column.second.begin();
            while (start != column.second.end()) {
                auto end = start;
                auto next = std::next(end);
                while (next != column.second.end() && next->first == end->first + 1 && next->second == start->second) {
                    end = next++;
                }
                if (start != end) {
                    uno::Reference<util::XMergeable> group(sheet->getCellRangeByPosition(
                        column.first, start->first, column.first, end->first), uno::UNO_QUERY_THROW);
                    // Leave existing merged areas intact; never absorb a partial existing merge.
                    if (!group->getIsMerged()) groups.push_back(group);
                }
                start = next;
            }
        }
        if (groups.empty()) return 0;
        undo->EnterListAction(title, OUString(), 0, view->GetViewShellId()); context = true;
        model->lockControllers(); locked = true;
        for (const auto& group : groups) {
            group->merge(true);
            if (!group->getIsMerged()) throw uno::RuntimeException(u"Merge readback failed"_ustr);
            ++changed;
        }
        model->unlockControllers(); locked = false;
        undo->LeaveListAction(); context = false;
        return changed;
    } catch (...) {
        try { if (locked) model->unlockControllers(); } catch (...) {}
        try { if (context) undo->LeaveListAction(); } catch (...) {}
        try {
            if (changed > 0 && undo->GetUndoActionCount() && undo->GetUndoAction(0)->GetComment() == title)
                undo->Undo();
        } catch (...) {}
        return -1;
    }
}

int coolwsd_server_socket_fd = -1;

LibreOfficeKit *lo_kit;

// vim:set shiftwidth=4 softtabstop=4 expandtab:
