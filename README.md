# 照片水印（PhotoEditor）

一个自用的安卓 App：把照片自带的拍摄信息（时间、机型、镜头、焦距、光圈、快门、ISO）和位置印到照片上，另存为新图，原图不动。

## 安装

用手机浏览器打开 [Releases 里的"最新测试版"](https://github.com/fenghanyue/PhotoEditor/releases/tag/latest-dev)，下载 APK 后直接安装。第一次安装时，需要允许浏览器"安装未知应用"。

每次往仓库推送代码，GitHub Actions 都会自动重新编译并更新这个页面。App 首页显示的版本号（如 `0.1.0-build12`）就是编译序号，可以用来核对装的是不是最新版。

## 进度

- [x] M0 搭架子：工程、自动编译、固定签名
- [x] M1 读信息：相册页、照片信息详情页
- [x] M2 出图：参数边框、信息叠加两套模板，预览和导出
- [ ] M3 位置：离线省/市/区县查询和手动选择
- [ ] M4 批量和设置：批量导出、机型别名、默认值

## 怎么用

在相册里点一张照片，进入"加水印"页：选模板（参数边框 / 信息叠加），调好选项，点"保存到相册"。新图存在 `Pictures/PhotoEditor/`，文件名后面加 `_wm`，原图不动。右上角的 ⓘ 可以查看照片里读出来的全部信息。

## 自己编译

需要 JDK 17 以上和 Android SDK（compileSdk 37）：

```
./gradlew assembleRelease
```

APK 在 `app/build/outputs/apk/release/` 下。

## 注意

`app/debug.keystore` 是公开的测试签名，只是为了让每个新版本都能直接覆盖安装。不要用它签名要发给公众的 App。

## 字体和 Logo 的来源

- 水印字体是思源黑体（Noto Sans SC）的常用字子集，使用 SIL Open Font License 1.1，授权文本见 `app/src/main/assets/fonts/OFL.txt`。子集由 `tools/fonts/subset_noto_sc.py` 生成。
- 尼康 Logo 来自 Wikimedia Commons（File:Nikon_Logo.svg），在版权上属于公有领域，商标归尼康所有，这里只用于个人照片水印。
