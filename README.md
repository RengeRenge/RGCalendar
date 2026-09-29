# 桌面日程（RGCalendar）

华为手机桌面日程小工具（Android AppWidget）。读取系统日历，在桌面上用极简卡片 / 可滚动列表展示接下来要发生的日程。

> 本应用走 **Android APK + AppWidget** 路线。最初评估过鸿蒙原生服务卡片：该设备 HarmonyOS 版本低于 API 10，Calendar Kit 不存在，且第三方无法申请 `READ_WHOLE_CALENDAR` 权限，故放弃。

## 功能

- **紧凑卡片**：最多显示最近 4 条日程，分组标题（今天 / 明天 / N天后）弱化、首条日程突出、重点日（法定假日、调休、生日 / 纪念日）高亮
- **列表小工具**：可滚动，不限条数，按日期分组展示接下来一年的日程
- **节假日数据**：联网获取（优先 `timor.tech/api/holiday/year/{年}`，备选 `holiday-cn`），缓存到 SharedPreferences，离线可用
- 卡片背景复刻华为系统「日历」服务卡片：45° 紫渐变 + 16dp 圆角，四边内缩按真机像素逐边校准

## 权限

- `READ_CALENDAR`：读取系统日历
- `INTERNET`：获取节假日数据

首次添加小工具后需打开应用授权日历权限。

## 构建

不依赖 Android Studio / Gradle，纯命令行：

```
.tools\build-app.bat
```

构建链：`aapt2 compile → aapt2 link → javac → d8 → jar → zipalign → apksigner`，输出 `app\RGCalendar.apk`，成功打印 `BUILD_OK`。

**首次构建前需要装好工具链**（Android SDK build-tools 34.0.0、JDK 17、aapt2 等），参见 `.tools\setup-sdk.ps1` 与 `.tools\setup-toolchain.ps1`。签名密钥缺失时脚本会自动生成 `debug.keystore`。

## 安装

```
adb install -r app\RGCalendar.apk
```

## 目录结构

```
app\       主应用（两个小工具 + 主界面）
probe\     早期可行性验证程序（探针）
.tools\    构建脚本与工具链（SDK / JDK 不入库）
```
