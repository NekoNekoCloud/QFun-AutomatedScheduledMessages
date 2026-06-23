package menu;

import autotask.DailyTask;
import autotask.IntervalTask;
import bootstrap.Initializer;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

public class GroupTaskStatusToggleHandler {
    private Initializer initializer;
    public GroupTaskStatusToggleHandler(Initializer initializer) {
        this.initializer = initializer;
    }

    // 菜单回调：切换当前群的定时状态
    public void handleToggleStatus(int chatType, String peerUin, String peerName) {
        if (chatType != 2) {
            qqToast(1, "请在群聊界面使用此功能");
            return;
        }
        try {
            boolean enabled = toggleGroupStatusInConfig(peerUin);
            String tip = enabled ? "已启用该群的定时发送" : "已禁用该群的定时发送";
            qqToast(2, tip);
            log("用户切换群 " + peerUin + " 状态为: " + (enabled ? "启用" : "禁用"));
        } catch (Exception e) {
            qqToast(1, "切换失败：" + e.getMessage());
            log("切换群状态异常: " + e);
        }
    }

    // 翻转状态并同步内存
    private boolean toggleGroupStatusInConfig(String groupUin) throws Exception {
        boolean newEnabled = false;

        // 1. 处理每日配置文件
        File dailyFile = new File(pluginPath + "/config/daily_config.json");
        if (dailyFile.exists()) {
            JSONObject daily = loadJsonFile(dailyFile);
            if (daily.has(groupUin)) {
                int cur = daily.getJSONObject(groupUin).optInt("status", 1);
                int next = (cur == 0) ? 1 : 0;
                daily.getJSONObject(groupUin).put("status", next);
                saveJsonFile(dailyFile, daily);
                newEnabled = (next == 1);
            }
        }

        // 2. 处理间隔配置文件
        File intervalFile = new File(pluginPath + "/config/interval_config.json");
        if (intervalFile.exists()) {
            JSONObject interval = loadJsonFile(intervalFile);
            if (interval.has(groupUin)) {
                int cur = interval.getJSONObject(groupUin).optInt("status", 1);
                int next = (cur == 0) ? 1 : 0;
                interval.getJSONObject(groupUin).put("status", next);
                saveJsonFile(intervalFile, interval);
                newEnabled = (next == 1);
            }
        }

        // 3. 同步到内存（让定时任务立即感知）
        DailyTask dailyTask = initializer.getDailyTask();
        IntervalTask intervalTask = initializer.getIntervalTask();

        if (dailyTask != null && dailyTask.getMemoryConfig() != null
                && dailyTask.getMemoryConfig().has(groupUin)) {
            dailyTask.getMemoryConfig().getJSONObject(groupUin).put("status", newEnabled ? 1 : 0);
        }
        if (intervalTask != null && intervalTask.getMemoryConfig() != null
                && intervalTask.getMemoryConfig().has(groupUin)) {
            intervalTask.getMemoryConfig().getJSONObject(groupUin).put("status", newEnabled ? 1 : 0);
        }

        return newEnabled;
    }

    // 工具：读取JSON文件
    private JSONObject loadJsonFile(File file) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()));
        return new JSONObject(content);
    }

    // 工具：保存JSON文件
    private void saveJsonFile(File file, JSONObject json) throws Exception {
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write(json.toString(4));
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
