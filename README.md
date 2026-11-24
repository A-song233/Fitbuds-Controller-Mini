# FitBuds Controller Mini

适用于漫步者 EDIFIER FitBuds Pro 蓝牙耳机的轻量级第三方 Android 控制应用。本项目基于经典蓝牙 RFCOMM 私有控制协议实现，无需依赖官方客户端即可完成电量监控与各项耳机参数管理。界面基于 Jetpack Compose 与 Material 3 Expressive 构建。

## 主要功能

- **电量与佩戴状态监控**：实时读取左耳、右耳及充电仓电量，自动识别双耳、单耳及无在线耳机状态；支持前台每 45 秒自动轮询与手动下拉刷新。
- **降噪与环境声控制**：支持切换 `深度降噪`、`宽频降噪`、`防风噪`、`环境声` 与 `降噪关`，单耳佩戴时自动限制不可用的降噪档位。
- **预设音效（EQ）**：支持切换 `经典`、`古典`、`摇滚`、`流行` 四种内置音效，写入后自动回读校验。
- **低延迟游戏模式**：快速开启或关闭低延迟无线传输模式。
- **操控设置**：分别自定义左耳与右耳的 `轻触两下`、`轻触三下`、`长按` 手势动作，支持配置 2 至 3 种模式的降噪循环。
- **定时关机与远程关机**：支持设置 10、20、30、60、90 分钟自动关机或取消定时，并支持二次确认后远程关闭耳机。
- **找回耳机**：支持选择左耳、右耳或双耳，通过 FitBuds 蓝牙立体声（A2DP）音频通道播放约 3 秒提示音，并支持中途停止（播放前请先摘下耳机）。
- **设备发现与切换**：自动优先连接系统中已配对且名称包含 `FitBuds` 的设备，也支持手动从已配对蓝牙设备列表中指定目标设备。

## 系统要求与权限

- **系统版本**：Android 14（API 34）及以上。
- **蓝牙权限**：仅申请 `android.permission.BLUETOOTH_CONNECT`，用于读取系统已配对蓝牙设备列表及建立经典蓝牙 RFCOMM 连接，不申请定位权限或蓝牙扫描权限。
- **使用前提**：请先在系统蓝牙设置中完成与 EDIFIER FitBuds Pro 的配对。

## 从源码构建

### 构建环境

- JDK 21
- Android SDK Platform 37.0 与 Build-Tools 37.0.0（`compileSdk = 37`，`minSdk = 34`，`targetSdk = 36`）

项目在 `settings.gradle.kts` 中已优先配置阿里云 Maven 镜像源并保留官方仓库作为回退。

### 常用命令

运行单元测试：

```bash
./gradlew testDebugUnitTest
```

构建裁剪后的 Release APK（已开启 R8 代码与资源裁剪）：

```bash
./gradlew assembleRelease
```

生成的安装包位于 `app/build/outputs/apk/release/app-release.apk`。当前 `release` 构建类型默认复用本地 `debug` 签名配置以便直接安装测试，如需正式分发请在 `app/build.gradle.kts` 中替换为自定义签名配置。

## 项目结构

- `app/src/main/java/com/fitbuds/app/protocol/`：经典蓝牙 RFCOMM 帧编解码、增量帧解析及会话操作（纯 Kotlin 实现，可独立运行 JVM 单元测试）。
- `app/src/main/java/com/fitbuds/app/bluetooth/`：Android 已配对设备查询与 RFCOMM 套接字传输实现。
- `app/src/main/java/com/fitbuds/app/audio/`：找回耳机立体声 PCM 提示音合成与蓝牙 A2DP 定向播放。
- `app/src/main/java/com/fitbuds/app/controller/`：串行任务队列、指令写入间隔节流、状态同步与 ViewModel。
- `app/src/main/java/com/fitbuds/app/ui/`：基于 Material 3 Expressive 的主题与界面组件。

## 免责声明

本项目为个人学习与硬件互通性研究开发的第三方开源工具，与漫步者（EDIFIER）官方无任何关联。使用本软件所产生的任何风险由使用者自行承担。
