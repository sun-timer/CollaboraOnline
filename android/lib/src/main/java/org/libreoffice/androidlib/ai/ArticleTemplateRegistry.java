package org.libreoffice.androidlib.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArticleTemplateRegistry {
    private static final String[] CATEGORIES = {
            "通知类", "申请类", "证明类", "营销类"
    };

    private static final ArticleTemplate[] ALL = {
            // 通知类
            tmpl("general_notice", "通知类", "通用通知", "生成通用通知",
                    "请撰写一则通知，通知的主要内容为{变量1}，通知时间是{变量2}。",
                    v("通知主要内容", "公司今晚聚餐"),
                    v("通知时间", "2025年01月01日")),
            tmpl("meeting_notice", "通知类", "会议通知", "生成会议通知",
                    "请撰写一则会议通知，通知的主要内容为{变量1}，会议时间是{变量2}，参会人员包括{变量3}。",
                    v("会议主要内容", "2025年研发计划"),
                    v("会议时间", "2025年01月01日 17:00"),
                    v("参会人员", "软件研发人员")),
            tmpl("holiday_notice", "通知类", "放假通知", "生成放假通知",
                    "请撰写一则放假通知，假期名称为{变量1}，接收方是{变量2}，发送方是{变量3}，放假时间是{变量4}。",
                    v("假期名称", "元旦节"),
                    v("接收方", "全体员工"),
                    v("发送方", "橙子云计算（深圳）有限公司"),
                    v("放假时间", "2025年01月01日")),
            tmpl("interview_notice", "通知类", "面试通知", "生成面试通知",
                    "请撰写一则面试通知，面试人员是{变量1}，面试时间为{变量2}，面试地点是{变量3}，面试单位为{变量4}。",
                    v("面试人员", "小王"),
                    v("面试时间", "2025年01月01日 17:00"),
                    v("面试地点", "名优大厦A座1区101"),
                    v("面试单位", "橙子云计算")),
            tmpl("activity_notice", "通知类", "活动通知", "生成活动通知",
                    "请撰写一则活动通知，活动主题是{变量1}，活动时间是{变量2}，活动地点为{变量3}。",
                    v("活动主题", "员工羽毛球大赛"),
                    v("活动时间", "2025年01月01日 17:00"),
                    v("活动地点", "羽毛球馆")),
            tmpl("training_notice", "通知类", "培训通知", "生成培训通知",
                    "请撰写一则培训通知，培训主要内容为{变量1}，培训人员是{变量2}，培训日期是{变量3}。",
                    v("培训主要内容", "如何使用AI Office提效"),
                    v("培训人员", "全体员工"),
                    v("培训日期", "2025年01月01日 17:00")),
            // 申请类
            tmpl("general_apply", "申请类", "通用申请", "生成通用申请",
                    "请撰写一则申请，申请人是{变量1}，申请事项是{变量2}，申请时间是{变量3}。",
                    v("申请人", "小王"),
                    v("申请事项", "外出参加会议"),
                    v("申请时间", "2025年01月01日")),
            tmpl("leave_apply", "申请类", "请假申请", "生成请假申请",
                    "请撰写一则请假条，请假人为{变量1}，请假原因是{变量2}，请假天数为{变量3}，请假起始日期是{变量4}。",
                    v("请假人", "小王"),
                    v("请假原因", "身体不适"),
                    v("请假天数", "3天"),
                    v("请假起始日期", "2025年01月01日")),
            tmpl("resign_apply", "申请类", "离职申请", "生成离职申请",
                    "请撰写一则离职申请，申请人是{变量1}，离职原因是{变量2}，离职时间是{变量3}。",
                    v("申请人", "小王"),
                    v("离职原因", "身体长期不适"),
                    v("离职时间", "2025年01月01日")),
            // 证明类
            tmpl("general_cert", "证明类", "通用证明", "生成通用证明",
                    "请撰写一则证明，被证明人是{变量1}，证明主要内容是{变量2}，证明单位为{变量3}，证明时间是{变量4}。",
                    v("被证明人", "小王"),
                    v("证明主要内容", "小王是公司的员工"),
                    v("证明单位", "橙子云计算（深圳）有限公司"),
                    v("证明时间", "2025年01月01日")),
            tmpl("work_cert", "证明类", "工作证明", "生成工作证明",
                    "请撰写一则工作证明，被证明人是{变量1}，工作时间是{变量2}，工作单位是{变量3}，工作岗位是{变量4}。",
                    v("被证明人", "小王"),
                    v("工作时间", "2020年01月01日至2025年01月01日"),
                    v("工作单位", "橙子云计算（深圳）有限公司"),
                    v("工作岗位", "软件研发工程师")),
            tmpl("income_cert", "证明类", "收入证明", "生成收入证明",
                    "请撰写一则收入证明，被证明人是{变量1}，收入为{变量2}，工作单位是{变量3}，工作岗位是{变量4}。",
                    v("被证明人", "小王"),
                    v("收入", "年收入10万元"),
                    v("工作单位", "橙子云计算（深圳）有限公司"),
                    v("工作岗位", "软件研发工程师")),
            tmpl("resign_cert", "证明类", "离职证明", "生成离职证明",
                    "请撰写一则离职证明，被证明人是{变量1}，离职原因为{变量2}，离职时间是{变量3}，证明单位为{变量4}，证明时间是{变量5}。",
                    v("被证明人", "小王"),
                    v("离职原因", "员工个人原因"),
                    v("离职时间", "2025年01月01日"),
                    v("证明单位", "橙子云计算（深圳）有限公司"),
                    v("证明时间", "2025年01月01日")),
            // 营销类
            tmpl("xiaohongshu", "营销类", "小红书种草文", "生成小红书种草文",
                    "请撰写一篇小红书种草文，种草对象是{变量1}，目标受众是{变量2}，核心卖点是{变量3}，文章长度{变量4}，使用{变量5}的文案风格，",
                    v("种草对象", "最新复古游戏掌机"),
                    v("目标受众", "喜欢游戏机的年轻人"),
                    v("核心卖点", "畅玩复古游戏"),
                    v("文章长度", "500字左右"),
                    v("文案风格", "幽默风趣")),
            tmpl("ad_soft", "营销类", "产品广告软文", "生成产品广告软文",
                    "请撰写一篇产品广告软文，产品名称是{变量1}，品牌是{变量2}，核心卖点是{变量3}，目标受众是{变量4}，投放平台是{变量5}，营销节点是{变量6}，文案风格是{变量7}",
                    v("产品名称", "最新复古游戏掌机"),
                    v("品牌", "香橙派"),
                    v("核心卖点", "畅玩复古游戏"),
                    v("目标受众", "爱玩游戏的年轻人"),
                    v("投放平台", "微博"),
                    v("营销节点", "情人节"),
                    v("文案风格", "幽默风趣")),
            tmpl("douyin_script", "营销类", "抖音视频脚本", "生成抖音视频脚本",
                    "请撰写一篇抖音视频脚本，视频的主题内容是{变量1}，目标受众是{变量2}，视频风格是{变量3}，视频时长是{变量4}",
                    v("主题内容", "旅游攻略"),
                    v("目标受众", "旅游爱好者"),
                    v("视频风格", "搞笑幽默"),
                    v("视频时长", "三分钟左右")),
    };

    private static final Map<String, ArticleTemplate> BY_KEY;
    private static final Map<String, List<ArticleTemplate>> BY_CATEGORY;

    static {
        Map<String, ArticleTemplate> byKey = new LinkedHashMap<>();
        Map<String, List<ArticleTemplate>> byCategory = new LinkedHashMap<>();
        for (String cat : CATEGORIES) {
            byCategory.put(cat, new ArrayList<>());
        }
        for (ArticleTemplate t : ALL) {
            byKey.put(t.key, t);
            List<ArticleTemplate> list = byCategory.get(t.category);
            if (list != null) {
                list.add(t);
            }
        }
        BY_KEY = Collections.unmodifiableMap(byKey);
        BY_CATEGORY = Collections.unmodifiableMap(byCategory);
    }

    private ArticleTemplateRegistry() {}

    private static ArticleTemplate.Variable v(String label, String hint) {
        return new ArticleTemplate.Variable(label, hint);
    }

    private static ArticleTemplate tmpl(String key, String category, String subTypeLabel,
            String generateText, String promptTemplate, ArticleTemplate.Variable... variables) {
        return new ArticleTemplate(key, category, subTypeLabel, generateText, promptTemplate, variables);
    }

    public static String[] getCategories() {
        return CATEGORIES.clone();
    }

    public static List<ArticleTemplate> getByCategory(String category) {
        List<ArticleTemplate> list = BY_CATEGORY.get(category);
        return list == null ? Collections.emptyList() : list;
    }

    public static ArticleTemplate findByKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }
}
