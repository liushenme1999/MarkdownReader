# MD 阅读器 (Markdown Reader)

一款类似微信读书的 Android Markdown 阅读应用，支持书架管理、PDF/文本导入、丰富 Markdown 渲染、划线书签与阅读统计。

| 项目 | 说明 |
|------|------|
| **应用 ID** | `space.liushenme.markdownreader` |
| **包名** | `space.liushenme.markdownreader` |
| **最低系统** | Android 7.0 (API 24) |
| **目标 SDK** | 35 |
| **当前版本** | 1.1.2 (versionCode 14) |
| **仓库** | [github.com/liukejun1999/MarkdownReader](https://github.com/liushenme1999/MarkdownReader) |

## 安装包下载

无需自行编译，可直接下载已签名的正式版（Release）APK 安装使用：

| 版本 | 下载 | 大小约 |
|------|------|--------|
| **1.1.2** | [MD阅读器_release_1.1.2.apk](app/release/MD阅读器_release_1.1.2.apk) | 16 MB |

安装说明：

1. 点击上方链接，进入仓库中的 APK 文件页，再点击 **Download** / **下载** 保存到手机
2. 在系统设置中允许本应用（或文件管理器）「安装未知应用」
3. 打开已下载的 APK 完成安装

> Debug 包与中间产物仍不纳入版本库；仓库只保留最新正式包，便于直接下载。
>
> 启动后会在后台读取仓库根目录 [latest.json](latest.json)。有新版本时「我的 → 关于应用」显示红点，进入后可点「更新版本」。发版时请与 APK、`versionName` / `versionCode` 一并更新。

## 功能特性

### 书架管理

- 卡片式书籍展示
- 导入 **Markdown**（`.md` / `.markdown` 等）、**纯文本**（`.txt`）、**PDF**
- 支持系统文档选择器、URL 下载、「用其他应用打开」与分享导入
- **从公开 GitHub 仓库导入完整项目**（JGit 浅克隆）：按仓库目录树浏览文档，支持切换分支、`pull` 拉取更新；项目内相对路径图片可正常显示
- 项目继续阅读历史（可展开，显示进度）；Git 项目与书籍混排、收藏/置顶/分组
- 阅读进度百分比、收藏、分组管理、书架布局（网格/列表、宫格列数）、置顶与删除

### 阅读器

- **颜色与背景**：横向样式槽（点击选用、长按编辑、可新增）；编辑文字色、背景色（含色值）、透明度与内置背景图；代码块与公式底色随纸面自动区分
- **2 种翻页方式**：上下滚动、左右滑动
- 阅读页底栏将主题、字体排版、翻页方式、隐藏系统栏聚合为单一「阅读设置」
- **隐藏系统栏**：阅读时隐藏状态栏与底部导航条（默认开启，可在阅读页或「我的 → 阅读设置」关闭）
- 字体大小、页边距、行距调节（DataStore 持久化）
- **PDF 连续阅读**：页内双指缩放 / 拖动，平移不超出纸张；大文件导入时书架显示进度，完成后再打开
- **代码块自动换行**（默认开启）；关闭后超宽代码可横向滑动，并显示语言标签与复制按钮
- 阅读页顶栏随进度显示当前章节标题
- 目录（TOC）跳转；导入时优先使用结构化目录
- 书签、多色高亮、文本选择与笔记
- 阅读进度（字符坐标）自动保存
- 内链锚点跳转；外链可在应用内 WebView 打开

### Markdown 渲染

基于 [Markwon](https://github.com/noties/Markwon) 4.6.2，并针对阅读场景做了大量扩展：

| 类别 | 支持内容 |
|------|----------|
| **GFM** | 表格、任务列表、删除线、脚注、高亮 `==text==` |
| **LaTeX** | 块级 `$$…$$`、行内 `$…$`；JLatexMath 渲染 |
| **化学式** | mhchem 风格 `\ce{…}`（预处理为 JLatex 可解析形式） |
| **高级公式** | `\oiint` / `\oiiint`、`\cancel`、`\stackrel`、`\xleftarrow` 等（部分经预处理器改写） |
| **图表** | 围栏代码块 `mermaid` / `echarts` / `chart`（WebView 异步渲染） |
| **代码** | 围栏代码块语法高亮；行内 `` `code` `` 圆角底色；可关闭自动换行后横向滚动查看 |
| **图片** | 网络图片缓存与占位；点击普通图全屏预览（毛玻璃背景）；PDF 页图在阅读页内缩放平移；HTML `<img>` 布局优化；Git 项目文档支持相对路径本地图 |
| **其他** | HTML 片段、自动链接、PDF 栅格化页面 |

> LaTeX 由 JLaTeXMath 驱动，并非完整 TeX 环境。不支持的命令会在解析前尝试预处理；仍无法渲染的公式会在 Logcat 输出 `JLatexMathPlugin` 错误。

### 划线、书签与笔记

- 文本选择后高亮（黄 / 绿 / 青 / 粉 / 橙）
- 书签与笔记管理（搜索、跳转、删除）
- 导出为 Markdown 或 JSON

### 阅读统计与个人中心

- 书籍总数、阅读时长、字数、书签/划线数量
- 近 7 天阅读趋势图（Vico）
- 应用主题（亮色 / 暗色 / 跟随系统）、缓存清理
- **WebDAV 备份与恢复**（元数据云备份、书籍正文独立同步、合并恢复、启动检测新备份、退出时按日自动备份）

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 17 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 依赖注入 | Hilt + KSP |
| 本地数据 | Room（显式 Migration）+ DataStore |
| Markdown | Markwon + 自研插件（LaTeX / 图表 / 预处理 / 样式） |
| Git | JGit（公开 GitHub 仓库浅克隆 / pull） |
| 异步 | Kotlin Coroutines + Flow |
| 测试 | JUnit 4、Robolectric、MockK |
| CI | GitHub Actions（Lint / 构建 / 单元测试 / Release） |

## 项目结构

```
app/src/main/java/
├── space/liushenme/markdownreader/
│   ├── data/              # Room 实体/DAO、DataStore、Repository
│   ├── di/                # Hilt 模块
│   ├── git/               # GitHub 仓库解析、JGit 克隆/pull、工作区索引、文档打开
│   ├── importing/         # 书籍导入、PDF 提取、TOC 解析
│   ├── intent/            # 外部 Intent 打开文件
│   ├── markdown/          # Markwon 工厂、预处理、LaTeX/图表/代码样式
│   ├── model/             # 主题、翻页模式等枚举
│   ├── navigation/        # 路由
│   ├── platform/          # 平台工具
│   └── ui/
│       ├── components/    # 通用 Compose 组件
│       ├── screens/       # bookshelf / project / reader / profile / notes / statistics / weblink
│       ├── system/        # 系统栏
│       └── theme/         # 应用主题与阅读颜色/背景
└── io/noties/markwon/ext/latex/   # 行内 LaTeX 对齐、复合公式 Span 等定制
```

主要 Markdown 扩展代码位于 `markdown/` 包，例如：

- `ReaderMarkwonFactory.kt` — Markwon 组装入口
- `MarkdownPreprocessor.kt` — 脚注、图表围栏、块级公式展开等
- `ReaderLatexPreprocessor.kt` — `\ce`、`\cancel`、`\oiint` 等 JLatex 兼容改写
- `CeLatexConverter.kt` — mhchem 化学式子集转换
- `DiagramImagesPlugin.kt` — Mermaid / ECharts 图表

## 使用方法

1. 打开应用，点击「导入书籍」
2. 从文件管理器选择 Markdown / TXT / PDF，或通过分享 /「打开方式」导入
3. 在书架点击书籍开始阅读
4. 长按文本可选择内容并划线或添加书签
5. 在阅读界面或「我的 → 阅读设置」中调整主题、字号、翻页方式、是否隐藏系统栏等

## 构建与测试

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK API 35

### Debug 构建

```bash
./gradlew assembleDebug
```

Debug 构建**不需要** Release 签名配置。

### Release 构建

在 `local.properties`（本机，勿提交 Git）或 CI 环境变量中配置签名，**未配置时 `assembleRelease` 会失败**：

```properties
RELEASE_STORE_FILE=/绝对路径/你的/release.keystore
RELEASE_STORE_PASSWORD=你的库密码
RELEASE_KEY_ALIAS=你的别名
RELEASE_KEY_PASSWORD=你的密钥密码
```

也可使用同名环境变量（适用于 GitHub Actions）。

```bash
./gradlew assembleRelease
```

发版时请同步更新根目录 [latest.json](latest.json) 的 `versionName`、`versionCode`、`pageUrl`，供应用内检查更新。

> 请勿将 keystore 或密码写入仓库。`signing/` 与 `*.keystore` 已在 `.gitignore` 中忽略。

### 运行测试

```bash
# 全部单元测试
./gradlew testDebugUnitTest

# Lint（CI 中为 continue-on-error）
./gradlew lintDebug
```

单元测试以 Robolectric 为主，覆盖 Markdown 预处理、LaTeX 渲染、分页引擎、TOC 跳转等场景（见 `app/src/test/`）。

## 权限说明

应用仅声明 **`INTERNET`**（用于 URL 导入书籍、加载 Markdown 中的网络图片等）。

- **不**使用 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
- 本地文件通过系统文档选择器（SAF）以 `content://` URI 读取
- 导出笔记由用户通过系统「另存为」选择保存位置

## 相关文档

- [CHANGELOG.md](CHANGELOG.md) — 版本变更记录
- [CONTRIBUTING.md](CONTRIBUTING.md) — 贡献指南
- [SECURITY.md](SECURITY.md) — 安全政策
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — 行为准则

## 开源协议

[MIT License](LICENSE)
