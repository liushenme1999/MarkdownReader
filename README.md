# Markdown Reader - 微信读书风格的 Markdown 阅读器

一款类似微信读书的 Android Markdown 阅读应用，支持书架管理、阅读划线、书签、阅读统计等功能。

## 功能特性

### 书架管理
- 卡片式书籍展示，类似微信读书
- 从文件系统导入 Markdown 文件
- 显示阅读进度百分比
- 收藏功能
- 删除书籍

### 阅读器
- 5种阅读主题：纸质书、纯净白、护眼绿、复古棕、夜间模式
- 字体大小调节
- Markdown 渲染显示
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
- 近7天阅读趋势图
- 最近阅读书籍列表

### 数据导出
- 导出为 Markdown 格式
- 导出为 JSON 格式

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **架构**: MVVM + Repository 模式
- **依赖注入**: Hilt
- **数据库**: Room
- **Markdown 渲染**: Markwon

## 项目结构

```
app/src/main/java/com/example/markdownreader/
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

1. 打开应用，点击"导入书籍"按钮
2. 从文件管理器选择 Markdown 文件
3. 书籍会显示在书架上
4. 点击书籍开始阅读
5. 长按文本选择内容，可以划线或添加书签
6. 在阅读设置中可以切换主题和字体大小

## 构建说明

1. 使用 Android Studio 打开项目
2. 同步 Gradle 文件
3. 运行应用到设备或模拟器

## 权限说明

- `READ_EXTERNAL_STORAGE`: 读取 Markdown 文件
- `WRITE_EXTERNAL_STORAGE`: 导出笔记数据

## 开源协议

MIT License
