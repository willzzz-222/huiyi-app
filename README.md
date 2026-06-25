# Personal Memories

一个只给自己用的 Android 相册刷流应用。它从手机本地相册读取照片和视频，像短视频应用一样上下滑动浏览；按整轮洗牌生成随机队列；点赞、文字记录和语音旁白只保存在本机，不上传。

## 功能

- 读取本机图片和视频，支持 Android 13-15 的全部/部分授权。
- 竖向全屏滑动浏览，照片和视频混排。
- 整轮随机洗牌，队列内不重复。
- 本地点赞。
- 多条文字记录。
- 语音旁白录制、试听、保存、播放和删除。
- 调用系统确认界面将原媒体移入相册回收站。
- 本地 Room 数据库和 DataStore 设置，无 `INTERNET` 权限。

## 构建

1. 用 Android Studio 打开本目录。
2. 等待 Gradle 同步。
3. 连接 Android 手机。
4. 点击 Run 安装到手机。

首次打开时需要允许读取照片和视频权限。

## 技术

- Kotlin
- Jetpack Compose
- Android MediaStore
- AndroidX Media3 ExoPlayer
- Room
- DataStore
- MediaRecorder
