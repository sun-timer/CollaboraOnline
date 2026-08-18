# UI 适配跟踪清单

> **用途**：排查回归、真机验收、记录 open issue。详细背景见 [`mobile-ui-adaptation-report.md`](./mobile-ui-adaptation-report.md)。  
> **最后更新**：2026-08-18  
> **维护约定**：修完一项把 `[ ]` 改 `[x]`，新增问题写在 §6，并在 §9 变更记录留一行。

---

## 0. 快速定位（排查时先看这里）

| 现象 | 优先查 | 常见原因 |
|------|--------|----------|
| 底部按钮被三键/手势区挡住 | `SystemUiHelper.resolveNavigationBottomInset` → 对应 surface 是否接 Safe Area | 只修了 BottomToolbar，FAB/Dialog/Footer 未接 |
| 键盘弹出后底栏错位/截断 | `BottomToolbarController.applyImeState` | margin/padding 切换；IME inset 为 0 |
| 华为曲面屏底部仍贴边 | `lolib_inset_gesture_min`(20dp) + `lolib_inset_bottom_safe_extra`(8dp) | 系统报 inset=0，需 fallback |
| 平板弹窗过宽/贴边 | `ResponsiveUiHelper` + `values-sw600dp/dimens.xml` | 未走 `applyAdaptiveSheetWindow` |
| 平板首页一行两个文件 | `LibreOfficeUIActivity` 列表 LayoutManager | 误用 Grid 双列（应 Linear 单列） |
| 抽屉 → 基础模型全屏灰 | 平板是否走 `AiModelConfigActivity`；overlay 是否 `CONSUMED` inset | 抽屉内 overlay + 重复 inset |
| 功能面板 Tab 横杠错位 | `FunctionPanelTabIndicatorHelper` | 应用 `getLocationInWindow` 在曲面/分屏不准 |
| 选区/Calc 浮层被底栏挡 | `DocumentOverlayInsets` + `getReservedBottomHeightPx` | bottomReserved 未含 toolbar padding |
| Web 文档区被挡 | `LOActivity` WebView listener | 原生栏已避让；Web 弹层见 P5 |

**核心代码入口**

| 模块 | 路径 |
|------|------|
| Insets 统一 | `android/lib/.../SystemUiHelper.java` |
| Safe Area 快照 | `android/lib/.../SafeAreaInsets.java` |
| 浮层 clamp | `android/lib/.../DocumentOverlayInsets.java` |
| 文档页 | `android/lib/.../LOActivity.java` → `setupSystemChrome` / `applyDocumentPageSafeArea` |
| 底栏 | `android/lib/.../BottomToolbarController.java` |
| BottomSheet | `android/lib/.../BottomSheetAnchorHelper.java` |
| 首页 | `android/app/.../LibreOfficeUIActivity.java` |
| 尺寸断点 | `android/app/.../ResponsiveUiHelper.java` |
| 平板 dimens | `android/app/.../values-sw600dp/dimens.xml` |

---

## 1. 尺寸策略（弹窗 / 图标 / 组件要不要「自适应」）

**结论：不要整页按比例缩放（H5 式 vw/rem）；要「固定设计 token + 约束布局 + 断点微调」。**

### 1.1 分类规则

| 类型 | 是否随屏宽缩放 | 推荐做法 | 本项目现状 |
|------|----------------|----------|------------|
| **图标（视觉）** | ❌ 不缩放 | 固定 dp：24 / 32 / 48；来自 Figma ÷2 | 多数已固定，如 toolbar 24dp |
| **触控热区** | ❌ 不缩放 | ≥ 48dp；视觉 24dp 居中在 48dp 按钮内 | Quick Action 已 64×56 min |
| **字号** | ❌ 不用 px 缩放 | **sp** + 系统字体缩放；标题 16–20sp | 混用 dp/sp，逐步统一 sp |
| **Toolbar 高度** | ❌ 不缩放 | 顶 56dp、底 82dp + **inset 额外占位** | ✅ 已分离 |
| **弹窗 / BottomSheet 宽度** | ✅ 约束自适应 | `match_parent` + `maxWidth` + 左右 margin；平板 `sw600dp` 加大 maxWidth | ✅ `ResponsiveUiHelper` |
| **列表行 / 卡片** | ✅ 横向撑满 | `match_parent` + 左右 margin；**不要**固定 358dp 宽 | 首页最近文件已单列 match_parent |
| **抽屉宽度** | ⚪ 断点 | 手机全宽；平板 `ai_drawer_panel_width` 400dp | sw600 已覆盖 |
| **间距 / 圆角** | ⚪ Token | 4/8/12/16/24 dp，少写 13/17/19 | 部分魔法数待 P2 收敛 |

### 1.2 三种「自适应」不要混用

```text
✅ Adaptive Layout（推荐）
   约束 + wrap_content + min/max + Window Size Class 断点
   例：dialog width = min(335dp, screenWidth - 40dp)

✅ Safe Area（推荐）
   WindowInsets 额外占位，设计高度不变
   例：82dp 底栏 + paddingBottom(navInset)

❌ Proportional Scaling（不要）
   按屏宽比例放大图标/字号/弹窗
   例：icon = screenWidth * 0.06  → 大屏过大、小屏过小，且非 Material 做法
```

### 1.3 弹窗专项

| 场景 | Compact（手机） | Medium+（平板） |
|------|-----------------|-----------------|
| 呈现 | BottomSheet 贴底全宽 | 居中卡片，`bottom_sheet_max_width` 560dp |
| 宽度 | `min(dialog_content_max_width, screen - shellPadding×2)` | sw600：`dialog_content_max_width` 536dp |
| 高度 | `wrap_content` + `maxHeight`（长列表） | 同左，必要时 `local_model_list_max_height` |
| 系统栏 | `SystemUiHelper.applyCenteredDialogSafeInsets` | 同左 |

**不需要改**：弹窗内按钮高度 35–48dp、图标 20–24dp — 保持 Figma token，只改**容器宽度**和**安全区**。

### 1.4 待收敛项（尺寸相关，非阻塞）

- [ ] 统一 spacing/icon token 命名（`spacing_16`、`icon_24`、`touch_min_48`）
- [ ] 审查仍写死 `layout_width="335dp"` 的布局，改为 0dp 约束 + `@dimen/dialog_content_max_width`
- [ ] 首页 `home_file_icon_size` 39dp 等非 8 倍数 — 可保留视觉稿，新组件优先 8 网格

---

## 2. P0 — 系统栏 / Insets（当前重点）

| ID | 任务 | 状态 | 验证要点 |
|----|------|------|----------|
| P0-a | `SystemUiHelper` + `SafeAreaInsets` + `trackSafeAreaChanges` | [x] | 文档页/首页 inset 变化时 FAB、抽屉 footer 更新 |
| P0-b | AI FAB `clampAiFabPosition` | [x] | 拖到底不进入 nav 区 |
| P0-c | Selection / Calc / Impress 浮层 `DocumentOverlayInsets` | [x] | 浮层不被 82dp 底栏挡 |
| P0-d | Dialog `applyCenteredDialogSafeInsets` | [x] | 曲面屏底部按钮可点 |
| P0-e | **真机回归矩阵** | [ ] | 见 §4 |
| P0-f | `doc_bottom_nav_spacer` 与 padding 二选一 | [ ] | XML 有 spacer 但未调用 `applyBottomNavSpacer` |
| P0-g | 首页 inset 双路径收拢 | [ ] | coordinator + RecyclerView 各一套 listener |
| P0-h | WebView `CONSUMED` 策略文档化 | [x] | 有意不传 inset 给 Web；P5 再评估 bridge |

---

## 3. P1–P5 进度

| 级别 | 主题 | 进度 | 下一步 |
|------|------|------|--------|
| P1 | Safe Area 全覆盖 | ~70% | 首页单入口；Quick Action overlay 审计 |
| P2 | Design Tokens | ~20% | 新代码强制 `@dimen/spacing_*` |
| P3 | XML 约束布局 | ~40% | 去掉屏宽耦合 width |
| P4 | Window Size Class | ~40% | 分屏时改读 `WindowMetrics`（可选） |
| P5 | Android → WebView inset | 0% | Native 栏稳定后再做 |

---

## 4. 真机验收矩阵（P0-e）

复制到 issue / 飞书，测完打勾。

### 4.1 设备 × 导航模式

| 设备类型 | 三键导航 | 手势导航 | 横屏 | 备注 |
|----------|----------|----------|------|------|
| 小屏手机 ~360dp | [ ] | [ ] | [ ] | |
| 基准手机 ~390dp | [ ] | [ ] | [ ] | Figma 验收机 |
| 华为曲面 / 大屏手机 | [ ] | [ ] | [ ] | inset=0 fallback |
| 平板 sw≥600 | [ ] | [ ] | [ ] | 弹窗居中、列表单列 |

### 4.2 页面 × 检查项

| 页面 | Status 不挡内容 | Nav 不挡按钮 | 键盘不挡输入 | 弹窗可点 | 与 Figma 一致（safe 区内） |
|------|-----------------|--------------|--------------|----------|---------------------------|
| 首页 | [ ] | [ ] | [ ] | [ ] | [ ] |
| 首页抽屉 | [ ] | [ ] | — | — | [ ] |
| 基础模型（手机 overlay） | [ ] | [ ] | [ ] | — | [ ] |
| 基础模型（平板 Activity） | [ ] | [ ] | [ ] | — | [ ] |
| 文档页顶/底栏 | [ ] | [ ] | [ ] | — | [ ] |
| 功能面板 BottomSheet | [ ] | [ ] | [ ] | — | [ ] |
| AI 面板 | [ ] | [ ] | [ ] | — | [ ] |
| 选区菜单 / Calc 浮层 | [ ] | [ ] | — | — | [ ] |
| 重命名/确认类 Dialog | [ ] | [ ] | [ ] | [ ] | [ ] |

---

## 5. 已知 Open Issues

| # | 描述 | 严重性 | 关联 | 状态 |
|---|------|--------|------|------|
| O-1 | `doc_bottom_nav_spacer` 未接线，与 padding 方案重复 | 低 | `lolib_activity_main.xml` | open |
| O-2 | 首页 RecyclerView 独立 inset listener | 低 | `LibreOfficeUIActivity.setupHomeContentInsets` | open |
| O-3 | 部分 Dialog 未走 `applyCenteredDialogSafeInsets` | 中 | 各 `AlertDialog.show()` | 待审计 |
| O-4 | `ResponsiveUiHelper` 用 `smallestScreenWidthDp` 非当前 window width | 低 | 分屏场景 | 可接受 |
| O-5 | Web 层无 safe-area CSS | 低 | P5 | deferred |

---

## 6. 回归记录（测出问题写这里）

| 日期 | 设备 | 现象 | 根因 | 修复 PR/commit | 复测 |
|------|------|------|------|----------------|------|
| 2026-08-18 | 平板 | 首页一行两个文件 | Grid 双列 | 改回 LinearLayoutManager | [ ] |
| 2026-08-18 | 平板 | 基础模型灰屏 | 抽屉 overlay + inset | 平板改 Activity | [ ] |
| | | | | | |

---

## 7. 排查命令 / 日志 Tag

```bash
# 过滤 inset / 底栏 / 安全区相关日志
adb logcat -s BottomToolbarController LOActivity SystemUiHelper
```

关注 log：

- `bottom_toolbar_mode` — 编辑/预览切换
- `applyImeState` — 键盘时 margin/padding
- inset 为 0 时是否走了 `lolib_inset_gesture_min` / `lolib_inset_nav_bar_min`

---

## 8. 相关文档

- [mobile-ui-adaptation-report.md](./mobile-ui-adaptation-report.md) — 架构分析与 P0–P5 路线图  
- [Android Edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge)  
- [Window Size Class](https://developer.android.com/develop/ui/views/layout/use-window-size-classes)

---

## 9. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-08-18 | 新建跟踪清单：快速定位表、尺寸策略、P0 勾选、真机矩阵、Open Issues 模板 |
