package org.libreoffice.androidlib;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据有效性宏目录：jsdialog 拦截 MacroSelectorDialog 收集的真实宏树。
 * 每条宏含 scope（document/application）、库、模块、方法名、URI。
 */
final class CalcValidationMacroCatalog {

    /** 单个宏项。 */
    static final class MacroItem {
        final boolean documentScope;
        final String scopeLabel;
        final String library;
        final String module;
        final String name;
        final String uri;

        MacroItem(boolean documentScope, String scopeLabel, String library, String module,
                String name, String uri) {
            this.documentScope = documentScope;
            this.scopeLabel = scopeLabel;
            this.library = library;
            this.module = module;
            this.name = name;
            this.uri = uri;
        }
    }

    interface Callback {
        void onCatalogLoaded(CalcValidationMacroCatalog catalog);
    }

    private final List<MacroItem> items = new ArrayList<>();

    void add(MacroItem item) {
        if (item != null) {
            items.add(item);
        }
    }

    List<MacroItem> items() {
        return items;
    }

    List<MacroItem> forScope(boolean documentScope) {
        List<MacroItem> result = new ArrayList<>();
        for (MacroItem item : items) {
            if (item.documentScope == documentScope) {
                result.add(item);
            }
        }
        return result;
    }

    boolean isEmpty() {
        return items.isEmpty();
    }
}
