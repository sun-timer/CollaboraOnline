/* Copyright the Collabora Online contributors. SPDX-License-Identifier: MPL-2.0 */
#pragma once

#include <algorithm>
#include <vector>
#include <LibreOfficeKit/LibreOfficeKit.hxx>
#include <comphelper/solarmutex.hxx>
#include <sfx2/lokhelper.hxx>
#include <sfx2/viewsh.hxx>
#include <sfx2/objsh.hxx>
#include <com/sun/star/drawing/XDrawView.hpp>
#include <com/sun/star/drawing/XDrawPage.hpp>
#include <com/sun/star/beans/XPropertySet.hpp>

// DisplayMasterBackground's UNO dispatch always toggles, and LOK does not
// publish its boolean state. Clear the page's overriding fill and set master
// visibility together, rather than racing a socket UNO command with this edit.
inline bool setMobileImpressMasterBackground(lok::Document* document, int viewId,
                                            int part, bool visible)
{
    using namespace com::sun::star;
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
    try {
        uno::Reference<drawing::XDrawView> draw(view->GetController(), uno::UNO_QUERY_THROW);
        uno::Reference<beans::XPropertySet> page(draw->getCurrentPage(), uno::UNO_QUERY_THROW);
        // A visible master still sits behind a local solid/gradient/bitmap fill.
        // SdDrawPage requires a typed null XPropertySet (an empty Any is invalid).
        const uno::Reference<beans::XPropertySet> noBackground;
        page->setPropertyValue(u"Background"_ustr, uno::Any(noBackground));
        uno::Reference<beans::XPropertySet> remainingBackground;
        if ((page->getPropertyValue(u"Background"_ustr) >>= remainingBackground)
            && remainingBackground.is()) return false;
        bool current = false;
        if ((page->getPropertyValue(u"IsBackgroundVisible"_ustr) >>= current) && current == visible)
            return true;
        page->setPropertyValue(u"IsBackgroundVisible"_ustr, uno::Any(visible));
        return true;
    } catch (const uno::Exception&) {
        return false;
    }
}
