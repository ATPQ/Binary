package com.ssr.binary;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class StoragePermissionHelper {

    /**
     * 检测并请求 “管理所有文件访问权限” (MANAGE_EXTERNAL_STORAGE)
     * 适用于 Android 11 (API 30) 及以上版本
     *
     * @param activity 调用此方法的Activity实例
     * @return true 表示已经拥有权限或无需请求；false 表示已发起权限请求
     */
    public static boolean checkAndRequestManageAllFilesAccessPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                // 反射调用 Environment.isExternalStorageManager()
                Class<?> envClass = Class.forName("android.os.Environment");
                Method isManagerMethod = envClass.getDeclaredMethod("isExternalStorageManager");
                Boolean isManager = (Boolean) isManagerMethod.invoke(null);
                if (isManager != null && isManager) {
                    // 已经有权限
                    return true;
                } else {
                    // 反射获取 Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION 常量
                    Class<?> settingsClass = Class.forName("android.provider.Settings");
                    Field actionField = settingsClass.getDeclaredField("ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                    String permissionAction = (String) actionField.get(null);

                    Intent intent = new Intent(permissionAction);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);

                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 反射失败，尝试直接使用硬编码字符串（兼容性备选）
                try {
                    Intent intent = new Intent("android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION");
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(intent);
                    return false;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    // 无法请求权限，直接返回true，需自行处理
                    return true;
                }
            }
        } else {
            // 低于API 30的版本无需请求此权限
            return true;
        }
    }
}
