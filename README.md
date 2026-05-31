# Markdown Reader - 微信读书风格的 Markdown 阅读器

一款类似微信读书的 Android Markdown 阅读应用，支持书架管理、阅读划线、书签、阅读统计等功能。

**应用 ID**：`space.liushenme.markdownreader`

## 功能特性

### 书架管理
- 卡片式书籍展示，类似微信读书
- 从文件管理器导入 Markdown / TXT / PDF
- 支持通过「用其他应用打开」或分享导入
- 显示阅读进度百分比
- 收藏、分组与删除

### 阅读器
- 5 种阅读主题：纸质书、纯净白、护眼绿、复古棕、夜间模式
- 字体大小调节
- Markdown 渲染（含表格、任务列表、LaTeX、Mermaid/ECharts 图表等）
- 阅读进度保存

### 划线与笔记
- 文本选择划线
- 多种高亮颜色（黄、绿、青、粉、橙）
- 添加书签笔记
- 笔记管理页面

### 书签功能
- 快速添加书签
- 书签列表查看
- 跳转到书签位置
- 书签笔记

### 阅读统计
- 书籍总数、已读完数量
- 阅读时长统计
- 阅读字数统计
- 书签和划线数量
- 近 7 天阅读趋势图
- 最近阅读书籍列表

### 数据导出
- 导出为 Markdown 格式
- 导出为 JSON 格式

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **架构**: MVVM + Repository 模式
- **依赖注入**: Hilt
- **数据库**: Room（显式 Migration，不使用破坏性回退）
- **Markdown 渲染**: Markwon

## 项目结构

```
app/src/main/java/space/liushenme/markdownreader/
├── data/
│   ├── local/
│   │   ├── entity/          # 数据库实体
│   │   ├── dao/             # 数据访问对象
│   │   ├── AppDatabase.kt   # 数据库
│   │   └── Converters.kt    # 类型转换器
│   └── repository/          # 数据仓库
├── di/
│   └── AppModule.kt         # 依赖注入模块
├── ui/
│   ├── screens/
│   │   ├── bookshelf/       # 书架页面
│   │   ├── reader/          # 阅读器页面
│   │   ├── notes/           # 笔记管理页面
│   │   └── statistics/      # 统计页面
│   └── theme/               # 主题配置
└── MainActivity.kt
```

## 使用方法

1. 打开应用，点击「导入书籍」
2. 从文件管理器选择 Markdown / TXT / PDF，或通过分享 /「打开方式」导入
3. 书籍会显示在书架上
4. 点击书籍开始阅读
5. 长按文本选择内容，可以划线或添加书签
6. 在阅读设置中可以切换主题和字体大小

## 构建说明

### 日常开发（Debug）

1. 使用 Android Studio 打开项目
2. 同步 Gradle 文件
3. 运行 `assembleDebug` 或直接在设备/模拟器上 Run

Debug 构建**不需要** Release 签名配置。

### Release 构建

Release 包必须在 `local.properties`（本机，勿提交 Git）或 CI 环境变量中配置签名，**未配置时 `assembleRelease` 会直接失败**：

```properties
RELEASE_STORE_FILE=/绝对路径/你的/release.keystore
RELEASE_STORE_PASSWORD=你的库密码
RELEASE_KEY_ALIAS=你的别名
RELEASE_KEY_PASSWORD=你的密钥密码
```

也可使用同名环境变量（适用于 GitHub Actions 等 CI）。

```bash
./gradlew assembleRelease
```

> 请勿将 keystore 或密码写入仓库。`signing/` 与 `*.keystore` 已在 `.gitignore` 中忽略。

## 权限说明

应用仅声明 **`INTERNET`**（用于从网址导入书籍、加载 Markdown 中的网络图片等）。

- **不**使用 `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`
- 本地文件通过系统文档选择器（SAF）以 `content://` URI 读取，无需存储权限
- 导出笔记由用户通过系统「另存为」选择保存位置

## 开源协议

MIT License
