
package by.chemerisuk.cordova.firebase;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.app.Activity;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import by.chemerisuk.cordova.support.CordovaMethod;
import by.chemerisuk.cordova.support.ReflectiveCordovaPlugin;
import me.leolin.shortcutbadger.ShortcutBadger;

import static androidx.core.content.ContextCompat.getSystemService;
import static com.google.android.gms.tasks.Tasks.await;
import static by.chemerisuk.cordova.support.ExecutionThread.WORKER;

public class FirebaseMessagingPlugin extends ReflectiveCordovaPlugin {
    private static final String TAG = "FCMPlugin";

    private JSONObject lastBundle;
    private boolean isBackground = false;
    private boolean forceShow = false;
    private CallbackContext tokenRefreshCallback;
    private CallbackContext foregroundCallback;
    private CallbackContext backgroundCallback;
    private static FirebaseMessagingPlugin instance;
    private NotificationManager notificationManager;
    private FirebaseMessaging firebaseMessaging;
    private CallbackContext requestPermissionCallback;

    @Override
    protected void pluginInitialize() {
        FirebaseMessagingPlugin.instance = this;

        firebaseMessaging = FirebaseMessaging.getInstance();
        notificationManager = getSystemService(cordova.getActivity(), NotificationManager.class);
        lastBundle = getNotificationData(cordova.getActivity().getIntent());
    }

    @CordovaMethod(WORKER)
    private void subscribe(CordovaArgs args, final CallbackContext callbackContext) throws Exception {
        String topic = args.getString(0);
        await(firebaseMessaging.subscribeToTopic(topic));
        callbackContext.success();
    }

    @CordovaMethod(WORKER)
    private void unsubscribe(CordovaArgs args, CallbackContext callbackContext) throws Exception {
        String topic = args.getString(0);
        await(firebaseMessaging.unsubscribeFromTopic(topic));
        callbackContext.success();
    }

    @CordovaMethod
    private void clearNotifications(CallbackContext callbackContext) {
        notificationManager.cancelAll();
        callbackContext.success();
    }

    @CordovaMethod(WORKER)
    private void deleteToken(CallbackContext callbackContext) throws Exception {
        await(firebaseMessaging.deleteToken());
        callbackContext.success();
    }

    @CordovaMethod(WORKER)
    private void getToken(CordovaArgs args, CallbackContext callbackContext) throws Exception {
        String type = args.getString(0);
        if (!type.isEmpty()) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, (String) null));
        } else {
            String fcmToken = await(firebaseMessaging.getToken());
            callbackContext.success(fcmToken);
        }
    }

    @CordovaMethod
    private void onTokenRefresh(CallbackContext callbackContext) {
        instance.tokenRefreshCallback = callbackContext;
    }

    @CordovaMethod
    private void onMessage(CallbackContext callbackContext) {
        instance.foregroundCallback = callbackContext;
    }

    @CordovaMethod
    private void onBackgroundMessage(CallbackContext callbackContext) {
        instance.backgroundCallback = callbackContext;

        if (lastBundle != null) {
            sendNotification(lastBundle, callbackContext);
            lastBundle = null;
        }
    }

    @CordovaMethod
    private void setBadge(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        int value = args.getInt(0);
        if (value >= 0) {
            Context context = cordova.getActivity().getApplicationContext();
            ShortcutBadger.applyCount(context, value);
            callbackContext.success();
        } else {
            callbackContext.error("Badge value can't be negative");
        }
    }

    @CordovaMethod
    private void getBadge(CallbackContext callbackContext) {
        Context context = cordova.getActivity();
        SharedPreferences settings = context.getSharedPreferences("badge", Context.MODE_PRIVATE);
        callbackContext.success(settings.getInt("badge", 0));
    }

    @CordovaMethod
    private void requestPermission(CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        JSONObject options = args.getJSONObject(0);
        Context context = cordova.getActivity().getApplicationContext();
        forceShow = options.optBoolean("forceShow");
        
        // 检查通知权限
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            callbackContext.success();
            return;
        }
        
        // Android 13+ 需要运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionCallback = callbackContext;
            
            // 检查是否已有权限
            if (PermissionHelper.hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                callbackContext.success();
                return;
            }
            
            // 请求权限
            PermissionHelper.requestPermission(this, 0, Manifest.permission.POST_NOTIFICATIONS);
        } else {
            // Android 12及以下版本，引导用户到设置页面
            callbackContext.error("Notifications permission is not granted. Please enable in system settings.");
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                requestPermissionCallback.error("Notifications permission is not granted");
                return;
            }
        }
        requestPermissionCallback.success();
    }

    @Override
    public void onNewIntent(Intent intent) {
        JSONObject notificationData = getNotificationData(intent);
        if (instance != null && notificationData != null) {
            sendNotification(notificationData, instance.backgroundCallback);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        this.isBackground = true;
    }

    @Override
    public void onResume(boolean multitasking) {
        this.isBackground = false;
    }

    static void sendNotification(RemoteMessage remoteMessage) {
        JSONObject notificationData = new JSONObject(remoteMessage.getData());
        RemoteMessage.Notification notification = remoteMessage.getNotification();
        try {
            if (notification != null) {
                notificationData.put("gcm", toJSON(notification));
            }
            notificationData.put("google.message_id", remoteMessage.getMessageId());
            notificationData.put("google.sent_time", remoteMessage.getSentTime());

            if (instance != null) {
                // 改进前台/后台检测逻辑
                boolean isAppInForeground = !instance.isBackground;
                CallbackContext callbackContext = isAppInForeground ? instance.foregroundCallback
                        : instance.backgroundCallback;
                
                // 确保前台消息能够被正确处理
                if (isAppInForeground && instance.foregroundCallback != null) {
                    Log.d(TAG, "Sending foreground notification");
                    instance.sendNotification(notificationData, instance.foregroundCallback);
                } else if (!isAppInForeground && instance.backgroundCallback != null) {
                    Log.d(TAG, "Sending background notification");
                    instance.sendNotification(notificationData, instance.backgroundCallback);
                } else {
                    Log.w(TAG, "No callback available for notification. Foreground: " + isAppInForeground);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "sendNotification", e);
        }
    }

    static void sendToken(String instanceId) {
        if (instance != null) {
            if (instance.tokenRefreshCallback != null && instanceId != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, instanceId);
                pluginResult.setKeepCallback(true);
                instance.tokenRefreshCallback.sendPluginResult(pluginResult);
            }
        }
    }

    static boolean isForceShow() {
        return instance != null && instance.forceShow;
    }

    private void sendNotification(JSONObject notificationData, CallbackContext callbackContext) {
        if (callbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notificationData);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        }
    }

    private JSONObject getNotificationData(Intent intent) {
        Bundle bundle = intent.getExtras();

        if (bundle == null) {
            return null;
        }

        if (!bundle.containsKey("google.message_id") && !bundle.containsKey("google.sent_time")) {
            return null;
        }

        try {
            JSONObject notificationData = new JSONObject();
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                notificationData.put(key, bundle.get(key));
            }
            return notificationData;
        } catch (JSONException e) {
            Log.e(TAG, "getNotificationData", e);
            return null;
        }
    }

    private static JSONObject toJSON(RemoteMessage.Notification notification) throws JSONException {
        JSONObject result = new JSONObject()
                .put("body", notification.getBody())
                .put("title", notification.getTitle())
                .put("sound", notification.getSound())
                .put("icon", notification.getIcon())
                .put("tag", notification.getTag())
                .put("color", notification.getColor())
                .put("clickAction", notification.getClickAction());

        Uri imageUri = notification.getImageUrl();
        if (imageUri != null) {
            result.put("imageUrl", imageUri.toString());
        }

        return result;
    }
}
