# 贡献指南 (Contributing Guidelines)

欢迎参与 MarkdownReader 项目的开发！在提交贡献之前，请阅读以下指南。

## 如何贡献

### 1. 报告问题 (Issues)
- 使用 GitHub Issues 报告 Bug 或提出新功能建议
- 在报告问题前，请先搜索现有 Issues，避免重复
- 报告 Bug 时请包含：
  - 设备型号和 Android 版本
  - 应用版本
  - 复现步骤
  - 预期行为和实际行为
  - 截图或录屏（如有）

### 2. 提交代码 (Pull Requests)

#### 分支管理
- `main` - 稳定分支，用于发布
- `develop` - 开发分支，所有新功能应基于此分支

#### 提交代码步骤
1. Fork 本仓库
2. 从 `develop` 分支创建新分支：
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. 开发并提交代码
4. 确保本地构建通过
5. 提交 Pull Request 到 `develop` 分支

#### 提交信息规范
请使用语义化提交信息：
```
<type>(<scope>): <subject>

<description>
```

类型 (type)：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式调整（不影响代码逻辑）
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/依赖相关

示例：
```
feat(reader): 添加自动翻页功能

- 添加自动翻页开关
- 支持自定义翻页间隔
- 更新阅读设置页面
```

### 3. 代码风格规范

#### Kotlin 代码风格
- 使用官方 Kotlin Code Style
- 运行 `ktlint` 进行格式化
- 遵守 ktlint 检查（建议配置为 Git Hook）

#### Android 最佳实践
- 使用 Jetpack 组件库
- 遵循 MVVM 架构模式
- 使用 Hilt 进行依赖注入
- 使用 Kotlin 协程处理异步操作

#### 注释规范
- 公共 API 必须包含 KDoc 注释
- 复杂逻辑需要补充行内注释
- 保持注释与代码同步更新

### 4. 开发环境设置

#### 前置要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK API 35

#### 构建项目
1. 克隆仓库
2. 在 Android Studio 中打开项目
3. 等待 Gradle 同步完成
4. 连接设备或启动模拟器
5. 点击运行按钮

#### 运行测试
```bash
# 单元测试
./gradlew testDebugUnitTest

# Lint 检查
./gradlew lintDebug
```

### 5. 开发流程建议

1. 从小的功能或修复开始，逐步熟悉代码库
2. 对于大的功能变更，建议先在 Issue 中讨论方案
3. 保持代码变更专注，一个 PR 只做一件事
4. 充分测试你的更改，确保没有引入新问题

## 行为准则

请尊重每一位贡献者和用户，保持友好和专业的交流态度。

## 许可证

通过提交代码，你同意你的贡献将使用项目的 [MIT 许可证](LICENSE)。

## 获得帮助

如有任何问题，请通过以下方式联系：
- 提交 Issue
- 讨论区提问

感谢你对 MarkdownReader 的支持！

