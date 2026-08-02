# 变更日志 (CHANGELOG)

本项目遵循 [Semantic Versioning (语义化版本)](https://semver.org/) 规范。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

## [1.0.4] - 2026-08-02

### 新增

- 从公开 GitHub 仓库导入完整项目（JGit 浅克隆，无需登录）
- 项目目录树浏览 Markdown / TXT，打开后进入既有阅读器
- 项目内相对路径图片解析为本地 `file://` 并正常渲染
- 项目浏览器：更多菜单（更改分支、拉取更新、删除项目）
- 项目继续阅读：最近阅读历史列表，支持展开/折叠；条目展示阅读进度
- Git 项目支持收藏、置顶、分组、移出书架；下拉刷新同步云端进度并 pull 全部 Git 项目
- 云端备份同步 Git 项目元数据（上次打开/最近阅读历史）与项目内文档阅读进度
- 书架布局新增「项目最近阅读展示数量」（1–5，仅本机）

### 优化

- 书架布局（网格/列表、列数、最近阅读条数）仅本机保存，不再随 WebDAV 备份同步
- Git 项目卡片展示仓库名与作者（owner），封面渐变与书籍卡片区分
- 书架书籍与 Git 项目按活动时间混排（置顶优先）
- 收藏图标统一右下角、置顶图标统一右上角
- 书架布局中宫格列数、最近阅读条数改为粗滑条调节
- 阅读设置与文内翻页选项仅保留上下滚动、左右滑动

### 修复

- Markdown 图片在行距倍数下下方出现大块空白（顶对齐后补偿行高；本地图占位按正文宽度缩放）

## [1.0.3] - 2026-08-01

### 新增

- WebDAV 远程备份与恢复（书架、分组、进度、书签、划线、设置、个人资料与书籍内容）
- 备份与恢复设置页：服务器地址、账号密码、子目录、设备名称、同时上传最新备份
- 退出应用时按日自动 WebDAV 备份
- WebDAV 备份帮助页（顶栏入口与列表入口；中/英/繁）
- 书架分组管理与书架布局选项（网格/列表、网格列数）

### 修复

- 正式包恢复 WebDAV 备份时因 R8 抹掉 Gson TypeToken 泛型签名而失败的问题

## [1.0.2] - 2026-08-01

### 新增

- 应用内语言切换（简体中文 / 繁体中文 / English），支持设置持久化与运行时切换
- 用户协议、隐私政策、关于应用独立页面；「我的」页菜单入口
- APP ICP 备案号悬挂（关于页显著位置，可跳转工信部备案系统）
- 高亮样式持久化（Room 迁移至 v8）；记住上次高亮颜色与样式
- 目录当前章节高亮，并自动滚动置顶
- 选区绑定高亮：菜单区分「划线」与「取消划线」

### 优化

- 文本选择菜单隐藏多余「搜索」项，界面更干净
- 高亮卡片滑动删除与展开状态管理
- 行内 LaTeX 支持选区复制与划线提取（`sourceLatex`）
- 高亮绘制在滚动时减少被裁剪
- 阅读打开位置与滚动恢复更准确
- AppCompat / `localeConfig` 本地化基础设施与依赖整理

## [1.0.1] - 2026-07-24

### 新增

- LaTeX 块级/行内公式样式（居中、圆角边框与底色）
- 行内 `` `code` `` 圆角底色（仅包裹代码内容）
- mhchem 化学式 `\ce{…}` 预处理（`CeLatexConverter`）
- JLatex 兼容改写：`\cancel` / `\bcancel` → `\st`；`\oiint` / `\oiiint` → 组合积分算子
- 行内 `$…$` 垂直居中；`\otimes` 等符号 Noto Sans Math 回退
- 单元测试：LaTeX 预处理、化学式、积分公式、内联样式等（Robolectric）

### 修复

- 行内代码背景向左溢出遮挡相邻文字
- 长行内代码（如 JSON）在窄屏被裁切、无法换行的问题
- 列表项中行内代码换行后圆角底色与文字水平错位
- 块级公式因单个不支持命令导致整段空白
- Kotlin 编译警告（`ReaderCompoundInlineLatexSpan` 参数命名与接口不一致）

### 变更

- 应用包名迁移为 `space.liushenme.markdownreader`
- README / 贡献指南等文档同步更新

## [1.0.0] - 2026-05-28

### 新增

- **书架管理**
  - 卡片式书籍展示
  - Markdown / TXT / PDF 导入（文件选择器、URL、外部 Intent）
  - 阅读进度显示、收藏、分组、置顶与删除
- **阅读器**
  - Markwon Markdown 渲染（表格、任务列表、删除线、HTML、LaTeX、图表等）
  - 5 种阅读主题、字体/页边距/行距调节
  - 4 种翻页方式（滚动 / 滑动 / 仿真 / 覆盖）
  - 目录跳转、书签、多色高亮、阅读进度保存
- **笔记与统计**
  - 划线与书签管理；导出 Markdown / JSON
  - 阅读时长、字数、趋势图（Vico）
- **个人中心**
  - 应用主题（亮/暗/跟随系统）、阅读设置、缓存清理

### 技术

- Jetpack Compose + MVVM + Hilt + Room + DataStore
- Markwon 4.6.2 + JLatexMath；Mermaid / ECharts 图表 WebView 渲染
- PDF 页面栅格化导入
- GitHub Actions CI（Lint、Debug/Release 构建、单元测试）

[Unreleased]: https://github.com/liukejun1999/MarkdownReader/compare/v1.0.4...HEAD
[1.0.4]: https://github.com/liukejun1999/MarkdownReader/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/liukejun1999/MarkdownReader/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/liukejun1999/MarkdownReader/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/liukejun1999/MarkdownReader/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/liukejun1999/MarkdownReader/releases/tag/v1.0.0
