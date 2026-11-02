/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
#pragma once

#include <algorithm>
#include <array>
#include <memory>
#include <vector>
#include <LibreOfficeKit/LibreOfficeKit.hxx>
#include <comphelper/solarmutex.hxx>
#include <sfx2/lokhelper.hxx>
#include <sfx2/viewsh.hxx>
#include <sfx2/objsh.hxx>
#include <com/sun/star/drawing/XDrawView.hpp>
#include <com/sun/star/drawing/XDrawPage.hpp>
#include <com/sun/star/drawing/FillStyle.hpp>
#include <com/sun/star/beans/XPropertySet.hpp>
#include <svx/svdpage.hxx>
#include <svx/svdmodel.hxx>
#include <svx/svdundo.hxx>
#include <svx/svdsob.hxx>
#include <svx/unoapi.hxx>
#include <svx/xdef.hxx>
#include <svx/xfillit0.hxx>
#include <rtl/ref.hxx>
#include <rtl/ustring.hxx>
#include <svl/itemset.hxx>

namespace mobile_impress_background_detail
{
inline constexpr std::array<sal_uInt16, 1> FillWhichIds = { XATTR_FILLSTYLE };

inline SfxItemSet captureFillState(SdrPage& page)
{
    return SfxItemSet(page.getSdrPageProperties().GetItemSet());
}

inline void restoreFillState(SdrPage& page, const SfxItemSet& state)
{
    auto& properties = page.getSdrPageProperties();
    for (sal_uInt16 which : FillWhichIds)
    {
        const SfxPoolItem* item = nullptr;
        if (state.GetItemState(which, false, &item) == SfxItemState::SET && item)
            properties.PutItem(*item);
        else
            properties.ClearItem(which);
    }
    page.ActionChanged();
}

inline bool fillStateChanged(const SfxItemSet& before, const SfxItemSet& after)
{
    for (sal_uInt16 which : FillWhichIds)
    {
        const SfxPoolItem* oldItem = nullptr;
        const SfxPoolItem* newItem = nullptr;
        const auto oldState = before.GetItemState(which, false, &oldItem);
        const auto newState = after.GetItemState(which, false, &newItem);
        if (oldState != newState || (oldItem && newItem && !(*oldItem == *newItem)))
            return true;
    }
    return false;
}

class BackgroundUndo final : public SdrUndoAction
{
    rtl::Reference<SdrPage> m_page;
    SfxItemSet m_oldFill;
    SfxItemSet m_newFill;
    SdrLayerIDSet m_oldVisibleLayers;
    SdrLayerIDSet m_newVisibleLayers;

    void restore(const SfxItemSet& fill, const SdrLayerIDSet& layers)
    {
        restoreFillState(*m_page, fill);
        if (m_page->TRG_HasMasterPage())
        {
            m_page->TRG_SetMasterPageVisibleLayers(layers);
            m_page->ActionChanged();
        }
    }

public:
    BackgroundUndo(SdrPage& page, const SfxItemSet& oldFill, const SfxItemSet& newFill,
                   const SdrLayerIDSet& oldLayers, const SdrLayerIDSet& newLayers)
        : SdrUndoAction(page.getSdrModelFromSdrPage())
        , m_page(&page)
        , m_oldFill(oldFill)
        , m_newFill(newFill)
        , m_oldVisibleLayers(oldLayers)
        , m_newVisibleLayers(newLayers)
    {
    }

    void Undo() override { restore(m_oldFill, m_oldVisibleLayers); }
    void Redo() override { restore(m_newFill, m_newVisibleLayers); }
    OUString GetComment() const override { return u"Slide background"_ustr; }
};
}

// "None" and "Use slide background" both clear the slide's local fill.
// They differ only in the master background layer; master decorations remain visible.
inline bool setMobileImpressMasterBackground(lok::Document* document, int viewId,
                                            int part, bool visible, bool* changed = nullptr)
{
    using namespace com::sun::star;
    if (changed) *changed = false;
    auto* mutex = comphelper::SolarMutex::get();
    if (!document || !mutex || viewId < 0 || part < 0) return false;
    osl::Guard<comphelper::SolarMutex> guard(mutex);
    if (document->getDocumentType() != LOK_DOCTYPE_PRESENTATION) return false;
    std::vector<int> ids(std::max(1, document->getViewsCount()));
    if (!document->getViewIds(ids.data(), ids.size())
        || std::find(ids.begin(), ids.end(), viewId) == ids.end()) return false;
    auto* view = SfxLokHelper::getViewOfId(viewId);
    if (!view || !view->GetObjectShell() || view->GetObjectShell()->IsReadOnly()) return false;
    struct RestoreView {
        lok::Document* document;
        int previous;
        ~RestoreView() { document->setView(previous); }
    } restore{document, document->getView()};
    document->setView(viewId);
    if (document->getPart() != part) return false;

    try
    {
        uno::Reference<drawing::XDrawView> draw(view->GetController(), uno::UNO_QUERY_THROW);
        uno::Reference<drawing::XDrawPage> drawPage = draw->getCurrentPage();
        if (!drawPage.is()) return false;
        SdrPage* sdrPage = GetSdrPageFromXDrawPage(drawPage);
        if (!sdrPage) return false;
        uno::Reference<beans::XPropertySet> page(drawPage, uno::UNO_QUERY_THROW);
        const auto oldFill = mobile_impress_background_detail::captureFillState(*sdrPage);
        const SdrLayerIDSet oldLayers = sdrPage->TRG_HasMasterPage()
            ? sdrPage->TRG_GetMasterPageVisibleLayers() : SdrLayerIDSet();
        bool committed = false;
        struct Rollback {
            SdrPage* page;
            const SfxItemSet& fill;
            const SdrLayerIDSet& layers;
            bool& committed;
            ~Rollback()
            {
                if (committed) return;
                mobile_impress_background_detail::restoreFillState(*page, fill);
                if (page->TRG_HasMasterPage()) page->TRG_SetMasterPageVisibleLayers(layers);
                page->ActionChanged();
            }
        } rollback{sdrPage, oldFill, oldLayers, committed};

        auto& properties = sdrPage->getSdrPageProperties();
        properties.ClearItem(XATTR_FILLSTYLE);
        properties.PutItem(XFillStyleItem(drawing::FillStyle_NONE));
        page->setPropertyValue(u"IsBackgroundVisible"_ustr, uno::Any(visible));
        sdrPage->ActionChanged();

        bool actualVisible = false;
        const bool hasVisibleState = page->getPropertyValue(u"IsBackgroundVisible"_ustr) >>= actualVisible;
        const auto& finalItems = sdrPage->getSdrPageProperties().GetItemSet();
        const bool hasFillStyle = finalItems.GetItemState(XATTR_FILLSTYLE, false) == SfxItemState::SET;
        const bool isNone = hasFillStyle && finalItems.Get(XATTR_FILLSTYLE).GetValue() == drawing::FillStyle_NONE;
        if (!hasVisibleState || actualVisible != visible || !isNone)
            return false;

        const auto newFill = mobile_impress_background_detail::captureFillState(*sdrPage);
        const SdrLayerIDSet newLayers = sdrPage->TRG_HasMasterPage()
            ? sdrPage->TRG_GetMasterPageVisibleLayers() : SdrLayerIDSet();
        const bool fillChanged = mobile_impress_background_detail::fillStateChanged(oldFill, newFill);
        const bool layersChanged = oldLayers != newLayers;
        if (fillChanged || layersChanged)
        {
            sdrPage->getSdrModelFromSdrPage().AddUndo(
                std::make_unique<mobile_impress_background_detail::BackgroundUndo>(
                    *sdrPage, oldFill, newFill, oldLayers, newLayers));
            if (changed) *changed = true;
        }
        committed = true;
        return true;
    }
    catch (const uno::Exception&)
    {
        return false;
    }
}
