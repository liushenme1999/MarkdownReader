# AGENT.md — 本仓库的协作与操作约定

面向前来修改本仓库的 AI/工程协作者。记录**必须遵守的流程**、**踩过的坑**、**关键设计约束**。
改动涉及这些内容时，先读本文件。

---

## 1. 项目速览

| 项目 | 值 |
|---|---|
| 应用 | MD 阅读器（Markdown Reader） |
| applicationId | `space.liushenme.markdownreader` |
| 测试包 | `space.liushenme.markdownreader.test` |
| 技术栈 | Kotlin + Compose + Hilt + Room + DataStore；Markwon 渲染 Markdown |
| SDK | minSdk 24 / targetSdk 35 / compileSdk 35 |
| 版本号位置 | `app/build.gradle.kts` 的 `versionCode` / `versionName` |
| Release 签名 | `local.properties` 的 `RELEASE_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD`（不入库） |
| APK 命名 | `${apkBaseName}_${buildType}_${versionName}.apk`，`renameReleaseApk` 任务会把产物改名并复制到 `app/release/`，同时删掉旧版本包 |

构建产物：`app/release/MD阅读器_release_<版本>.apk`（仓库只保留最新正式包）。

---

## 2. 常用命令

```bash
# 单元测试 + 静态检查（提交前必跑）
./gradlew :app:testDebugUnitTest detekt --offline

# 只跑某个测试类
./gradlew :app:testDebugUnitTest --tests "*ReaderTextLinkTouchTest*" --offline
# 强制重跑（Gradle 会缓存 UP-TO-DATE，改完断言务必确认真的跑了）
./gradlew :app:testDebugUnitTest --tests "*XxxTest*" --rerun-tasks --offline

# Release APK（会顺带改名/复制到 app/release/）
./gradlew :app:assembleRelease --offline

# 覆盖安装到已连接设备（自动点掉 MIUI 的 USB 安装确认框）
python3 scripts/adb_install_confirm.py                 # 默认 app/release 最新包
python3 scripts/adb_install_confirm.py path/to/x.apk

# 启动应用
adb shell am start -n space.liushenme.markdownreader/.MainActivity

# 安装 debug + 测试包，跑设备端 Instrumentation 测试
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline
adb uninstall space.liushenme.markdownreader          # debug 与 release 签名不同，必须先卸载
python3 scripts/adb_install_confirm.py "app/build/outputs/apk/debug/MD阅读器_debug_<版本>.apk"
python3 scripts/adb_install_confirm.py "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb shell am instrument -w -r \
  -e class space.liushenme.markdownreader.FormatImportMatrixInstrumentedTest \
  space.liushenme.markdownreader.test/androidx.test.runner.AndroidJUnitRunner
```

---

## 3. 安装：MIUI 会拦截 adb 安装（必读）

测试机是小米（Android 15 / MIUI）。通过 adb 安装时系统会弹一个约 10 秒的
「USB安装提示 — 正在通过 USB 安装此应用，是否继续？」对话框：

- 不点「继续安装」→ `adb install` 超时，报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`
- 重启手机后「通过 USB 安装」授权会失效，需要重新确认
- 覆盖安装同一签名一般不再弹；**卸载后重新安装一定弹**
- 卸载 debug/release 会清空应用数据（书架/进度），测试前先告知用户

**处理方式：一律用 `scripts/adb_install_confirm.py` 安装**，它会并行轮询 uiautomator、
命中「继续安装」按钮立即点击，并校验安装后的 `versionName`。
不要手写 `adb install` 然后干等。

---

## 4. 发布流程（发版检查清单）

1. 改版本号：`app/build.gradle.kts` 的 `versionCode` +1、`versionName` 更新（语义化 patch/minor）
2. `CHANGELOG.md` 顶部加 `## [x.y.z] - YYYY-MM-DD`，按 `修复 / 优化 / 新增 / 测试` 分组
3. `README.md`：`当前版本` 行 + 下载表格行（文件名 `MD._release_<版本>.apk`）
4. `latest.json`：`versionName` / `versionCode` / `pageUrl`（指向新 tag 的下载地址）
5. `./gradlew :app:testDebugUnitTest detekt --offline` 全绿
6. `./gradlew :app:assembleRelease --offline`，确认 `app/release/` 只剩新包
7. 校验 APK：`aapt dump badging app/release/*.apk | head -1`（versionCode/versionName 正确）、
   `apksigner verify --print-certs`（证书 SHA-256 `cd64b679…61c239a`）
8. 装机验证（用第 3 节的脚本），至少跑一遍「打开书 → 翻页 → 退出 → 重进」
9. 提交：`release: vx.y.z`；打 tag `vx.y.z` 并 push
10. 发布资产名必须与 README/latest.json 一致：
    `gh release create vx.y.z "app/release/MD阅读器_release_x.y.z.apk#MD._release_x.y.z.apk" --title vx.y.z --notes-file …`

> 只做了「打包」不等于发布：tag / Release 资产 / latest.json 三处必须对齐，否则应用内检查更新会 404。

---

## 5. 测试体系（新增功能请按此补测）

| 层级 | 位置 | 说明 |
|---|---|---|
| 纯逻辑单测 | `app/src/test/java/...`（Robolectric） | `@Config(sdk=[28])`，需要真实布局/图形的加 `@GraphicsMode(NATIVE)` |
| 导入矩阵 | `SampleFormatImportMatrixTest` + `ImportPickerCompatibilityTest` | 22 份真实样本逐一走导入管线；样本在 `app/src/androidTest/assets/samples/` |
| 设备矩阵 | `app/src/androidTest/.../FormatImportMatrixInstrumentedTest` | 真实 `PdfRenderer` / `ContentResolver`，验证 PDF 页图与 MIME |
| 阅读器 UI 设备测试 | `CodePreviewDeviceAcceptanceTest` / `SafeReaderTextViewSelectionInstrumentedTest` | Compose UI Test + 截图，会往 `filesDir` 写截图 |
| 手工/脚本 E2E | adb + `uiautomator dump` + `screencap` | 仓库无 UI 自动化框架时用它；注意下文坑 |

**样本目录约定**：`app/src/androidTest/assets/samples/` 同时被 JVM 矩阵测试读取
（JVM 测试资源会排除 `.java/.kt`，所以不能放 `src/test/resources`）。
新增格式/改动导入管线时，把样本加进该目录并让矩阵断言覆盖。

**矩阵测试的硬性要求**：
- 断言正文非空、无占位文案、无 `\uFFFD`、中文完整
- 代码文件断言语言识别 + 缩进/长行不裁剪
- PDF 在 JVM 只断言「路由到提取器」，页图渲染由设备测试覆盖
- 样本集必须覆盖 `ImportedBookFormat` 全部枚举值

---

## 6. 阅读器的关键设计约束（改之前必读）

### 6.1 坐标必须区分「全书坐标」与「窗口局部坐标」

- 大 TXT 使用**窗口模式**：只加载阅读位置附近若干分段（`TxtSegmentIndex` + `DocumentRepository.openTxtWindowMode`）。
- `ReaderViewModel` 的会话阅读位置 **一律是全书坐标**；`ReaderScreen` 的 pageSpecs / TextView 偏移是**窗口内局部坐标**。
- 进窗口模式后，`uiState.windowStartChar` 是唯一换算基准：
  `全局 = 局部 + windowStartChar`，`restoreLocalCharPos() = readingCharPosForRestore() - windowStartChar`。
- 违反此约定会出现：顶栏章节名错、退出后位置回退、跳目录落到别处。

### 6.2 分页与阅读位置

- 纯文本横向分页由 `ReaderPagingEngine.splitTextToPagesByLines` 生成；页高必须用**页视图实测**（raw padding，不能用 `totalPadding`）。
- 打开书时保留 `pendingRestoreGlobalChar` 作为精确恢复目标；用户真正翻页后才清除。
- 退出/切后台落盘用 `persistCurrentReadingPositionNow()`（按全书长度裁剪），**不要**用当前页 TextView 起点或窗口长度。
- 分页重排（页高变化、系统栏显隐）不得改写阅读位置：页码 collector 只在「非重排首帧 + 非退出中 + 非工具栏可见」时更新进度。

### 6.3 链接点击命中

- Markdown 链接命中在 `ReaderTextLinkTouch.findClickableSpanAt`：先按字符偏移，再按「span 在本行字形横向范围」兜底；**不允许**「本行有链接就命中」。
- 表格单元格由 `ReaderTableRowSpan`（ReplacementSpan）内部 `StaticLayout` 绘制，外层取不到链接；
  表格内链接必须走 `ReaderTableLinkTouch`（按 Markwon 绘制原点换算进单元格）。

### 6.4 其它

- 「标记为已读完」提示必须等用户真实翻页/滑动后才允许弹（短文档打开即到底，不能一进来就弹）。
- 章节名/目录 offset 用全书坐标；`visibleChapterOffset` 只存全书 offset。
- PDF 导入是逐页栅格化成 jpg（`PdfBookExtractor`），不是文本层。

---

## 7. 已知的坑（都真实踩过）

1. **R8 会删掉 `Log.v/d/i`**：release 包里只剩 `Log.e`/`Log.w`。临时诊断用 `Log.e`，**提交前必须删除**，
   并用 `unzip -p app/release/*.apk classes*.dex | strings | grep <TAG>` 确认没残留。
2. **Gradle 测试缓存**：改断言后 `UP-TO-DATE` 不会重跑，必须 `--rerun-tasks` 或核对 XML 时间戳。
3. **文档 MIME 不可信**：同一批源码文件被系统报成 `null` / `video/mp2ts` / `application/rls-services+xml`。
   选择器一律 `*/*`，由扩展名白名单判定；不要再用 MIME 白名单裁剪文件。
4. **DocumentsUI 多选**：`OpenMultipleDocuments` 支持长按 → 右上「全选」→「选择」，批量导入用它一次导入全部样本。
5. **adb 点击坐标 ≠ 截图坐标**：`input tap` 是屏幕坐标，事件进到 TextView 是 view-local（会减去状态栏/顶栏）。
   自动化点击前先 `uiautomator dump` 拿 bounds，不要凭截图目测。
6. **`screencap` 是二进制**：`adb exec-out screencap -p` 不能按文本解析，直接写文件。
7. **`uiautomator dump` 偶发 `null root node`**：等 1–2 秒重试。
8. **JVM 测试资源排除 `.java/.kt`**：矩阵样本必须放 androidTest assets，并从那里读。
9. **debug/release 签名不同**：装 debug 前先 uninstall，数据会丢；跑完设备测试记得装回 release。
10. **Compose UI 测试需要「空 Compose rule + 启动 Activity」**：参考 `CodePreviewDeviceAcceptanceTest` 的
    `createEmptyComposeRule()` + `launchMainActivity()` 写法，不要另起一套。

---

## 8. 提交规范

- 提交信息：`<type>(<scope>): <subject>`，例如 `fix(reader): 链接只在实际文字上响应点击`；
  发布提交用 `release: vx.y.z`。
- 每次改动必须：**单测 + detekt 通过**；涉及导入/阅读位置的改动补矩阵或回归测试。
- 不要提交：`local.properties`、debug 日志、临时 `Scratch*Test`、`/tmp` 产物。
- 测试样本（`app/src/androidTest/assets/samples/`）属于代码，可提交；`app/build/`、`build/` 不入库。
- 版本号、CHANGELOG、README、latest.json 四处必须同步（见第 4 节）。

---

## 9. 当前测试文档集

`app/src/androidTest/assets/samples/`（22 份，与推送到手机的 `Download/MDReader-TestDocs/` 同源）：

| 文件 | 覆盖 |
|---|---|
| `01/17/18/19-*.md` | 全功能渲染、链接命中、公式、Mermaid/ECharts |
| `02-纯文本-长篇测试.txt` | 140 章 / 17 万字符：分页、目录、窗口续读、历史位置 |
| `03-网页-HTML测试.html` | WebView 渲染 |
| `04-PDF-多页测试.pdf` | 4 页栅格化、翻页/缩放 |
| `05~16-code-*` | 12 种源码：高亮、行号、长行 |
| `00-测试说明.txt`、`000/001-link-hit-test*.md` | 说明与链接命中专项 |

新增格式时：**同时**补样本、补矩阵断言、更新本表。
