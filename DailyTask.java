import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import org.json.*;

public class DailyTask {
    private ScheduledExecutorService scheduler;
    private String pluginPath;
    private File configFile;
    private JSONObject memoryConfig;
    
    private SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH-mm-ss");

    public DailyTask(String pluginPath) {
        this.pluginPath = pluginPath;
        this.configFile = new File(pluginPath + "/config", "daily_config.json");
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    private void loadConfig() {
        try {
            File configDir = new File(pluginPath + "/config");
            if (!configDir.exists()) configDir.mkdirs();

            if (!configFile.exists()) {
                memoryConfig = new JSONObject();
                JSONObject groupCfg = new JSONObject();
                groupCfg.put("time", "12-00-00");
                groupCfg.put("message", "默认每日发送模板");
                groupCfg.put("is_sent_today", false);
                groupCfg.put("last_update_date", "1970-01-01");
                memoryConfig.put("123456789", groupCfg);
                saveConfigToDisk();
                MessageSender.log("[DailyTask] 生成了默认配置文件");
                return;
            }

            BufferedReader br = new BufferedReader(new FileReader(configFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            
            memoryConfig = new JSONObject(sb.toString());
            MessageSender.log("[DailyTask] 配置文件读取成功，包含 " + memoryConfig.length() + " 个群配置");

        } catch (Exception e) {
            MessageSender.log("[DailyTask 致命错误] 读取或解析 JSON 失败: " + e.toString());
        }
    }

    private synchronized void updateStatusAndSave(String groupUin, boolean isSentToday, String updateDate) {
        try {
            if (memoryConfig != null && memoryConfig.has(groupUin)) {
                JSONObject groupData = memoryConfig.getJSONObject(groupUin);
                groupData.put("is_sent_today", isSentToday);
                groupData.put("last_update_date", updateDate);
                saveConfigToDisk();
            }
        } catch (Exception e) {
            MessageSender.log("[DailyTask 错误] 写入状态失败: " + e.toString());
        }
    }

    private synchronized void saveConfigToDisk() {
        try {
            FileWriter fw = new FileWriter(configFile);
            fw.write(memoryConfig.toString(4));
            fw.close();
        } catch (Exception e) {
            MessageSender.log("[DailyTask 错误] 保存文件失败: " + e.toString());
        }
    }

    public void start() {
        loadConfig();
        if (memoryConfig == null) return;

        Iterator<String> keys = memoryConfig.keys();
        while (keys.hasNext()) {
            String groupUin = keys.next();
            scheduleNextExecution(groupUin);
        }
    }

    private void scheduleNextExecution(final String groupUin) {
        try {
            if (!memoryConfig.has(groupUin)) return;
            JSONObject groupData = memoryConfig.getJSONObject(groupUin);
            
            String targetTimeStr = groupData.getString("time");
            String message = groupData.getString("message");
            boolean isSentToday = groupData.getBoolean("is_sent_today");
            String lastUpdateDate = groupData.getString("last_update_date");

            Date now = new Date();
            String todayStr = dateFmt.format(now);

            if (!todayStr.equals(lastUpdateDate)) {
                isSentToday = false;
                lastUpdateDate = todayStr;
                updateStatusAndSave(groupUin, false, todayStr);
            }

            Calendar targetCal = Calendar.getInstance();
            targetCal.setTime(timeFmt.parse(targetTimeStr));
            
            Calendar currentCal = Calendar.getInstance();
            targetCal.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));
            targetCal.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            targetCal.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));

            long targetTimeMillis = targetCal.getTimeInMillis();
            long currentTimeMillis = currentCal.getTimeInMillis();
            long delayMillis;

            if (currentTimeMillis >= targetTimeMillis) {
                if (!isSentToday) {
                    delayMillis = 0; // 今天没发过，立即补发
                } else {
                    targetCal.add(Calendar.DAY_OF_MONTH, 1);
                    delayMillis = targetCal.getTimeInMillis() - currentTimeMillis;
                }
            } else {
                if (!isSentToday) {
                    delayMillis = targetTimeMillis - currentTimeMillis;
                } else {
                    targetCal.add(Calendar.DAY_OF_MONTH, 1);
                    delayMillis = targetCal.getTimeInMillis() - currentTimeMillis;
                }
            }

            MessageSender.log(String.format("[DailyTask] 群 %s 计划在 %d 秒后触发发送 (状态: %s)", 
                              groupUin, delayMillis / 1000, isSentToday ? "明天" : "今天"));

            scheduler.schedule(() -> {
                try {
                    String executeDateStr = dateFmt.format(new Date());
                    updateStatusAndSave(groupUin, true, executeDateStr);
                    
                    MessageSender.sendGroupMessage(groupUin, message);
                    MessageSender.log("[DailyTask] 已成功向群 " + groupUin + " 发送每日消息");
                    
                    // 递归计划下一次
                    scheduleNextExecution(groupUin);
                } catch (Exception e) {
                    MessageSender.log("[DailyTask 线程内部错误] " + e.toString());
                }
            }, delayMillis, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            MessageSender.log("[DailyTask 逻辑异常] 群 " + groupUin + " 处理失败: " + e.toString());
        }
    }

    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
