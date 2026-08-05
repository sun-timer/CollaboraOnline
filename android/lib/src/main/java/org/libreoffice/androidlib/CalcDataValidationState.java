package org.libreoffice.androidlib;

/**
 * Calc 数据有效性设置快照（供自定义 UI 与 CO 对话框注入共用）。
 */
final class CalcDataValidationState {

    int allowIndex = 0;
    int dataIndex = 0;
    String minValue = "";
    String maxValue = "";
    String listEntries = "";
    boolean allowEmpty = true;
    boolean showDropdownList = true;
    boolean caseSensitive = false;
    boolean sortAscending = true;

    boolean showInputHelp = false;
    String inputHelpTitle = "";
    String inputHelpText = "";

    boolean showErrorAlert = true;
    int errorActionIndex = 0;
    String errorTitle = "";
    String errorMessage = "";
    String macroUrl = "";

    CalcValidationCatalog.Option allowOption() {
        return CalcValidationCatalog.findAllowByIndex(allowIndex);
    }

    CalcValidationCatalog.Option dataOption() {
        return CalcValidationCatalog.findDataByIndex(dataIndex);
    }

    CalcValidationCatalog.Option errorActionOption() {
        return CalcValidationCatalog.findErrorActionByIndex(errorActionIndex);
    }
}
