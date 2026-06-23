package menu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import java.io.*;
import java.nio.file.*;

import autotask.DailyTask;
import autotask.IntervalTask;
import org.json.*;

public class GroupTaskConfigHandler {
    private String pluginPath;
    private DailyTask dailyTask;
    private IntervalTask intervalTask;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public GroupTaskConfigHandler(String pluginPath, DailyTask dailyTask, IntervalTask intervalTask) {
        this.pluginPath = pluginPath;
        this.dailyTask = dailyTask;
        this.intervalTask = intervalTask;
    }

    public void showManagementDialog(int chatType, String peerUin, String peerName) {
        if (chatType != 2) {
            qqToast(1, "请在群聊界面打开此功能");
            return;
        }
        final String groupUin = peerUin;
        mainHandler.post(new Runnable() {
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getNowActivity());
                builder.setTitle("管理群 " + groupUin + " 定时任务");
                String[] items = {"查看当前配置", "切换启用/禁用", "修改每日时间", "修改每日消息", "修改间隔时间", "修改间隔消息"};
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: showCurrentConfig(groupUin); break;
                            case 1: toggleStatus(groupUin); break;
                            case 2: showEditDialog(groupUin, "daily", "time", "请输入每日发送时间 (HH-mm-ss)"); break;
                            case 3: showEditDialog(groupUin, "daily", "message", "请输入每日发送内容"); break;
                            case 4: showEditDialog(groupUin, "interval", "interval", "请输入间隔时间 (HH-mm-ss)"); break;
                            case 5: showEditDialog(groupUin, "interval", "message", "请输入间隔发送内容"); break;
                        }
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        });
    }

    private void showCurrentConfig(String groupUin) {
        JSONObject daily = dailyTask.getMemoryConfig();
        JSONObject interval = intervalTask.getMemoryConfig();
        StringBuilder sb = new StringBuilder();
        sb.append("【每日任务】\n");
        if (daily != null && daily.has(groupUin)) {
            JSONObject d = daily.getJSONObject(groupUin);
            sb.append("时间: ").append(d.optString("time")).append("\n");
            sb.append("消息: ").append(d.optString("message")).append("\n");
            sb.append("状态: ").append(d.optInt("status", 1) == 1 ? "启用" : "禁用").append("\n");
            sb.append("今日已发: ").append(d.optBoolean("is_sent_today") ? "是" : "否");
        } else {
            sb.append("未配置");
        }
        sb.append("\n\n【间隔任务】\n");
        if (interval != null && interval.has(groupUin)) {
            JSONObject i = interval.getJSONObject(groupUin);
            sb.append("间隔: ").append(i.optString("interval")).append("\n");
            sb.append("消息: ").append(i.optString("message")).append("\n");
            sb.append("状态: ").append(i.optInt("status", 1) == 1 ? "启用" : "禁用");
        } else {
            sb.append("未配置");
        }
        final String msg = sb.toString();
        mainHandler.post(new Runnable() {
            public void run() {
                new AlertDialog.Builder(getNowActivity())
                        .setTitle("当前配置")
                        .setMessage(msg)
                        .setPositiveButton("关闭", null)
                        .show();
            }
        });
    }

    private void toggleStatus(String groupUin) {
        boolean newEnabled = flipStatusInConfig(dailyTask.getMemoryConfig(), groupUin, "daily_config.json");
        flipStatusInConfig(intervalTask.getMemoryConfig(), groupUin, "interval_config.json");
        final String tip = newEnabled ? "已启用该群的定时发送" : "已禁用该群的定时发送";
        mainHandler.post(new Runnable() {
            public void run() { qqToast(2, tip); }
        });
    }

    private boolean flipStatusInConfig(JSONObject config, String groupUin, String fileName) {
        if (config == null || !config.has(groupUin)) return false;
        JSONObject groupCfg = config.getJSONObject(groupUin);
        int cur = groupCfg.optInt("status", 1);
        int next = (cur == 0) ? 1 : 0;
        groupCfg.put("status", next);
        saveConfigToFile(fileName, config);
        return (next == 1);
    }

    private void showEditDialog(final String groupUin, final String type, final String field, String hint) {
        mainHandler.post(new Runnable() {
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getNowActivity());
                builder.setTitle("修改" + (type.equals("daily") ? "每日" : "间隔") + "配置");
                final EditText input = new EditText(getNowActivity());
                input.setHint(hint);
                JSONObject config = type.equals("daily") ? dailyTask.getMemoryConfig() : intervalTask.getMemoryConfig();
                if (config != null && config.has(groupUin)) {
                    String current = config.getJSONObject(groupUin).optString(field);
                    if (current != null && !current.isEmpty()) {
                        input.setText(current);
                    }
                }
                builder.setView(input);
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String newValue = input.getText().toString().trim();
                        if (newValue.isEmpty()) {
                            mainHandler.post(new Runnable() {
                                public void run() { qqToast(1, "内容不能为空"); }
                            });
                            return;
                        }
                        updateFieldAndSave(type, groupUin, field, newValue);
                        mainHandler.post(new Runnable() {
                            public void run() { qqToast(2, "修改成功"); }
                        });
                    }
                });
                builder.setNegativeButton("取消", null);
                builder.show();
            }
        });
    }

    private void updateFieldAndSave(String type, String groupUin, String field, String value) {
        JSONObject config = type.equals("daily") ? dailyTask.getMemoryConfig() : intervalTask.getMemoryConfig();
        String fileName = type.equals("daily") ? "daily_config.json" : "interval_config.json";
        if (config == null || !config.has(groupUin)) {
            if (config == null) config = new JSONObject();
            JSONObject newCfg = new JSONObject();
            if (type.equals("daily")) {
                newCfg.put("time", "12-00-00");
                newCfg.put("message", "默认每日消息");
                newCfg.put("is_sent_today", false);
                newCfg.put("last_update_date", "1970-01-01");
            } else {
                newCfg.put("interval", "00-01-00");
                newCfg.put("message", "默认间隔消息");
                newCfg.put("last_sent_time", 0L);
            }
            newCfg.put("status", 1);
            config.put(groupUin, newCfg);
        }
        config.getJSONObject(groupUin).put(field, value);
        saveConfigToFile(fileName, config);
        if (type.equals("daily")) {
            dailyTask.start();
        } else {
            intervalTask.start();
        }
    }

    private void saveConfigToFile(String fileName, JSONObject data) {
        FileWriter fw = null;
        try {
            File file = new File(pluginPath + "/config", fileName);
            fw = new FileWriter(file);
            fw.write(data.toString(4));
        } catch (Exception e) {
            log("保存配置文件失败: " + e);
        } finally {
            if (fw != null) {
                try { fw.close(); } catch (IOException ignored) {}
            }
        }
    }
}