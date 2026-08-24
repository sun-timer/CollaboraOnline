# UI 适配跟踪清单

> **用途**：排查回归、真机验收、记录 open issue。详细背景见 [`mobile-ui-adaptation-report.md`](./mobile-ui-adaptation-report.md)。  
> **最后更新**：2026-08-19（文档页 Sheet 浮层 anchor/padding 补丁）  
> **维护约定**：修完一项把 `[ ]` 改 `[x]`，新增问题写在 §6，并在 §9 变更记录留一行。

---

## 0. 快速定位（排查时先看这里）

| 现象 | 优先查 | 常见原因 |
|------|--------|----------|
| 底部按钮被三键/手势区挡住 | `SystemUiHelper.resolveNavigationBottomInset` → surface 是否接 Safe Area | 只修了 BottomToolbar，FAB/Dialog/Footer 未接 |
| 键盘弹出后底栏错位/截断 | `BottomToolbarController.applyImeState` | margin/spacer 切换；IME inset 为 0 |
| 华为曲面屏底部仍贴边 | inset=0 → `lolib_inset_gesture_min`(20dp) | 仅 fallback，不再强制三键 48dp |
| 横屏/瀑布屏左右按钮贴边 | `resolveHorizontalSafeInsetLeft/Right` | 缺 waterfall / cutout 合并 |
| 三键导航底部空白过大 | `resolveNavigationBottomInset` | 旧逻辑强制 min 48dp；现仅 inset=0 时 fallback |
| 平板弹窗过宽/贴边 | `ResponsiveUiHelper` + `values-sw600dp/` | app 模块已有 sw600，lib 仍 mostly values/ |
| 平板首页一行两个文件 | `LinearLayoutManager` 单列 | 误用 Grid 双列 |
| 抽屉 → 基础模型灰屏 | 平板 `AiModelConfigActivity` | overlay + CONSUMED inset |
| 选区/Calc 浮层被底栏挡 | `DocumentOverlayInsets` + `getReservedBottomHeightPx` | 含 spacer 高度 |
| Web 文档 fixed 贴底 UI 被挡 | P5 WebView bridge | Native 安全 ≠ Web 安全 |

**核心代码入口**

| 模块 | 路径 |
|------|------|
| Insets 统一 | `android/lib/.../SystemUiHelper.java` |
| Safe Area 快照 | `android/lib/.../SafeAreaInsets.java` |
| 浮层 clamp | `android/lib/.../DocumentOverlayInsets.java` |
| 文档页 | `android/lib/.../LOActivity.java` |
| 底栏 | `android/lib/.../BottomToolbarController.java` |
| 首页 | `android/app/.../LibreOfficeUIActivity.java` |
| 平板断点 | `android/app/.../values-sw600dp/dimens.xml` |

---

## 0.1 外部评审结论（GPT 2026-08-18）

**总体**：架构方向正确，**不必推翻** `SystemUiHelper + SafeAreaInsets`；不能宣称「全面兼容」。

| 维度 | 评分 | 说明 |
|------|------|------|
| dp/sp + Figma token | ★★★★★ | 56/82dp 已规范 |
| 顶栏 status plate | ★★★★☆ | plate + 56dp 分离 |
| 底栏 nav spacer | ★★★★☆ | 82dp + spacer 分层 |
| 三键/手势/IME | ★★★★☆ | 已改：inset 优先，fallback 次之 |
| FAB / Drawer / Dialog | ★★★★☆ | 有体系，浮层待审计 |
| Cutout / 刘海 | ★★★★☆ | top + WebView 左右 |
| **Waterfall / 横屏侧 inset** | ★★★☆☆ → 补丁中 | **P0-i 已补代码，待真机** |
| Tablet adaptive layout | ★★☆☆☆ | app 有 sw600，缺 layout-sw600dp |
| WebView safe-area | ★★☆☆☆ | P5 deferred |

**GPT 建议继续做的 4 项（本仓库优先级）**

1. **P0-i** waterfall + 横向 cutout → `SafeAreaInsets.left/right`（**已提交代码**）
2. **P0-j** 三键 fallback：WindowInsets 优先，48dp 仅 inset=0（**已提交代码**）
3. **P1-a** Dialog/BottomSheet/Popup 全局 Safe Area 审计
4. **P5** Native inset → WebView/CSS bridge

**明确不做**：继续堆 `+8dp` fallback；整页比例缩放；按手机型号分支。

---

## 1. 尺寸策略（摘要）

固定 **设计 token**（图标 24dp、Toolbar 56/82dp）+ **约束横向撑满** + **Insets 占位**；不要 H5 式 vw 缩放。  
内部组件（搜索框、头像）：横向 `weight=1` / 约束自适应，高度/直径固定 dp。详见 §1 历史版本或 report §5。

---

## 2. P0 — 系统栏 / Insets

| ID | 任务 | 状态 | 验证要点 |
|----|------|------|----------|
| P0-a | `SystemUiHelper` + `SafeAreaInsets` + `trackSafeAreaChanges` | [x] | |
| P0-b | AI FAB clamp（含横向 waterfall） | [x] | 横屏拖 FAB 不进侧弯区 |
| P0-c | 浮层 `DocumentOverlayInsets` | [x] | |
| P0-d | Dialog `applyCenteredDialogSafeInsets` | [x] | |
| P0-e | **真机回归矩阵** | [ ] | §4 |
| P0-f | `doc_bottom_nav_spacer` 接线 | [x] | `BottomToolbarController.applyBottomNavSpacer` |
| P0-g | 首页 inset 双路径收拢 | [ ] | RecyclerView 独立 listener |
| P0-h | WebView `CONSUMED` 文档化 | [x] | |
| P0-i | **waterfall + 横向 safe** | [x] 待测 | 顶/底栏、WebView、抽屉、首页顶栏 |
| P0-j | **三键 48dp 仅 fallback** | [x] 待测 | 真实 32dp nav 不应被抬到 48dp |

---

## 3. P1–P5 进度

| 级别 | 主题 | 进度 | 下一步 |
|------|------|------|--------|
| P1 | Safe Area 全覆盖 | ~78% | 文档页 wrap-content Sheet 已对齐（O-8）；编辑态大面板/AI 系待审计（O-3） |
| P2 | Design Tokens | ~20% | spacing/icon 命名 |
| P3 | XML 约束布局 | ~40% | 去固定屏宽 |
| P4 | Window Size Class | ~45% | app 有 sw600；lib 文档页可补 content_maxWidth |
| P5 | WebView inset bridge | 0% | Native 稳定后 |

---

## 4. 真机验收矩阵（P0-e）

### 4.1 设备 × 导航模式

| 设备类型 | 三键 | 手势 | 横屏 | 瀑布/曲面侧 inset |
|----------|------|------|------|-------------------|
| 小屏 ~360dp | [ ] | [ ] | [ ] | [ ] |
| 基准 ~390dp | [ ] | [ ] | [ ] | [ ] |
| 华为曲面 | [ ] | [ ] | [ ] | [ ] |
| 平板 sw≥600 | [ ] | [ ] | [ ] | [ ] |

### 4.2 页面 × 检查项

| 页面 | Status | Nav 底 | Nav 侧(横屏) | 键盘 | Figma |
|------|--------|--------|--------------|------|-------|
| 首页顶栏 | [ ] | [ ] | [ ] | [ ] | [ ] |
| 文档顶/底栏 | [ ] | [ ] | [ ] | [ ] | [ ] |
| 抽屉 footer | [ ] | [ ] | — | — | [ ] |
| 功能面板 Sheet（编辑态大面板） | [ ] | [ ] | — | [ ] | [ ] |
| 预览功能 Sheet | [ ] | [ ] | — | — | [ ] |
| 查找替换 Sheet | [ ] | [ ] | — | [ ] | [ ] |
| 字数统计 Sheet | [ ] | [ ] | — | — | [ ] |
| Dialog | [ ] | [ ] | — | [ ] | [ ] |

---

## 5. 已知 Open Issues

| # | 描述 | 严重性 | 状态 |
|---|------|--------|------|
| O-1 | ~~spacer 未接线~~ | — | **closed** |
| O-2 | 首页 RecyclerView 独立 inset listener | 低 | open |
| O-3 | 部分 Dialog 未 `applyCenteredDialogSafeInsets` | 中 | 待审计 |
| O-4 | `ResponsiveUiHelper` 用 sw 非 window width | 低 | 可接受 |
| O-5 | Web 层无 safe-area CSS | 低 | P5 |
| O-6 | lib 模块无 `layout-sw600dp`（GPT 指出的 adaptive 缺口） | 中 | open |
| O-7 | WebView / LOActivity 双路径更新底栏 inset | 低 | open |
| O-8 | 文档页 wrap-content Sheet 缺 `anchorAboveBottomPx` | 中 | **closed** — overlay sheet 应 anchor=0 + XML 34dp；勿叠文档底栏高度 |
| O-9 | BottomSheet 多 tab 测高（双 panel 叠测） | 低 | **closed** — 查找替换已分 tab 测高取 max |

---

## 6. 回归记录

| 日期 | 设备 | 现象 | 根因 | 修复 | 复测 |
|------|------|------|------|------|------|
| 2026-08-18 | 平板 | 一行两个文件 | Grid 双列 | Linear | [ ] |
| 2026-08-18 | 平板 | 基础模型灰屏 | drawer overlay | Activity | [ ] |
| 2026-08-18 | — | GPT：waterfall/三键 fallback | 见 P0-i/j | 已改代码 | [ ] |
| 2026-08-19 | — | 预览功能 Sheet 高度/tab/底 padding 重复 | `expandFixed` + tab 重测 + `applyNavBarPadding=false` | LOActivity | [x] 用户已确认 |
| 2026-08-19 | — | 查找替换替换 tab 按钮截断 | 只测查找 tab；双 panel 叠测 | 分 tab 测高 + Figma dimens | [ ] |
| 2026-08-19 | — | `expandFixed` 清零 XML 34dp 底 padding | `applyNavBarPadding=false` 时仍改 padding | BottomSheetAnchorHelper | [ ] |
| 2026-08-19 | — | 字数统计 Sheet 未锚在文档底栏上方 | 误用 `anchorAboveBottomPx` | 改回 anchor=0 | [ ] |
| 2026-08-19 | — | 查找替换底部多段空白 + 键盘不跟随 | anchor 叠 34dp；decor 未接 IME inset | 去 anchor；BottomSheetAnchorHelper decor 监听 | [ ] |

---

## 7. Safe Area 四层模型（冻结，勿再堆 magic number）

```text
System Insets (status/nav/cutout/waterfall/gesture/IME)
        ↓
SafeAreaInsets 快照
        ↓
App Chrome（顶 56 / 底 82 + spacer / plate）
        ↓
Floating UI（FAB / Sheet / Dialog / Popup → reserved height）
```

---

## 8. 排查命令

```bash
adb logcat -s BottomToolbarController LOActivity SystemUiHelper
```

---

## 9. 变更记录

| 日期 | 说明 |
|------|------|
| 2026-08-18 | 初版 tracker |
| 2026-08-18 | GPT 二次评审落盘；P0-i waterfall 横向；P0-j nav fallback；P0-f spacer 完成；`doc_bottom_toolbar_content` id |
| 2026-08-19 | 二级页 status bar：头像昵称/清缓存/ShowHTML/Settings + 抽屉模型配置 overlay；`applySecondaryActivityChrome` |
