# Android 34/35 兼容性更新说明

本文档说明了为支持Android 34（API 级别 34）和Android 35（API 级别 35）所做的兼容性更新。

## 主要问题

### 1. 前台消息接收问题
在Android 15上，应用在前台时无法接收FCM消息，主要原因：
- 通知权限处理不完整
- 前台/后台状态检测不准确
- FCM消息处理逻辑需要优化

### 2. 依赖版本过旧
- Firebase BOM版本从32.5.0更新到33.5.1
- Cordova Android版本要求从11.0.0提升到13.0.0
- Google Services插件版本更新

## 更新内容

### 1. plugin.xml 更新

#### 版本要求更新
```xml
<engines>
    <engine name="cordova" version=">=12.0.0"/>
    <engine name="cordova-android" version=">=13.0.0"/>
    <engine name="cordova-ios" version=">=6.0.0"/>
</engines>
```

#### Firebase依赖更新
```xml
<preference name="ANDROID_FIREBASE_BOM_VERSION" default="33.5.1" />
<preference name="GradlePluginGoogleServicesVersion" value="4.4.2" />
```

#### 权限和服务配置
```xml
<!-- 新增权限 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<!-- 服务配置更新 -->
<service android:name="by.chemerisuk.cordova.firebase.FirebaseMessagingPluginService" 
         android:exported="false"
         android:foregroundServiceType="dataSync">
```

### 2. FirebaseMessagingPlugin.java 更新

#### 权限请求改进
- 增强了Android 13+的POST_NOTIFICATIONS权限处理
- 改进了权限检查逻辑
- 添加了更好的错误处理

#### 前台消息处理优化
- 改进了前台/后台状态检测
- 增强了消息分发逻辑
- 添加了详细的日志记录

### 3. FirebaseMessagingPluginService.java 更新

#### 前台状态检测
```java
private boolean isAppInForeground() {
    ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    // ... 实现逻辑
}
```

#### 通知渠道改进
- 为Android O+创建了更完善的通知渠道
- 添加了渠道描述和特性配置
- 改进了通知显示逻辑

#### Android 34/35 兼容性
- 添加了UPSIDE_DOWN_CAKE（Android 34）的特殊处理
- 改进了通知优先级设置
- 优化了通知ID生成逻辑

## 使用说明

### 1. 更新Cordova平台
```bash
cordova platform remove android
cordova platform add android@13.0.0
```

### 2. 清理并重新构建
```bash
cordova clean android
cordova build android
```

### 3. 测试验证
在Android 34和35设备上测试：
- 前台消息接收
- 后台消息接收
- 通知权限请求
- 通知显示效果

## 注意事项

### 1. 权限处理
- Android 13+需要运行时请求POST_NOTIFICATIONS权限
- 确保在应用启动时调用requestPermission方法

### 2. 前台服务限制
- Android 15对前台服务有新的限制
- dataSync类型的前台服务有24小时内6小时的运行限制

### 3. 通知渠道
- 确保为Android O+设备创建了适当的通知渠道
- 通知渠道的重要性级别影响通知显示

## 故障排除

### 前台消息仍然接收不到
1. 检查通知权限是否已授予
2. 确认应用是否正确注册了前台回调
3. 查看日志中的前台状态检测结果

### 通知不显示
1. 检查通知渠道是否正确创建
2. 确认通知权限状态
3. 验证通知内容是否完整

### 构建错误
1. 确保Cordova Android版本>=13.0.0
2. 检查Android SDK和构建工具版本
3. 清理项目并重新构建
