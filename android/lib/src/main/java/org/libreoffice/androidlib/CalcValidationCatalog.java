package org.libreoffice.androidlib;

/**
 * Calc 数据有效性选项（对齐 LO sc/source/ui/dbgui/validate.cxx 与 validationcriteriapage.ui）。
 */
final class CalcValidationCatalog {

    static final class Option {
        final String id;
        final String label;
        final int index;

        Option(String id, String label, int index) {
            this.id = id;
            this.label = label;
            this.index = index;
        }
    }

    /** allow 下拉索引，见 SC_VALIDDLG_ALLOW_*。 */
    static final Option[] ALLOW_OPTIONS = {
            new Option("any", "所有值", 0),
            new Option("whole", "整数", 1),
            new Option("decimal", "小数", 2),
            new Option("date", "日期", 3),
            new Option("time", "时间", 4),
            new Option("range", "单元格区域", 5),
            new Option("list", "列表", 6),
            new Option("textlen", "文本长度", 7),
            new Option("custom", "自定义", 8),
    };

    /** data 比较运算符索引，见 SC_VALIDDLG_DATA_*。 */
    static final Option[] DATA_OPTIONS = {
            new Option("equal", "等于", 0),
            new Option("less", "小于", 1),
            new Option("greater", "大于", 2),
            new Option("eqless", "小于或等于", 3),
            new Option("eqgreater", "大于或等于", 4),
            new Option("notequal", "不等于", 5),
            new Option("validrange", "有效的区域", 6),
            new Option("invalidrange", "无效的区域", 7),
    };

    /** 出错提示 actionCB 索引，见 ScValidErrorStyle。 */
    static final Option[] ERROR_ACTION_OPTIONS = {
            new Option("stop", "停止", 0),
            new Option("warning", "警告", 1),
            new Option("info", "信息", 2),
            new Option("macro", "宏", 3),
            new Option("silent", "默默拒绝", 4),
    };

    static Option findAllowByIndex(int index) {
        for (Option option : ALLOW_OPTIONS) {
            if (option.index == index) {
                return option;
            }
        }
        return ALLOW_OPTIONS[0];
    }

    static Option findAllowById(String id) {
        if (id == null) {
            return ALLOW_OPTIONS[0];
        }
        for (Option option : ALLOW_OPTIONS) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        return ALLOW_OPTIONS[0];
    }

    static Option findDataByIndex(int index) {
        for (Option option : DATA_OPTIONS) {
            if (option.index == index) {
                return option;
            }
        }
        return DATA_OPTIONS[0];
    }

    static Option findDataById(String id) {
        if (id == null) {
            return DATA_OPTIONS[0];
        }
        for (Option option : DATA_OPTIONS) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        return DATA_OPTIONS[0];
    }

    static Option findErrorActionByIndex(int index) {
        for (Option option : ERROR_ACTION_OPTIONS) {
            if (option.index == index) {
                return option;
            }
        }
        return ERROR_ACTION_OPTIONS[0];
    }

    static Option findErrorActionById(String id) {
        if (id == null) {
            return ERROR_ACTION_OPTIONS[0];
        }
        for (Option option : ERROR_ACTION_OPTIONS) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        return ERROR_ACTION_OPTIONS[0];
    }

    /** 按中文 label 匹配（读对话框时 JSDialog combo 可能返回文本而非 index）。 */
    static Option findAllowByLabel(String label) {
        return findOptionByLabel(ALLOW_OPTIONS, label);
    }

    static Option findDataByLabel(String label) {
        return findOptionByLabel(DATA_OPTIONS, label);
    }

    static Option findErrorActionByLabel(String label) {
        return findOptionByLabel(ERROR_ACTION_OPTIONS, label);
    }

    private static Option findOptionByLabel(Option[] options, String label) {
        if (label == null) {
            return options[0];
        }
        String needle = label.trim();
        if (needle.isEmpty()) {
            return options[0];
        }
        // 优先精确匹配中文 label
        for (Option option : options) {
            if (needle.equals(option.label)) {
                return option;
            }
        }
        // 其次匹配英文内部 id（CO 可能返回 id 而非显示文本）
        for (Option option : options) {
            if (needle.equalsIgnoreCase(option.id)) {
                return option;
            }
        }
        // 数字 index
        try {
            int index = Integer.parseInt(needle);
            for (Option option : options) {
                if (option.index == index) {
                    return option;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        // 子串（如 "小于或等于" 可能被截断）
        for (Option option : options) {
            if (needle.contains(option.label) || option.label.contains(needle)) {
                return option;
            }
        }
        return options[0];
    }

    static boolean needsDataOperator(int allowIndex) {
        return allowIndex != 0;
    }

    static boolean isListAllow(int allowIndex) {
        return allowIndex == 6;
    }

    static boolean isRangeAllow(int allowIndex) {
        return allowIndex == 5;
    }

    static boolean isCustomAllow(int allowIndex) {
        return allowIndex == 8;
    }

    static boolean needsBetweenValues(int dataIndex) {
        return dataIndex == 6 || dataIndex == 7;
    }

    static boolean showDropdownListToggle(int allowIndex) {
        return allowIndex == 5 || allowIndex == 6;
    }

    private CalcValidationCatalog() {
    }
}
