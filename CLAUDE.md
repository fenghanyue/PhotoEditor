# PhotoEditor 交接说明

自用的安卓 App：把照片里的拍摄信息（时间、机型、镜头、焦距光圈快门 ISO）和地点印成水印，另存新图，原图不动。用户自己和身边的朋友用，从 GitHub Releases 下载 APK 安装，不上架应用商店（所以可以印品牌 Logo）。

## 和用户协作的约定

- 用中文回复。用户说"看不懂"时，换成初中生都能懂的话重新解释，但不要说明是在简化。
- **改代码或生成具体文件内容之前，先把计划发给用户确认。** 用户明确说了怎么改的小事（比如"把 X 删了"）可以直接做。
- 技术问题要主动质疑：代码质量、数据质量、实现里的假设。同一个疑问不要换着说法反复问。
- 按里程碑推进：计划 → 用户确认 → 实现 → 本地编译、测试、lint → 推送 → CI 出 APK → 把下载链接和真机检查清单发给用户 → 用户真机验证。
- 用户用手机看 GitHub，浏览器常开着网页翻译；翻译会弄坏 GitHub 的设置页（改默认分支就失败过一次）。网页操作失败时，先让用户关掉翻译再试。

## 现状（2026-10-07）

- 正式版 0.3.0。M0 搭架子、M1 读信息、M2 出图、M3 离线地区都已完成，用户在真机上验证过。
- 待办都在 GitHub Issues：
  - #1 批量导出、#2 记住设置、#3 手机机型名称、#4 更多品牌 Logo：这四个是 M4。
  - #5 地区数据精度：待用户决定。
  - #6 水印样式重新设计：用户觉得现在两套模板都丑。动手前先让用户给参考样式。
- 用户的相机是尼康 Z5II，不记录 GPS。照片用读卡器拷到手机的 `Download/`。
  - EXIF 里机型写的是 `NIKON CORPORATION` / `NIKON Z5_2`。
  - 镜头写的是 `NIKKOR Z 24-200mm f/4-6.3 VR`。
- 已定的需求：
  - 两套模板：参数边框（徕卡/小米风）、信息叠加（打卡风）。
  - 照片来源：手机和相机拍的都要处理，App 里不做拍照。
  - 地址：离线查到区县。

## 技术栈

- Kotlin + Jetpack Compose（Material3），单模块 `:app`。依赖版本在 `gradle/libs.versions.toml`。
- 构建：AGP 9.4.1 自带 Kotlin 支持，没有 kotlin-android 插件。根 `build.gradle.kts` 用 buildscript classpath 把 KGP 换成 2.4.20，和 Compose 编译插件一致。
- Gradle 9.8.0（wrapper 带 sha256 校验）。compileSdk/targetSdk 37，minSdk 29，Java 17。

## 代码地图

代码在 `app/src/main/java/io/github/fenghanyue/photoeditor/` 下：

| 包 | 内容 |
|---|---|
| `media/` | `MediaRepository` 查媒体库；`MediaPermissions` 申请读照片和 `ACCESS_MEDIA_LOCATION` |
| `meta/` | `ExifReader` 读 EXIF；`ExifMetaParser` 解析成 `PhotoMeta`，每项标明来源；`ParamFormatter` 格式化参数；`DeviceNames` 整理机型名（NIKON Z5_2 → Nikon Z5II） |
| `render/` | `WatermarkOptions` 是用户选项，`WatermarkContent` 是要印的各行文字；`WatermarkRenderer` 调 `FrameTemplate`（参数边框）和 `OverlayTemplate`（信息叠加）；`WatermarkAssets` 管内置字体和 SVG Logo |
| `export/` | `PhotoExporter`：原尺寸解码 → 画水印 → JPEG 95 → 存到 `Pictures/PhotoEditor/`；`ExifCopier` 按白名单复制 EXIF |
| `geo/` | `RegionIndex` 读 `assets/regions.bin`，按坐标查区县，也能按名字搜；`Region`/`RegionStyle` 是地区和三种写法；`CoordTransform` 把 WGS-84 换成 GCJ-02 |
| `ui/` | `MainActivity` + `Route` 自己管页面栈；`gallery/` 相册；`editor/` 加水印页；`detail/` 照片信息页 |

`editor/` 里：`EditorViewModel` 管地区规则（有 GPS 自动查、在国外只印坐标、没有 GPS 沿用上一张），`RegionPicker` 是选地区的弹层。

界面文字都在 `res/values/strings.xml`（中文）。代码注释也用中文，写成外行也能看懂的样子。

## 踩过的坑

改相关代码前先看：

- **GPS 会被系统删掉。**
  - 读原图要有 `ACCESS_MEDIA_LOCATION` 权限，并用 `MediaStore.setRequireOriginal(uri)`。
  - 被删掉的 GPS 读出来是 0,0，当作没有。
  - 系统的照片选择器也会去掉位置，所以相册是用 MediaStore 自己做的。
- **ExifInterface（androidx 1.4.2）的几个怪处：**
  - 照片里没有方向标签时，它也会给出 Orientation 0。
  - 写错类型的标签会被忽略。
  - Robolectric 里 `ExifInterface(FileDescriptor)` 用不了，`ExifReader` 会退回用数据流读。
- **ImageDecoder 会按 EXIF 方向自动转正**，所以导出时方向写成"正常"。
- **导出的几个保护：**
  - 超过 6400 万像素、或者内存不够时，缩小一半再导出。
  - HDR 色彩空间（HLG/PQ）建不了 8 位图，退回 sRGB。
- **水印尺寸都按照片短边的百分比算**（unit = 短边/100），所以预览和导出效果一致。中文排版用 `EM_ASCENT = 0.88` 定基线。
- **字体：**`assets/fonts/` 是思源黑体的子集，只收了 GB2312、常用符号和地名用字。地名里出现新字时要重新生成字体，`RegionFontTest` 会检查有没有缺字。
- **地区数据：**
  - DataV 的边界用 GCJ-02 坐标，照片 GPS 是 WGS-84，查之前要换算，国外的点不换算。港澳和大陆是同一套坐标，核对过。
  - 不在任何边界里、但离最近的地区不到 2 公里的点（海边）算到那个地区。
  - 层级不统一：直辖市和港澳下面直接是区；东莞、仙桃这类市下面没有区县；台湾只有省一级。
- **导出的 EXIF：**按白名单复制，不带 MakerNote、缩略图、机身和镜头序列号。GPS 看用户的开关。
- **签名：**`app/debug.keystore` 是故意公开的测试签名，保证新版本能覆盖安装旧版本，不要换。

## 命令

```
./gradlew assembleRelease testDebugUnitTest lintDebug    # 和 CI 一样
./gradlew testDebugUnitTest --tests '*RegionIndexTest*'   # 只跑一个测试类
```

- 测试都在 `app/src/test`（JVM + Robolectric 4.17），没有真机测试。工作目录是 `app/`，测试直接读 `src/main/assets` 里的真实数据。
- 画图的测试用 `@GraphicsMode(NATIVE)`，效果图写到 `app/build/watermark-samples/`，可以直接打开图片检查。
- 界面测试用 Compose 的 `junit4.v2` API。
- lint 结果在 `app/build/reports/lint-results-debug.sarif`，要求 0 条。

## 云端会话的环境

- `.claude/hooks/session-start.sh` 在会话开始时自动运行：
  - 装好 Android SDK（`/root/android-sdk`）；
  - 写好 `local.properties`；
  - 把 Maven Central 指到 Google 的镜像（经这里的代理直连会返回 429）；
  - 预先下载编译依赖。
- 推送标签会被拒绝（403），所以发正式版用 Actions 里的按钮（见下）。推送分支没问题。
- GitHub 操作用 MCP 的 github 工具，没有 gh 命令。

## 分支和发版本

- **日常开发：**推到会话指定的 `claude/...` 分支。CI 会自动编译，并更新 Releases 里的"最新测试版"（latest-dev）。
- **发正式版：**里程碑完成、用户真机验证过之后，经用户同意：
  1. 在 `CHANGELOG.md` 写好这一版的说明，确认 `app/build.gradle.kts` 里的 `appVersion` 是这一版的版本号。
  2. 把 `main` 快进到同一个提交。
  3. 用 `actions_run_trigger` 运行 `android.yml`：ref 填 `main`，inputs 填 `{"release": "0.4.0"}`。用户自己也可以在 Actions 页面点 Run workflow。
  4. CI 会打标签 `v0.4.0` 并发正式版。
- **版本号：**和里程碑对应（M3 = 0.3.0）。开始做下一个里程碑时，就把 `appVersion` 改成下一个版本号，测试版会显示成 `0.4.0-build编译序号`。

## 工具脚本

在 `tools/` 下：

- `regions/build_regions.py`：从 DataV 下载边界，生成 `app/src/main/assets/regions.bin` 和 `tools/fonts/extra_chars.txt`。只用 Python 标准库，文件格式写在脚本开头。
- `fonts/subset_noto_sc.py`：下载思源黑体，裁成子集。需要 fontTools。
- `testdata/make_exif_fixtures.py`：生成测试用的 EXIF 图片。需要 Pillow。
