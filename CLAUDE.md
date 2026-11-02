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

## 0.4.0 版本范围（2026-11-01）

- 本次发版包含机型名称的手动输入/选择切换、尼康 Z5 II 和一加 15T 目录，以及下拉选择后点"确定"才应用的交互。
- 用户已确认将本次修改合并到 main 并发布 0.4.0；该版本只完成 M4 的机型选择部分，其余待办继续保留。
- 用户检查过本地测试包并反馈了品牌选择没有展开的问题；下方记录了对应的交互调整。发布构建和安装效果的状态以当次任务汇报为准。

## M4 后续进展（2026-10-31）

- 开发版本号进入 0.4.0，已加入机型目录选择：相机 → 尼康 → Z5 II，手机 → 一加 → 15T。
- 参数边框的机型名称分为互斥的"手动输入"和"选择机型"。选择后显示不可编辑的卡片，点"更换"重新选；品牌和型号列表不显示勾选图标。
- 手填内容和选择结果分别保留，按原始 EXIF 的品牌和型号在本次运行中记忆。点"确定"才生效，浏览或取消不会覆盖原设置。
- 水印的品牌和显示名一起使用选择结果；手动模式使用照片品牌。拍摄参数仍来自各张照片，EXIF 复制继续使用原始标签。
- `meta/DeviceCatalog` 维护机型目录；`editor/DeviceNameState` 管两种方式的内容；`DeviceNameEditor` 和 `DevicePicker` 分别负责输入区和选择弹层。
- #1 批量导出、#2 设置持久保存、#3 常见手机型号代码识别、#4 更多 Logo 仍待完成。

### 机型选择交互调整（2026-11-01）

- 首版把唯一品牌默认选中并平铺展示，点击同一品牌没有可见变化。品牌和型号现改为下拉框，只有一个选项时也能点开再选。
- 弹层内保存临时品牌、型号；选中型号后仍留在弹层，点底部"确定"才回填名称、切换品牌并关闭。
- 未选完整时确定按钮不可用。切换相机/手机时清空品牌、型号；切换品牌时清空不匹配的型号。重新打开时显示上次已经确定的机型。

## 拍摄参数编辑测试版（2026-11-02）

- 用户确认先做一版试用，开发版本进入 0.4.1；本次不更新 `.github/release-version`，正式版仍为 0.4.0。
- 编辑弹层支持 EV、ISO、实际／等效焦距、光圈和快门。EV 用精确的三分之一档，光圈选择标准档位，原图的特殊值保留；未记录的项不填默认值。
- 草稿点确定才应用，关闭取消；恢复原值也先修改草稿。已确定的参数按图片 URI 在本次运行里记忆，不影响另一张同设备照片。
- `meta/CaptureParameters` 是参数快照与预设；`editor/CaptureParameterDraft` 解析、校验输入并保留未编辑快门的精度；`CaptureParameterEditor` 是弹层。
- `export/CaptureParameterExif` 在复制原标签之后覆盖确实修改的字段，处理光圈／快门 APEX 和 ISO 的关联标签；导出后重读核对再写入相册。`ExifMetaParser` 支持扩展 ISO。
- `WatermarkContent` 使用已应用的参数，参数行加入 EV；截图可从无参数状态补填，导出仍为 JPEG。
- 已知镜头两端的光圈范围来自尼康 NIKKOR Z 24–200mm 官方说明书；中间焦段不推测完整变焦光圈曲线。参数测试覆盖真实图片保存读回、截图、恢复与取消、特殊原值以及逐张隔离；交付状态以任务汇报为准。

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
- 早期云环境推送标签会被拒绝（403）；正式版的标签和 APK 仍由 GitHub Actions 创建，云端只提交发布版本文件。推送分支正常。
- GitHub 操作优先使用 MCP 的 github 工具。当前 Codex 云环境虽有 gh，但 API 访问受限，且连接器没有触发 Actions 的接口；不依赖云环境直接调用发布 API。

## 分支和发版本

- **日常开发：**推到会话指定的 `claude/...` 分支。CI 会自动编译，并更新 Releases 里的"最新测试版"（latest-dev）。
- **发正式版：**里程碑完成、用户真机验证过之后，经用户同意：
  1. 在 `CHANGELOG.md` 写好这一版的说明，确认 `app/build.gradle.kts` 里的 `appVersion` 是这一版的版本号。
  2. 将 `.github/release-version` 改成同一版本号，随代码一起合并到 `main`。
  3. `android.yml` 比较本次推送前后；只有 main 上发布版本文件变化，才自动准备正式版。编译、测试和代码检查全部通过后，由 CI 打标签并发布 APK。
  4. 跟进 Actions 的实际结果，检查 Releases 的标签和 APK，再给用户下载链接。已有完整正式版跳过，已有标签或不完整发布记录报错，不覆盖。
- **手动补发：**仍可在 Actions 页面 Run workflow，ref 选 `main`，`release` 填版本号；留空只编译。原来交接中提到的 `actions_run_trigger` 不在当前连接器里，不假定可用。
- **重试：**临时故障可重跑原任务；代码修复合并后，使用手动入口重试同一版本，重跑原任务仍使用原提交。仅推一个没有改发布版本文件的修复提交不会再次触发发布。
- **版本号：**和里程碑对应（M3 = 0.3.0）。开始做下一个里程碑时，就把 `appVersion` 改成下一个版本号，测试版会显示成 `0.4.0-build编译序号`。

### 自动发布入口调整（2026-11-01）

- 0.4.0 已由用户手动触发 Actions 发布。本次只调整后续的发版入口，发布版本文件从 `0.4.0` 起步，不新增 App 版本。
- main 按提交分别运行，不让普通提交取消正在进行的发版；开发分支保留取消旧编译的行为。
- 发布准备脚本先检查格式、App 版本、更新说明和已有发布，上传前再检查一次。GitHub 权限或网络错误会停止流程，不当作版本不存在。
- 后续新版本的自动发布需要通过实际发布再验收；本次配置上线的状态以任务汇报为准。

## 工具脚本

在 `tools/` 下：

- `regions/build_regions.py`：从 DataV 下载边界，生成 `app/src/main/assets/regions.bin` 和 `tools/fonts/extra_chars.txt`。只用 Python 标准库，文件格式写在脚本开头。
- `fonts/subset_noto_sc.py`：下载思源黑体，裁成子集。需要 fontTools。
- `testdata/make_exif_fixtures.py`：生成测试用的 EXIF 图片。需要 Pillow。
- `release/prepare_release.py`：Actions 的发布判断、版本和更新说明检查、重复发布保护。只用 Python 标准库；`python3 -B -m unittest discover -s tools/release -v` 检查触发条件及失败情况，不创建真实 Release。
