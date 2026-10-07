# 照片水印（PhotoEditor）

一个自用的安卓 App：把照片自带的拍摄信息（时间、机型、镜头、焦距、光圈、快门、ISO）和位置印到照片上，另存为新图，原图不动。

## 安装

用手机浏览器打开 [Releases 里的"最新测试版"](https://github.com/fenghanyue/PhotoEditor/releases/tag/latest-dev)，下载 APK 后直接安装。第一次安装时，需要允许浏览器"安装未知应用"。

每次往仓库推送代码，GitHub Actions 都会自动重新编译并更新这个页面。App 首页显示的版本号（如 `0.1.0-build12`）就是编译序号，可以用来核对装的是不是最新版。

## 进度

- [x] M0 搭架子：工程、自动编译、固定签名
- [x] M1 读信息：相册页、照片信息详情页
- [ ] M2 出图：参数边框、信息叠加两套模板，预览和导出
- [ ] M3 位置：离线省/市/区县查询和手动选择
- [ ] M4 批量和设置：批量导出、机型别名、默认值

## 自己编译

需要 JDK 17 以上和 Android SDK（compileSdk 37）：

```
./gradlew assembleRelease
```

APK 在 `app/build/outputs/apk/release/` 下。

## 注意

`app/debug.keystore` 是公开的测试签名，只是为了让每个新版本都能直接覆盖安装。不要用它签名要发给公众的 App。
