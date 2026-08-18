# Android 移动端 UI 适配分析报告

> 文档版本：2026-08-18  
> 适用范围：`CollaboraOnline/android` 原生壳 + WebView 内 Collabora 浏览器 UI  
> 状态：**分析落盘**，作为后续 P0–P5 改进路线图基线  
> **跟踪排查**：[ui-adaptation-tracker.md](./ui-adaptation-tracker.md)（验收矩阵、Open Issues、回归记录）

---

## 1. 执行摘要

本仓库 Android 端**并非未做适配**：已在 `LOActivity`、`BottomToolbarController`、`BottomSheetAnchorHelper` 等模块中处理 `WindowInsets`（导航栏、键盘、手势区等）。

当前主要问题不是「完全没有响应式」，而是：

1. **系统栏 inset 处理不对称** — 文档页底栏链路较完整，顶栏/抽屉/弹窗/FAB 覆盖不一致  
2. **edge-to-edge 引入后暴露结构问题** — 固定高度顶栏 + inset padding 会导致内容被裁切（首页头像/搜索框）  
3. **「BottomToolbar 处理了」≠「全页底部交互安全」** — FAB、抽屉保存、BottomSheet 等需逐 surface 接入 Safe Area  
4. **Web 层未参与 safe-area** — Browser 侧无 `env(safe-area-inset-*)` / `theme-color`，Android 上部分 UI 被原生替代，但 Web 弹层仍有风险  

**核心原则（与 Android 官方 edge-to-edge 一致）：**

- Figma 定义的 Toolbar 高度（如 56dp / 82dp）是**设计尺寸**，保持不变  
- 系统栏（Status Bar / Navigation Bar / 手势区 / 挖孔）通过 **WindowInsets 额外占位**，不与设计高度混算  

---

## 2. UI 结构

### 2.1 文档页（`LOActivity`）

```text
DrawerLayout (doc_drawer_layout)
└── ConstraintLayout (doc_main_content)
    ├── doc_top_toolbar_include     ← 顶栏（预览/编辑）
    ├── COWebView (browser)         ← 文档 WebView
    ├── ai_fab                      ← AI 浮动按钮（可拖拽）
    ├── doc_bottom_toolbar          ← 底栏（82dp 设计高度）
    └── 各类 Overlay / Dialog
```

约束关系（`lolib_activity_main.xml`）：

```text
Top Toolbar
    ↓ constraint
WebView
    ↓ constraint
Bottom Toolbar
```

WebView **不直接**通过 margin 消费 inset；`LOActivity` 中 WebView 的 `topMargin = 0`、`bottomMargin = 0`，由上下工具栏承担系统栏避让。

### 2.2 首页（`LibreOfficeUIActivity`）

```text
DrawerLayout
└── ConstraintLayout (overview_coordinator_layout)
    ├── homeTopBar                  ← 头像 + 搜索 + 打开文件
    ├── recentsHeaderRow            ← 「最近」标题行
    ├── RecyclerView (list_recent)  ← 最近文件列表
    ├── editFAB / newDocOverlay     ← 新建文档 FAB
    └── navigation_drawer           ← 左侧抽屉（AI 配置、清理缓存等）
```

### 2.3 Web 层（Collabora Browser）

- Android 移动端启用原生顶/底栏时，Web 侧 `MobileTopBar` / `MobileBottomBar` 被禁用（`Control.UIManager.ts`）  
- 无 `viewport-fit=cover`、`theme-color`、`env(safe-area-inset-*)`  
- 横屏 popup 有 `android-native-bottom-toolbar` 等 CSS workaround  

---

## 3. 当前 Inset 实现盘点

### 3.1 统一入口（已建立，未全覆盖）

| 模块 | 路径 | 职责 |
|------|------|------|
| `SystemUiHelper` | `android/lib/.../SystemUiHelper.java` | edge-to-edge、透明系统栏、status/nav padding、inset 解析、`trackSafeAreaChanges` |
| `SafeAreaInsets` | `android/lib/.../SafeAreaInsets.java` | 统一 Safe Area 快照（top/left/right/bottom/IME） |
| 尺寸 Token | `android/lib/.../values/dimens.xml` | `lolib_inset_top_safe_extra`(4dp)、`lolib_inset_bottom_safe_extra`(8dp)、`lolib_inset_gesture_min`(20dp) |

**`resolveBottomInset` 合并类型：**

- `navigationBars` / `systemGestures` / `tappableElement` / `displayCutout`  
- IME 可见时优先 IME bottom  
- inset 为 0 时（常见手势导航/曲面屏）回退 **20dp**  
- 额外 **8dp** 安全边距（防曲面裁切）  

### 3.2 文档页

| Surface | 状态 | 实现方式 | 备注 |
|---------|------|----------|------|
| 顶栏 `doc_top_toolbar` | ✅ 已接 | `#F2F2F2` + `doc_top_status_plate` + 56dp 内容 | Figma 556:21271；`statusBarColor` 同步 |
| 底栏 `doc_bottom_toolbar` | ✅ 已接 | 82dp 内容 + `doc_bottom_nav_spacer`（无键盘）/ `bottomMargin`（有键盘） | Figma 白底延伸进三键区，内容不被挤占 |
| WebView | ⚪ 不直接接 | 约束在上下栏之间 | `WindowInsets.CONSUMED` 在 WebView listener |
| AI FAB | ✅ 已修 | `getFabMaxY` + `getReservedBottomHeightPx` 含 margin/padding/inset；inset 变化时 `clampAiFabPosition` | |
| 设置抽屉顶/底 | ✅ 部分 | `doc_settings_drawer` + footer padding | |
| BottomSheet 功能面板 | ✅ | `BottomSheetAnchorHelper` → `SystemUiHelper` | expandedOffset + 8dp 内容 safe pad |
| AI Panel | ✅ 部分 | `AiPanelController.installSheetInsets` | 已对齐 SystemUiHelper |
| 居中 AlertDialog | ⚪ 一般无问题 | 居中布局 | 不贴底 |

**底栏逻辑（当前代码，2026-08-18）：**

```text
无键盘：
  bottomMargin = 0
  bottomPadding = navigationBarInset + 8dp(safe)
  → 工具栏 82dp 内容区不变，白底延伸到系统导航区

有键盘：
  bottomMargin = max(IME, nav) + 4dp
  bottomPadding = 0
  → 整栏顶到键盘上方
```

> **注意：** 早期分析文档写「无键盘时 bottomMargin = navInset」已过时；当前为 **padding 延伸** 方案，更接近「内容区 + 系统区分层」。

### 3.3 首页

| Surface | 状态 | 实现方式 | 备注 |
|---------|------|----------|------|
| `homeTopBar` | ✅ 已修 | `#F2F2F2` 底板 + `home_top_status_plate` + 56dp 内容行 | 与文档页顶栏 Figma 556:21271 一致 |
| 最近列表 | ✅ | RecyclerView bottom padding | |
| 首页 FAB | ✅ 部分 | `applyHomeFabWindowInsets` | |
| 抽屉 panel 顶 | ✅ | `navigation_drawer_panel` status padding | |
| 抽屉 footer | ✅ | `navigation_drawer_footer` nav padding + 12dp | |
| 模型配置 overlay | ✅ | `panel_ai_model_config` header/footer inset | 取消/保存按钮 |
| `AiModelConfigActivity` | ✅ | 独立 Activity 版 inset | |

### 3.4 主题

`LibreOfficeTheme.Base`（`themes.xml`）：

- `statusBarColor` / `navigationBarColor` → transparent  
- `windowDrawsSystemBarBackgrounds` → true  

---

## 4. 已知问题与风险

### 4.1 P0 级（系统栏 / 遮挡）

| ID | 问题 | 影响 | 相关文件 |
|----|------|------|----------|
| P0-1 | AI FAB 拖拽边界未含 nav inset | ~~拖到底部可能进入手势/三键区~~ | ✅ 已修：`SafeAreaInsets` + `clampAiFabPosition` |
| P0-2 | 部分 Dialog / 原生确认框未统一接 Safe Area | 曲面屏底部按钮可能贴边 | 各 `lolib_dialog_*.xml` |
| P0-3 | WebView `CONSUMED` insets | 若未来 Web 层需 inset 传递会受阻 | `LOActivity` WebView listener |
| P0-4 | 手势 nav inset=0 的设备 | 已加 20dp 最小回退，需真机回归 | `SystemUiHelper` |
| P0-5 | 首页 vs 文档页 inset 路径不一致 | 维护成本高、易回归 | 多 Activity |

### 4.2 P1 级（Safe Area 覆盖）

| ID | 问题 | 说明 |
|----|------|------|
| P1-1 | 无统一 Safe Area 查询 API | 各 Controller 自行计算 |
| P1-2 | `getReservedBottomHeightPx()` 不含 nav inset | ~~用于 FAB、Web 预留高度时可能偏小~~ | ✅ 已修：含 toolbar margin/padding + nav fallback |
| P1-3 | Quick Action 浮层、Selection Menu 等待审计 | 贴底 UI 需逐文件确认 |
| P1-4 | 抽屉内二级 overlay 与 panel inset 可能重复或遗漏 | 需清单化 |

### 4.3 P2–P5 级（长期）

| 级别 | 内容 |
|------|------|
| P2 | Figma → Design Tokens（spacing / icon / radius / touch target） |
| P3 | XML 响应式布局（减少写死 width/height） |
| P4 | Window Size Class（Compact / Medium / Expanded） |
| P5 | Android inset → WebView → Collabora CSS/JS bridge |

---

## 5. 设计原则（验收基准）

### 5.1 系统栏 vs Figma 尺寸

```text
Android 实际屏幕：
┌──────────────────┐
│ Status Bar       │ ← inset（非 Figma）
├──────────────────┤
│ Top Toolbar 56dp │ ← Figma 设计高度
├──────────────────┤
│                  │
│     WebView      │
│                  │
├──────────────────┤
│ Bottom Bar 82dp  │ ← Figma 设计高度
├──────────────────┤
│ Nav / Gesture    │ ← inset（非 Figma）
└──────────────────┘
```

**禁止：** 把 Toolbar XML 高度写成 `56 + statusBarHeight`。  
**正确：** Toolbar 保持 56dp，外层容器通过 `paddingTop += statusBarInset` 增高。

### 5.2 底栏推荐结构

```text
BottomBarContainer (doc_bottom_toolbar)
├── ToolbarContent      ← 固定 82dp（Figma，HorizontalScrollView）
└── doc_bottom_nav_spacer ← 三键/手势导航预留白底区（高度 = navInset + safeExtra）
```

`BottomToolbarController` 通过 `SystemUiHelper.applyBottomNavSpacer` 驱动 spacer，**不再**用 padding 挤占 82dp 内容区。三键导航设备 fallback 最小 48dp（`lolib_inset_nav_bar_min`）。

### 5.2.1 顶栏推荐结构（Figma 556:21271）

```text
TopBarContainer (#F2F2F2)
├── doc_top_status_plate  ← 状态栏区，高度 = statusBarInset
└── 内容行                 ← 固定 56dp（预览/编辑工具栏）
```

系统 `statusBarColor` 同步为 `#F2F2F2`（`lolib_chrome_status_plate`），与顶栏底板一致。

### 5.3 三键 vs 手势导航

- **不要**所有设备统一加 48dp margin  
- **应该**优先读 `WindowInsets`，0 时最小回退 + 安全边距  
- 可选：用 `tappableElement` 区分三键（有 tappable 区域）与手势（透明延伸）  

### 5.4 交互热区

- 视觉 icon 24dp 可以，**触控目标建议 ≥ 48dp**（Material）  
- 视觉尺寸 ≠ Hit Target 尺寸  

### 5.5 验收清单（单页）

```text
① 系统栏：Status / Nav / Gesture / Cutout / IME
② 窗口：360 / 390 / 430dp 宽、横屏、平板
③ 布局：约束布局、避免写死屏宽
④ 交互：无遮挡、键盘不挡输入、单手可达
⑤ 视觉：spacing / 字体 / 圆角与 Figma 一致（在 safe area 内）
⑥ WebView：viewport / safe area / 键盘（若启用 Web 栏）
```

---

## 6. 改进路线图

### P0 — 系统栏 / Insets 统一（★★★★★ 当前重点）

**目标：** 所有贴边 interactive UI 不被系统栏遮挡。

| 任务 | 说明 | 优先级 |
|------|------|--------|
| P0-a | 扩展 `SystemUiHelper`：暴露 `SafeAreaInsets` + `trackSafeAreaChanges` | 高 | ✅ |
| P0-b | 修复 `LOActivity` AI FAB：`getFabMaxY` + `clampAiFabPosition` | 高 | ✅ |
| P0-c | 审计并接入：Quick Action、Selection Popup、原生确认 Dialog 底栏 | 中 | ✅ |
| P0-d | 统一 WebView inset 策略：是否改为 `return insets` 或集中由 root 分发 | 中 |
| P0-e | 真机矩阵回归：三键 / 手势 / 曲面 / 刘海 / 横竖屏 | 高 |

**完成标准：** 文档页 FAB、底栏按钮、功能面板保存、抽屉保存在测试矩阵内无遮挡。

---

### P1 — Safe Area 全覆盖（★★★★★）

**目标：** 建立「Safe Content Area」概念，浮动 UI 全部遵循。

```text
WindowInsets
    ↓
SafeAreaInsets (top, bottom, left, right, ime)
    ↓
├── Top Toolbar
├── Bottom Toolbar
├── FAB / Popup / BottomSheet / Dialog
└── (可选) WebView reserved height
```

| 任务 | 说明 |
|------|------|
| P1-a | `getReservedBottomHeightPx()` 改为含 nav inset |
| P1-b | 抽屉 / overlay / 二级 panel 统一走 SafeArea API |
| P1-c | 文档页所有贴底 Overlay 清单 + 逐条打勾 |

---

### P2 — Design Tokens（★★★★☆）

统一 `dimens.xml` / `colors.xml`：

- spacing: 4 / 8 / 12 / 16 / 20 / 24 / 32  
- corner: 8 / 12 / 16 / 24  
- icon: 20 / 24 / 32 / 48  
- touch_target_min: 48dp  
- toolbar: top 56dp, bottom 82dp  

减少散落魔法数（13dp、17dp、19dp…）。

---

### P3 — XML 响应式布局（★★★★☆）

- 多用 `0dp` + Constraint、`wrap_content`、`minHeight`  
- 少写 `layout_width="358dp"` 等屏宽耦合值  
- Figma 提取**约束规则**而非绝对像素  

---

### P4 — Window Size Class（★★★☆☆）

按 window width 适配，非手机型号：

| 宽度 | 类别 |
|------|------|
| < 600dp | Compact |
| 600–839dp | Medium |
| ≥ 840dp | Expanded |

横屏：紧凑底栏 / 最大化 WebView viewport（生产力场景）。

---

### P5 — Android → WebView → Collabora（★★★☆☆）

```text
Native SafeAreaInsets
    ↓ JS Bridge / CSS variables
Collabora browser UI
    ↓
env(safe-area-inset-*) / padding-bottom on mobile-wizard
```

仅在 Native 栏未覆盖的 Web UI 场景必要。

---

## 7. 关键文件索引

| 文件 | 职责 |
|------|------|
| `LOActivity.java` | 文档页生命周期、WebView inset、FAB、SystemChrome |
| `BottomToolbarController.java` | 底栏模式、IME/nav padding/margin |
| `TopToolbarController.java` | 顶栏逻辑（56dp 内容区） |
| `SystemUiHelper.java` | 统一 inset / edge-to-edge / SafeArea 追踪 |
| `SafeAreaInsets.java` | Safe Area 快照与 FAB 预留高度计算 |
| `DocumentOverlayInsets.java` | 浮层贴底 clamp / 底栏预留解析 |
| `SelectionMenuController.java` | 选区菜单定位（含 bottom reserved） |
| `BottomSheetAnchorHelper.java` | BottomSheet 锚定 + inset |
| `AiPanelController.java` | AI 面板 sheet inset |
| `LibreOfficeUIActivity.java` | 首页、抽屉、模型配置 overlay |
| `lolib_activity_main.xml` | 文档页主布局 |
| `lolib_doc_top_toolbar.xml` | 文档顶栏 |
| `activity_document_browser.xml` | 首页布局 |
| `navigation_drawer_*.xml` | 首页抽屉 |
| `panel_ai_model_config.xml` | 抽屉内模型配置 |
| `device-mobile.css` | Web 移动端 CSS |
| `Control.UIManager.ts` | Android 原生栏开关 |

---

## 8. 外部参考

- [Android Edge-to-edge（Views）](https://developer.android.com/develop/ui/views/layout/edge-to-edge)  
- [Android 系统栏设计指南](https://developer.android.com/design/ui/mobile/guides/foundations/system-bars)  
- [WindowInsets API](https://developer.android.com/reference/android/view/WindowInsets)  
- [Window Size Class](https://developer.android.com/develop/ui/views/layout/use-window-size-classes)  

---

## 9. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-08-18 | 初版：结构分析、现状盘点、问题列表、P0–P5 路线图 |
| 2026-08-18 | 已实施：`SystemUiHelper`、首页顶栏 wrap_content、底栏 padding 策略、BottomSheet/抽屉部分 inset |
| 2026-08-18 | P0-c：`DocumentOverlayInsets`、Selection/Calc/Impress 浮层 clamp、Dialog safe inset、AI overlay 约束到工具栏之间、首页 FAB 接 `SafeAreaInsets` |

---

## 10. 下一步行动

按路线图 **从 P0 继续**，建议顺序：

1. ~~**P0-b** — 修复 AI FAB Safe Area~~ ✅  
2. ~~**P0-a** — 抽出 `SafeAreaInsets` 统一 API~~ ✅  
3. ~~**P0-c** — 贴底 UI 审计（Quick Action、Selection Menu、原生 Dialog）~~ ✅  
4. **P0-e** — 真机回归矩阵  
5. 再进入 **P1** 全覆盖（Web reserved height 等）  

本文档随每次 P 级任务完成更新「§3 现状盘点」与「§9 变更记录」。
