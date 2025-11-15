package com.ssr.binary;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.os.Build;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.database.Cursor;
import android.content.ContentResolver;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends Activity {

    private AutoCompleteTextView paramInput;
    private Spinner binarySpinner;
    private Button btnExecute;
    private Button btnStop;
    private TextView logOutput;
    private ScrollView logScrollView;
    private static final int REQUEST_CODE_IMPORT_FILE = 1001;
    private String selectedFilePath = null; // 当前选中文件的完整路径
    private String file_m = null;
    private static boolean lookroot = false;
    private static final String PREFS_NAME = "app_prefs";
    private static final String PREF_SELECTED_FILE = "selected_file_path";
    private static final String PREFS_PARAM_HISTORY = "param_history";
    private static final String PREFS_PARAM_LIST_KEY = "param_list";
    private ArrayAdapter<String> paramHistoryAdapter;
    private final List<String> paramHistoryList = new ArrayList<>();
    private Process runningProcess = null;
    private final List<String> logLines = new ArrayList<>();
    private static final int MAX_LOG_LINES = 100;

    private BroadcastReceiver execLogReceiver;
    private BroadcastReceiver execFinishReceiver;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取文件权限
        boolean hasPermission = StoragePermissionHelper.checkAndRequestManageAllFilesAccessPermission(this);
        if (!hasPermission) {
            appendLog("未获取存储权限");
        }


        //file_m = "/data/data/"+getPackageName()+"/files/";
        file_m = getFilesDir().getAbsolutePath();
        

        // 绑定控件
        paramInput = findViewById(R.id.param_input);
        loadParamHistory();
        paramInput.setAdapter(paramHistoryAdapter);
        paramInput.setThreshold(1); // 输入1个字符后显示建议
        binarySpinner = findViewById(R.id.binary_spinner);
        Button btnImport = findViewById(R.id.btn_import);
        Button btnDelete = findViewById(R.id.btn_delete);
        btnExecute = findViewById(R.id.btn_execute);
        btnStop = findViewById(R.id.btn_stop);
        logOutput = findViewById(R.id.log_output);
        logScrollView = findViewById(R.id.log_scrollview);

        btnExecute.setEnabled(true);   // 执行按钮默认可用
        btnStop.setEnabled(false);     // 停止按钮默认禁用（灰色不可点）



        String lastParam = null;
        if (!paramHistoryList.isEmpty()) {
            lastParam = paramHistoryList.get(0);
        }
        if (lastParam != null) {
            paramInput.setText(lastParam);
        }


        // 注册日志广播接收器
        execLogReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String log = intent.getStringExtra("log");
                if (log != null) {
                    appendLog(log);
                }
            }
        };

        execFinishReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // 进程结束，恢复按钮状态
                btnExecute.setEnabled(true);
                btnStop.setEnabled(false);
                //appendLog("执行完成");
            }
        };

        registerReceiver(execLogReceiver, new android.content.IntentFilter(ExecService.BROADCAST_LOG));
        registerReceiver(execFinishReceiver, new android.content.IntentFilter(ExecService.BROADCAST_FINISH));



        // 按钮事件监听示例
        btnImport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector();
            }
        });


        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedFilePath == null) {
                    Toast.makeText(MainActivity.this, "没有选中文件可删除", Toast.LENGTH_SHORT).show();
                    return;
                }
                File fileToDelete = new File(selectedFilePath);
                if (!fileToDelete.exists()) {
                    Toast.makeText(MainActivity.this, "文件不存在或已被删除", Toast.LENGTH_SHORT).show();
                    loadSpinnerData(); // 刷新列表
                    return;
                }

                // 弹出确认对话框
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定要删除文件吗？\n" + fileToDelete.getName())
                        .setPositiveButton("删除", (dialog, which) -> {
                            boolean deleted = fileToDelete.delete();
                            if (deleted) {
                                appendLog("删除文件成功：" + fileToDelete.getAbsolutePath());
                                Toast.makeText(MainActivity.this, "删除成功", Toast.LENGTH_SHORT).show();

                                // 删除后清除选中缓存
                                selectedFilePath = null;
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                                        .remove(PREF_SELECTED_FILE)
                                        .apply();

                                loadSpinnerData(); // 刷新Spinner列表
                            } else {
                                appendLog("删除文件失败：" + fileToDelete.getAbsolutePath());
                                Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });


//        btnExecute.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (selectedFilePath == null) {
//                    Toast.makeText(MainActivity.this, "请选择要执行的二进制文件", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//                String params = paramInput.getText().toString().trim();
//
//                //appendLog("执行二进制按钮点击，参数：" + params);
//                saveParamToHistory(params);
//
//                btnExecute.setEnabled(false);
//                btnStop.setEnabled(true);
//                String cmd = selectedFilePath;
//                if (params != null) {
//                    cmd = cmd +  " " + params;
//                }
//                appendLog("运行命令: " + cmd);
//                if (lookroot) {
//                    cmd = "su -c " + cmd;
//                }
//                execAsync(cmd);
//
//            }
//        });
//
//        btnStop.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (runningProcess != null) {
//                    runningProcess.destroy();
//                    appendLog("正在停止进程");
//
//                    btnStop.setEnabled(false);
//                    btnExecute.setEnabled(true);
//                    // 不要这里置 null，等执行线程结束后置 null
//                    // runningProcess = null;
//                } else {
//                    Toast.makeText(MainActivity.this, "无运行中的程序", Toast.LENGTH_SHORT).show();
//                }
//            }
//        });


        btnExecute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedFilePath == null) {
                    Toast.makeText(MainActivity.this, "请选择要执行的二进制文件", Toast.LENGTH_SHORT).show();
                    return;
                }
                String params = paramInput.getText().toString().trim();
                saveParamToHistory(params);

                btnExecute.setEnabled(false);
                btnStop.setEnabled(true);

                String cmd = selectedFilePath;
                if (params != null && !params.isEmpty()) {
                    cmd = cmd + " " + params;
                }
                if (lookroot) {
                    cmd = "su -c " + cmd;
                }

                //appendLog("启动前台服务执行命令: " + cmd);
                Intent serviceIntent = new Intent(MainActivity.this, ExecService.class);
                serviceIntent.setAction(ExecService.ACTION_START);
                serviceIntent.putExtra(ExecService.EXTRA_COMMAND, cmd);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }
        });


        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent stopIntent = new Intent(MainActivity.this, ExecService.class);
                stopIntent.setAction(ExecService.ACTION_STOP);
                startService(stopIntent);
                //appendLog("已发送停止命令给服务");
            }
        });



        loadSpinnerData();

        String[] abis;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            abis = Build.SUPPORTED_ABIS; // 返回设备支持的CPU ABI数组，优先级排序
        } else {
            abis = new String[]{Build.CPU_ABI, Build.CPU_ABI2}; // 兼容低版本
        }

        StringBuilder abiBuilder = new StringBuilder();
        for (String abi : abis) {
            abiBuilder.append(abi).append(" ");
        }
        appendLog("设备支持的CPU ABI：" + abiBuilder.toString().trim());



        if (RootUtils.checkAndRequestRoot()) {
            lookroot = true;
            // Toast.makeText(this, "设备已Root，获取Root权限成功", Toast.LENGTH_SHORT).show();
        } else {
            // Toast.makeText(this, "设备未Root或获取Root权限失败", Toast.LENGTH_SHORT).show();
        }


    }


    private void loadParamHistory() {
        SharedPreferences sp = getSharedPreferences(PREFS_PARAM_HISTORY, MODE_PRIVATE);
        String saved = sp.getString(PREFS_PARAM_LIST_KEY, "");
        paramHistoryList.clear();
        if (!saved.isEmpty()) {
            paramHistoryList.addAll(Arrays.asList(saved.split("\n")));
        }
        paramHistoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, paramHistoryList);
    }


    private void saveParamToHistory(String param) {
        if (param == null ) return;

        param = param.trim();
        // 移到首位
        paramHistoryList.remove(param);
        paramHistoryList.add(0, param); // 新参数加到最前面

        // 保存到sp
        StringBuilder sb = new StringBuilder();
        for (String s : paramHistoryList) {
            sb.append(s).append('\n');
        }
        getSharedPreferences(PREFS_PARAM_HISTORY, MODE_PRIVATE).edit()
                .putString(PREFS_PARAM_LIST_KEY, sb.toString())
                .apply();

        // 更新adapter
        paramHistoryAdapter.clear();
        paramHistoryAdapter.addAll(paramHistoryList);
        paramHistoryAdapter.notifyDataSetChanged();
    }



    private void loadSpinnerData() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isCopied = prefs.getBoolean("PREF_IS_COPIED", false);

        if (!isCopied) {
            appendLog("第一次运行，开始复制二进制文件到软件私有目录，assets/binary 如果需要内置可用mt管理器等其他工具将二进制文件放置此目录重新签名");
            try {
                String[] assetFiles = getAssets().list("binary");
                if (assetFiles != null) {
                    for (String assetFileName : assetFiles) {
                        try (InputStream in = getAssets().open("binary/" + assetFileName);
                             FileOutputStream out = openFileOutput(assetFileName, Context.MODE_PRIVATE)) {

                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = in.read(buffer)) != -1) {
                                out.write(buffer, 0, len);
                            }
                            out.flush();

                            File copiedFile = new File(file_m, assetFileName);
                            Process chmodProcess = Runtime.getRuntime().exec("chmod 777 " + copiedFile.getAbsolutePath());
                            int chmodResult = chmodProcess.waitFor();

                            if (chmodResult == 0) {
                                appendLog("复制并赋权成功: " + assetFileName);
                            } else {
                                appendLog("复制成功，但赋权失败: " + assetFileName + "，错误码：" + chmodResult);
                            }

                        } catch (Exception e) {
                            appendLog("复制文件失败：" + assetFileName + "，错误：" + e.getMessage());
                        }
                    }
                }
                // 复制成功后设置标志
                prefs.edit().putBoolean("PREF_IS_COPIED", true).apply();
            } catch (IOException e) {
                appendLog("获取assets/binary文件列表失败：" + e.getMessage());
            }
        }


        // 继续原有加载文件列表逻辑
        File dir = new File(file_m);
        File[] files = dir.listFiles();

        String[] fileNames;
        final String noFileTip = "无文件";

        if (files == null || files.length == 0) {
            // 无文件时Spinner只显示“无文件”且不可选
            fileNames = new String[]{noFileTip};
            selectedFilePath = null;
            setSpinnerEnabled(false);
        } else {
            fileNames = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                fileNames[i] = files[i].getName();
            }
            setSpinnerEnabled(true);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, fileNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binarySpinner.setAdapter(adapter);

        // 恢复上次选中项
        String lastSelected = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(PREF_SELECTED_FILE, null);

        if (lastSelected != null && selectedFilePath == null && files != null) {
            // 查找lastSelected在当前文件列表中的位置
            int pos = -1;
            for (int i = 0; i < files.length; i++) {
                if (files[i].getAbsolutePath().equals(lastSelected)) {
                    pos = i;
                    break;
                }
            }
            if (pos != -1) {
                binarySpinner.setSelection(pos);
                selectedFilePath = lastSelected;
            } else {
                // 文件不存在则不选中任何项
                binarySpinner.setSelection(0);
                selectedFilePath = files.length > 0 ? files[0].getAbsolutePath() : null;
            }
        } else if (files != null && files.length > 0 && selectedFilePath == null) {
            // 默认选第一个
            binarySpinner.setSelection(0);
            selectedFilePath = files[0].getAbsolutePath();
        }

        // 监听选择变化
        binarySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedName = (String) parent.getItemAtPosition(position);
                if (noFileTip.equals(selectedName)) {
                    selectedFilePath = null;
                    setSpinnerEnabled(false);
                    return;
                }
                File selectedFile = new File(file_m, selectedName);
                selectedFilePath = selectedFile.getAbsolutePath();

                // 保存到SharedPreferences
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putString(PREF_SELECTED_FILE, selectedFilePath)
                        .apply();

                //appendLog("选择文件：" + selectedFilePath);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 没选中任何项时清空路径
                selectedFilePath = null;
            }
        });
    }



    // 追加日志显示并自动滚动到底部
    private void appendLog(String message) {
        logOutput.post(() -> {
            // 追加新日志行
            logLines.add(message);

            // 超过最大行数时，删除最早的行
            if (logLines.size() > MAX_LOG_LINES) {
                // 移除多余的行数，保证只剩MAX_LOG_LINES行
                int removeCount = logLines.size() - MAX_LOG_LINES;
                logLines.subList(0, removeCount).clear();
            }

            // 拼接所有行显示
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append("\n");
            }

            // 更新TextView文本
            logOutput.setText(sb.toString());

            // 滚动到底部
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }




    private void openFileSelector() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_FILE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                //appendLog("选择文件: " + uri);
                importFileToPrivateDir(uri);
            } else {
                appendLog("未选择文件");
            }
        }
    }


    private void importFileToPrivateDir(Uri uri) {
        if (uri == null) return;

        ContentResolver resolver = getContentResolver();
        String fileName = queryFileName(uri);
        if (fileName == null) fileName = "imported_file";

        File destFile = new File(file_m, fileName);

        //appendLog(fileName+" 开始导入到 " + destFile.getAbsolutePath());
        boolean importSuccess = false;
        boolean chmodSuccess = false;

        try (InputStream in = resolver.openInputStream(uri);
             OutputStream out = this.openFileOutput(fileName, Context.MODE_PRIVATE)) {

            if (in == null) {
                appendLog("无法读取文件输入流");
            } else {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
                importSuccess = true;
                appendLog("文件导入成功，路径：" + destFile.getAbsolutePath());
            }

        } catch (Exception e) {
            appendLog("导入失败: " + e.getMessage());
        }

        if (importSuccess) {
            try {
                Process process = Runtime.getRuntime().exec("chmod 777 " + destFile.getAbsolutePath());

                int result = process.waitFor();
                String archInfo = "";
                try {
                    File file = new File(destFile.getAbsolutePath());
                    archInfo = ElfChecker.checkElfArchitecture(file);

                } catch (IOException e) {
                    appendLog("架构检测异常：" + e.getMessage());
                }


                if (result == 0) {
                    chmodSuccess = true;
                    // 调试：检查实际权限
                    appendLog("赋予777权限成功" +
                            "\n文件是否存在: " + destFile.exists() +
                            " 文件大小: " + destFile.length() + " bytes" +
                            "\n可执行:" + destFile.canExecute() +
                            " 可读:" + destFile.canRead() +
                            " 可写:" + destFile.canWrite() +
                            "\n二进制文件架构检测结果：" + archInfo);
                } else {
                    appendLog("赋予777权限失败，错误码：" + result);
                }
            } catch (Exception e) {
                appendLog("赋予777权限异常：" + e.getMessage());
            }

        }

        // 最终提示
        if (importSuccess && chmodSuccess) {
            Toast.makeText(this, "导入成功，权限设置成功", Toast.LENGTH_SHORT).show();
        } else if (importSuccess) {
            Toast.makeText(this, "导入成功，但权限设置失败", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
        }
        loadSpinnerData();
    }

//    private void execAsync(String command) {
//        new Thread(() -> {
//            Process process;
//            try {
//                process = Runtime.getRuntime().exec(command);
//                runningProcess = process;  // 设置全局变量
//
//                appendLog("开始执行");
//
//                Process finalProcess = process; // 用于内部线程引用
//
//                Thread outputThread = new Thread(() -> {
//                    try (BufferedReader reader = new BufferedReader(
//                            new InputStreamReader(finalProcess.getInputStream()))) {
//                        String line;
//                        while ((line = reader.readLine()) != null) {
//                            appendLog("[OUT] " + line);
//                        }
//                    } catch (IOException e) {
//                        if (!e.getMessage().contains("interrupted")) {
//                            appendLog("读取标准输出异常: " + e.getMessage());
//                        }
//                        // 忽略read interrupted异常
//                    }
//                });
//
//                Thread errorThread = new Thread(() -> {
//                    try (BufferedReader errorReader = new BufferedReader(
//                            new InputStreamReader(finalProcess.getErrorStream()))) {
//                        String line;
//                        while ((line = errorReader.readLine()) != null) {
//                            appendLog("[ERR] " + line);
//                        }
//                    } catch (IOException e) {
//                        if (!e.getMessage().contains("interrupted")) {
//                            appendLog("读取错误输出异常: " + e.getMessage());
//                        }
//                        // 忽略read interrupted异常
//                    }
//                });
//
//                outputThread.start();
//                errorThread.start();
//
//                outputThread.join();
//                errorThread.join();
//
//                int exitCode = finalProcess.waitFor();
//                appendLog("进程结束，退出码: " + exitCode);
//
//            } catch (Exception e) {
//                appendLog("执行异常: " + e.getMessage());
//            } finally {
//                runOnUiThread(() -> {
//                    btnExecute.setEnabled(true);
//                    btnStop.setEnabled(false);
//                });
//                runningProcess = null;
//            }
//        }).start();
//    }



    private String queryFileName(Uri uri) {
        String result = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx != -1) {
                    result = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {}
        finally {
            if (cursor != null) cursor.close();
        }
        return result;
    }




    private void setSpinnerEnabled(boolean enabled) {
        binarySpinner.setEnabled(enabled);
        if (!enabled) {
            binarySpinner.setClickable(false);
        } else {
            binarySpinner.setClickable(true);
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (execLogReceiver != null) {
            unregisterReceiver(execLogReceiver);
            execLogReceiver = null;
        }
        if (execFinishReceiver != null) {
            unregisterReceiver(execFinishReceiver);
            execFinishReceiver = null;
        }
    }


}
