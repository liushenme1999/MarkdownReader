# 贡献指南 (Contributing Guidelines)

欢迎参与 [MarkdownReader](https://github.com/liukejun1999/MarkdownReader) 的开发！在提交贡献之前，请阅读以下指南。

## 如何贡献

### 1. 报告问题 (Issues)

- 使用 GitHub Issues 报告 Bug 或提出新功能建议
- 在报告问题前，请先搜索现有 Issues，避免重复
- 报告 Bug 时请包含：
  - 设备型号和 Android 版本
  - 应用版本（设置 → 关于，或 `versionName`）
  - 复现步骤
  - 预期行为和实际行为
  - 截图或录屏（如有）
  - **Markdown/LaTeX 问题**：请附上无法渲染的原文片段

### 2. 提交代码 (Pull Requests)

#### 分支管理

- `main` — 稳定分支，用于发布
- `develop` — 开发分支，新功能应基于此分支

#### 提交步骤

1. Fork 本仓库
2. 从 `develop` 创建分支：
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. 开发并提交代码
4. 确保本地构建与测试通过（见下文）
5. 向 `develop` 提交 Pull Request

#### 提交信息规范

```
<type>(<scope>): <subject>

<description>
```

类型 (type)：

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `style` | 格式调整（不影响逻辑） |
| `refactor` | 重构 |
| `test` | 测试 |
| `chore` | 构建 / 依赖 |

示例：

```
feat(markdown): 支持 mhchem 化学式预处理

- 添加 CeLatexConverter
- 在 MarkdownPreprocessor 中改写块级 $$ 内容
- 补充单元测试
```

### 3. 代码风格

- 使用官方 Kotlin Code Style
- 遵循 MVVM + Repository 分层
- 使用 Hilt 注入、协程处理异步
- 公共 API 补充 KDoc；复杂逻辑加简要行内注释
- **保持变更范围最小**，不要顺带修改无关代码

### 4. 项目结构速查

```
space.liushenme.markdownreader/
├── markdown/          # Markwon 插件与 LaTeX/图表扩展（改渲染优先看这里）
├── importing/         # 导入与 TOC
├── ui/screens/reader/ # 阅读器 UI 与分页
└── data/              # Room / Repository

io/noties/markwon/ext/latex/   # 行内 LaTeX Span 定制（与 Markwon 同包名）
```

### 5. 开发环境

#### 前置要求

- Android Studio Hedgehog (2023.1.1) 或更高
- JDK 17
- Android SDK API 35

#### 构建

```bash
./gradlew assembleDebug
```

#### 测试

```bash
# 全部单元测试
./gradlew testDebugUnitTest

# 指定 Markdown/LaTeX 相关测试
./gradlew testDebugUnitTest --tests "space.liushenme.markdownreader.markdown.*"

# Lint
./gradlew lintDebug
```

CI 会在 `push` / `pull_request` 到 `main`、`develop` 时自动运行 Lint、Debug 构建与单元测试；向 `main`/`develop` 推送时若配置了 `RELEASE_*` secrets 还会构建 Release APK。

### 6. 开发 Markdown / LaTeX 功能时

1. 解析前改写 → `MarkdownPreprocessor` / `ReaderLatexPreprocessor`
2. Markwon 插件注册 → `ReaderMarkwonFactory`
3. 行内 LaTeX 行为 → `io/noties/markwon/ext/latex/` 与 `ReaderSingleDollarLatexPlugin`
4. 新增渲染能力请补充 Robolectric 单元测试（`app/src/test/.../markdown/`）

> 行内 `$…$` 由 `ReaderSingleDollarLatexPlugin` 处理，**不要**在预处理阶段把 `$…$` 展开成 `$$…$$`（会变成块级换行）。

## 行为准则

请尊重每一位贡献者和用户，保持友好和专业的交流态度。详见 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。

## 许可证

通过提交代码，你同意你的贡献将使用项目的 [MIT 许可证](LICENSE)。

## 获得帮助

- 提交 [Issue](https://github.com/liukejun1999/MarkdownReader/issues)
- 阅读 [README.md](README.md) 了解功能与构建说明

感谢你对 MarkdownReader 的支持！
