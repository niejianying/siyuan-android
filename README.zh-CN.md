[English](README.md) | [日本語](README.ja.md)

## 概述

* 捏见影 Android 壳（fork 自 [siyuan-android](https://github.com/siyuan-note/siyuan-android)）
* `applicationId`：`cn.niejianying.niejianying`（debug 后缀 `.debug`）
* 日常与发版：见 [`niejianying/siyuan` docs/ANDROID-RELEASE.md](https://github.com/niejianying/siyuan/blob/master/docs/ANDROID-RELEASE.md)

## 日常开发（推荐）

在 `siyuan` 仓库执行（勿再经 Flutter）：

```bash
cd ../siyuan
node scripts/android-dev-run.mjs --flavor=official
```

会构建 desktop 前端、裁剪打包 `app.zip`、gomobile `kernel.aar`，写入本仓 `app/libs` 与 `app/src/main/assets`，再安装 debug 包。

## 手动搭建

1. 参考[思源笔记开发指南](https://github.com/siyuan-note/siyuan/blob/master/.github/CONTRIBUTING.zh-CN.md)编译内核，或使用上方一键脚本
2. 使用 `siyuan/scripts/package-android-app-zip.sh` 生成裁剪后的 `app/src/main/assets/app.zip`
   * appearance / guide / stage / changelogs（已排除 covers、LXGW、多余语言等）

目录结构参考：

![project-tree](project-tree.png)

![app.zip](app-zip.png)

## 关于多渠道软件分发

如果你使用的是 Android Studio 的【Build】【Generate Signed Bundle APK...】的方式构建程序，只需要修改项目级的 build.gradle 文件内的
siyuanVersionName 和 siyuanVersionCode 两个版本号即可，修改完毕后直接打包，可忽略以下内容。

### 步骤

**以下内容仅仅是在控制台命令行执行时才需要配置**：

需要使用控制台命令行构建，不仅仅需要修改项目级的 build.gradle 文件内的 siyuanVersionName 和 siyuanVersionCode 版本号，还需要进行以下操作：

1. 将 signings.templates.gradle 复制一份，并且重命名为 signings.gradle
2. 配置 signings.gradle 相关信息
3. 使用控制台进入项目根目录并执行以下内容
   ```shell
   # windows
   .\gradlew clean buildReleaseTask
   
   # linux / macOS
   ./gradlew clean buildReleaseTask
   ```

   这里的命名规则是：
   
   ```txt
   assemble/bundle  Googleplay  Debug/Release
   ```
   
   `assemble` 生成 APK  
   `bundle` 生成 AAB  
   `Googleplay` 为渠道包名称，指定位置请看 flavors.gradle productFlavors {} 配置  
   `Debug/Release` 测试版/正式版  
4. 执行完成之后，你可以在以下位置找到生成好的程序
   ```txt
   siyuan-android/app/build-release/siyuan-${versionName}-all
   ```
