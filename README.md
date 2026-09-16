[中文](README.zh-CN.md) | [日本語](README.ja.md)

## Overview

* NieJianYing Android shell (fork of [siyuan-android](https://github.com/siyuan-note/siyuan-android))
* `applicationId`: `cn.niejianying.niejianying` (debug suffix `.debug`)
* Daily / release workflow: see [`niejianying/siyuan` docs/ANDROID-RELEASE.md](https://github.com/niejianying/siyuan/blob/master/docs/ANDROID-RELEASE.md)

## Daily development (recommended)

From the sibling `siyuan` repo (do not go through Flutter):

```bash
cd ../siyuan
node scripts/android-dev-run.mjs --flavor=official
```

Builds desktop UI, packs a trimmed `app.zip`, gomobile `kernel.aar`, writes into this repo, then installs the debug APK.

## Manual setup

1. Follow the [SiYuan Development Guide](https://github.com/siyuan-note/siyuan/blob/master/.github/CONTRIBUTING.md) to build the kernel, or use the one-shot script above
2. Use `siyuan/scripts/package-android-app-zip.sh` to produce trimmed `app/src/main/assets/app.zip`
   * appearance / guide / stage / changelogs (covers, LXGW, extra langs excluded)

Directory structure reference:

![project-tree](project-tree.png)

![app.zip](app-zip.png)

## About Multi-Channel Software Distribution

If you are building your program using the Android Studio method of going to 【Build】,【Generate Signed Bundle APK...】, you only need to modify the `siyuanVersionName` and `siyuanVersionCode` within the build.gradle file at the project level. After making the changes, you can directly package the app and ignore the following content.

### Steps

**The following content is only necessary when building via the command line console**:

When building using the command line console, you not only need to modify the `siyuanVersionName` and `siyuanVersionCode` within the build.gradle file at the project level, but you also need to perform the following steps:

1. Copy the `signings.templates.gradle` file and rename it to `signings.gradle`.
2. Configure the related information in `signings.gradle`.
3. Use the command line to navigate to the root directory of the project and execute the following
   ```shell
   # windows
   .\gradlew clean buildReleaseTask
   
   # linux / macOS
   ./gradlew clean buildReleaseTask
   ```

   The naming convention is as follows:

   ```txt
   assemble/bundle  Googleplay  Debug/Release
   ```

   `assemble` generates APKs
   `bundle` generates AABs
   `Googleplay` is the name of the channel package; refer to the `productFlavors {}` configuration in flavors.gradle for the specified location
   `Debug/Release` stands for Test version/Official version
4. After the execution is complete, you can find the generated program at the following location
   ```txt
   siyuan-android/app/build-release/siyuan-${versionName}-all
   ```
