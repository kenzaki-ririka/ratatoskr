# 依赖与环境

## 语言与构建
- Kotlin + Android Gradle Plugin（AGP 8.x），要求 JDK 17。
- Gradle Wrapper：确保团队一致。

## 第三方库
- Assists（本地）：`3rd/assists`（基于 assists-base 能力）。
- 悬浮窗：`FloatingX` + `Compose`（已在 `app/build.gradle.kts` 中使用）。
- 推荐网络栈：OkHttp/Retrofit（用于 AI 接口）。

## 仓库配置
- JitPack（如需远端依赖）：`maven { url = uri("https://jitpack.io") }`（已在 `settings.gradle.kts` 配置）。

## 权限
- 无障碍：`android.permission.BIND_ACCESSIBILITY_SERVICE`。
- 悬浮窗：`SYSTEM_ALERT_WINDOW` 等相关权限。
- 剪贴板：系统剪贴板访问。

## 运行要求
- 设备需允许无障碍与悬浮窗；网络可用（远端 AI）。
- 低端设备需控制生成耗时与动画开销。

## 版本与兼容
- 最低 SDK：24；目标 SDK：35（当前工程设置）。
- 常见 IM（微信）布局兼容策略：节点健壮遍历与空值保护。
