package com.ssr.binary;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class RootUtils {

    /**
     * 判断设备是否已Root，并尝试获取Root权限。
     * @return true 表示设备已Root且成功获取Root权限，false表示未Root或获取失败
     */
    public static boolean checkAndRequestRoot() {
        // 1. 先简单检测su文件是否存在，快速判断设备是否可能Root
        String[] suPaths = {
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/data/local/su",
                "/su/bin/su"
        };
        boolean suExists = false;
        for (String path : suPaths) {
            if (new File(path).exists()) {
                suExists = true;
                break;
            }
        }
        if (!suExists) {
            // 设备没有su文件，基本判定没Root
            return false;
        }

        // 2. 尝试执行一个简单的su命令，检测是否能获取Root权限
        Process process = null;
        try {
            // 调用su -c id命令，尝试获取Root环境
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});

            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                output.append(line).append("\n");
            }

            StringBuilder errOutput = new StringBuilder();
            while ((line = err.readLine()) != null) {
                errOutput.append(line).append("\n");
            }

            int exitCode = process.waitFor();

            // 判断输出是否包含root uid信息（uid=0表示root）
            if (exitCode == 0 && output.toString().contains("uid=0")) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return false;
    }
}
