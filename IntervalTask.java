import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class IntervalTask {
    private ScheduledExecutorService scheduler;
    private String pluginPath;
    private File configFile;
    private JSONObject memoryConfig;

    public IntervalTask(String pluginPath) {
        this.pluginPath = pluginPath;
        this.configFile = new File(pluginPath + "/config", "interval_config.json");
        // 使用多线程池防止群太多卡死
        this.scheduler = Executors.newScheduledThreadPool(3); 
    }

    private void loadConfig() {
        try {
            File configDir = new File(pluginPath + "/config");
            if (!configDir.exists()) configDir.mkdirs();

            if (!configFile.exists()) {
                memoryConfig = new JSONObject();
                JSONObject groupCfg = new JSONObject();
                groupCfg.put("interval", "00-01-00");
                groupCfg.put("message", "默认间隔发送模板");
                groupCfg.put("last_sent_time", 0L);
                memoryConfig.put("123456789", groupCfg);
                saveConfigToDisk();
                MessageSender.log("[IntervalTask] 生成了默认配置文件");
                return;
            }

            BufferedReader br = new BufferedReader(new FileReader(configFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            
            memoryConfig = new JSONObject(sb.toString());
            MessageSender.log("[IntervalTask] 配置文件读取成功，包含 " + memoryConfig.length() + " 个群配置");

        } catch (Exception e) {
            MessageSender.log("[IntervalTask 致命错误] 读取JSON失败: " + e.toString());
        }
    }

    private synchronized void updateLastSentTimeAndSave(String groupUin, long currentTime) {
        try {
            if (memoryConfig != null && memoryConfig.has(groupUin)) {
                memoryConfig.getJSONObject(groupUin).put("last_sent_time", currentTime);
                saveConfigToDisk();
            }
        } catch (Exception e) {
            MessageSender.log("[IntervalTask 错误] 写入状态失败: " + e.toString());
        }
    }

    private synchronized void saveConfigToDisk() {
        try {
            FileWriter fw = new FileWriter(configFile);
            fw.write(memoryConfig.toString(4));
            fw.close();
        } catch (Exception e) {
            MessageSender.log("[IntervalTask 错误] 保存文件失败: " + e.toString());
        }
    }

    private long parseIntervalToMillis(String intervalStr) {
        String[] parts = intervalStr.split("-");
        if (parts.length == 3) {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            long totalSeconds = (hours * 3600L) + (minutes * 60L) + seconds;
            return Math.max(totalSeconds, 30L) * 1000L; // 防刷屏底线30秒
        }
        return 30000L; 
    }

    public void start() {
        loadConfig();
        if (memoryConfig == null) return;

        Iterator<String> keys = memoryConfig.keys();
        long now = System.currentTimeMillis();

        while (keys.hasNext()) {
            String groupUin = keys.next();
            try {
                JSONObject groupData = memoryConfig.getJSONObject(groupUin);
                String intervalStr = groupData.getString("interval");
                String message = groupData.getString("message");
                long lastSentTime = groupData.optLong("last_sent_time", 0L);

                long intervalMillis = parseIntervalToMillis(intervalStr);
                long nextExpectedSendTime = lastSentTime + intervalMillis;

                long delayMillis = 0;
                if (now < nextExpectedSendTime) {
                    delayMillis = nextExpectedSendTime - now;
                }

                MessageSender.log(String.format("[IntervalTask] 群 %s 计划在 %d 秒后触发间隔发送", groupUin, delayMillis / 1000));
                triggerSendCycle(groupUin, message, intervalMillis, delayMillis);

            } catch (Exception e) {
                MessageSender.log("[IntervalTask 逻辑异常] 群 " + groupUin + " 处理失败: " + e.toString());
            }
        }
    }

    private void triggerSendCycle(final String groupUin, final String message, long intervalMillis, long initialDelay) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. 原子化防护：先写磁盘更新时间戳
                updateLastSentTimeAndSave(groupUin, System.currentTimeMillis());

                // 2. 真正发送消息 (对接已写好的 MessageSender)
                MessageSender.sendGroupMessage(groupUin, message);
                
                // 3. 打印日志
                MessageSender.log("[IntervalTask] 已成功向群 " + groupUin + " 发送间隔消息");

            } catch (Exception e) {
                MessageSender.log("[IntervalTask 线程内部错误] 发送异常: " + e.toString());
            }

        }, initialDelay, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
