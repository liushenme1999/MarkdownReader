# 变更日志 (CHANGELOG)

本项目遵循 [Semantic Versioning (语义化版本)](https://semver.org/) 规范。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

### 新增

- LaTeX 块级/行内公式样式（居中、圆角边框与底色）
- 行内 `` `code` `` 圆角底色（仅包裹代码内容）
- mhchem 化学式 `\ce{…}` 预处理（`CeLatexConverter`）
- JLatex 兼容改写：`\cancel` / `\bcancel` → `\st`；`\oiint` / `\oiiint` → 组合积分算子
- 行内 `$…$` 垂直居中；`\otimes` 等符号 Noto Sans Math 回退
- 单元测试：LaTeX 预处理、化学式、积分公式、内联样式等（Robolectric）

### 修复

- 行内代码背景向左溢出遮挡相邻文字
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

[Unreleased]: https://github.com/liukejun1999/MarkdownReader/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/liukejun1999/MarkdownReader/releases/tag/v1.0.0
