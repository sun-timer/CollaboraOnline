package org.libreoffice.androidapp.feedback;

/**
 * V1.2 反馈类型（与接口说明书 5.1 一致）。UI 文案可本地化，提交必须用此处常量。
 */
public final class FeedbackTypes {

    /** 与表单 Chip0…Chip3 顺序一致 */
    public static final String[] API_LABELS = {
            "功能异常",
            "产品建议与改进",
            "使用体验问题",
            "其他"
    };

    private FeedbackTypes() {
    }

    public static String apiLabelForChipIndex(int chipIndex) {
        if (chipIndex < 0 || chipIndex >= API_LABELS.length) {
            return "";
        }
        return API_LABELS[chipIndex];
    }
}
