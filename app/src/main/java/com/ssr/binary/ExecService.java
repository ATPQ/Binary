package com.ssr.binary;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.TimeZone;

public class ExecService extends Service {

    public static final String ACTION_START = "com.ssr.binary.action.START";
    public static final String ACTION_STOP = "com.ssr.binary.action.STOP";

    public static final String EXTRA_COMMAND = "command";

    public static final String BROADCAST_LOG = "com.ssr.binary.broadcast.LOG";
    public static final String BROADCAST_FINISH = "com.ssr.binary.broadcast.FINISH";

    private Process runningProcess = null;
    private Thread execThread = null;

    private static final int NOTIFY_ID = 1;
    private static final String CHANNEL_ID = "exec_service_channel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "执行服务通知",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("前台执行命令服务");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle("二进制执行中")
                .setContentText(contentText)
                .setSmallIcon(R.mipmap.ic_launcher) // 你项目的图标
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return builder.build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            if (command != null && execThread == null) {
                startExecCommand(command);
            }
        } else if (ACTION_STOP.equals(action)) {
            stopExecCommand();
        }

        return START_NOT_STICKY;
    }

    private void startExecCommand(String command) {
        startForeground(NOTIFY_ID, buildNotification("正在执行命令"));

        execThread = new Thread(() -> {
            try {


                ProcessBuilder processBuilder = new ProcessBuilder();

                processBuilder.command(command.split(" ")); // 将命令及其参数分割成数组

                processBuilder.environment().put("TZ", TimeZone.getDefault().getID()); // 设置环境变量

                runningProcess = processBuilder.start(); // 启动进程

                //runningProcess = Runtime.getRuntime().exec(command);
                sendLog("开始执行: " + command);

                BufferedReader stdout = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream()));

                Thread stdoutThread = new Thread(() -> {
                    String line;
                    try {
                        while ((line = stdout.readLine()) != null) {
                            sendLog("[OUT] " + line);
                        }
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("interrupted")) {
                            // 忽略正常中断异常
                        } else {
                            sendLog("读取标准输出异常: " + e.getMessage());
                        }
                    }
                });

                Thread stderrThread = new Thread(() -> {
                    String line;
                    try {
                        while ((line = stderr.readLine()) != null) {
                            sendLog("[ERR] " + line);
                        }
                    } catch (IOException e) {
                        if (e.getMessage() != null && e.getMessage().contains("interrupted")) {
                            // 忽略正常中断异常
                        } else {
                            sendLog("读取错误输出异常: " + e.getMessage());
                        }
                    }
                });

                stdoutThread.start();
                stderrThread.start();

                stdoutThread.join();
                stderrThread.join();

                int exitCode = runningProcess.waitFor();
                sendLog("进程结束，退出码: " + exitCode);

            } catch (Exception e) {
                sendLog("执行异常: " + e.getMessage());
            } finally {
                runningProcess = null;
                execThread = null;
                stopForeground(true);
                sendFinishBroadcast();
                stopSelf();
            }
        });
        execThread.start();
    }

    private void stopExecCommand() {
        if (runningProcess != null) {
            runningProcess.destroy();
            //sendLog("收到停止命令，正在终止进程");
        }
    }

    private void sendLog(String message) {
        Intent intent = new Intent(BROADCAST_LOG);
        intent.putExtra("log", message);
        sendBroadcast(intent);
    }

    private void sendFinishBroadcast() {
        Intent intent = new Intent(BROADCAST_FINISH);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // 无需绑定
    }

}
