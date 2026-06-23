package autotask;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;

import bootstrap.BaseTask;
import org.json.*;
import utils.MessageSender;

/**
 * 每日定时任务管理类。
 * 负责根据配置文件为每个群安排一次精确到秒的每日定时消息发送，
 * 支持跨天自动重置状态，并在首次启动或离线恢复时计算合适延迟进行补发或准点发送。
 */
public class DailyTask extends BaseTask {
    /** 定时任务调度器，用于安排具体的每日发送任务 */
    private ScheduledExecutorService scheduler;
    /** 日期格式化器，用于生成和比较日期字符串（yyyy-MM-dd） */
    private SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd");
    /** 时间格式化器，用于解析配置文件中的目标发送时间（HH-mm-ss） */
    private SimpleDateFormat timeFmt = new SimpleDateFormat("HH-mm-ss");

    /**
     * 构造函数，初始化调度器并设置配置文件路径。
     * @param pluginPath 插件根目录路径
     */
    public DailyTask(String pluginPath) {
        // 配置文件: daily_config.json, 标签: [DailyTask], 线程池大小: 1
        super(pluginPath, "daily_config.json", "[DailyTask]", 1);
        this.scheduler = Executors.newScheduledThreadPool(threadPoolSize);
    }

    protected JSONObject createDefaultConfig() {
        JSONObject cfg = new JSONObject();
        JSONObject groupCfg = new JSONObject();
        groupCfg.put("time", "12-00-00");
        groupCfg.put("message", "默认每日发送模板");
        groupCfg.put("is_sent_today", false);
        groupCfg.put("last_update_date", "1970-01-01");
        groupCfg.put("status", 1);   // 1-启用，0-禁用
        cfg.put("123456789", groupCfg);
        return cfg;
    }

    /**
     * 更新指定群的状态并立即保存到磁盘。
     * 使用synchronized保证多线程环境下的数据一致性。
     * @param groupUin 目标群号
     * @param isSentToday 是否已完成今日发送
     * @param updateDate 当前日期字符串（yyyy-MM-dd）
     */
    private synchronized void updateStatusAndSave(String groupUin, boolean isSentToday, String updateDate) {
        try {
            if (memoryConfig != null && memoryConfig.has(groupUin)) {
                JSONObject groupData = memoryConfig.getJSONObject(groupUin);
                groupData.put("is_sent_today", isSentToday);
                groupData.put("last_update_date", updateDate);
                // 立即持久化，防止程序异常退出导致状态丢失
                saveConfigToDisk();
            }
        } catch (Exception e) {
            MessageSender.log("[DailyTask 错误] 写入状态失败: " + e.toString());
        }
    }

    /**
     * 启动所有群的每日定时任务。
     * 加载配置后遍历每个群，调用scheduleNextExecution进行任务调度。
     */
    public void start() {
        loadConfig();
        if (memoryConfig == null) return;

        Iterator<String> keys = memoryConfig.keys();
        while (keys.hasNext()) {
            String groupUin = keys.next();
            try {
                JSONObject groupData = memoryConfig.getJSONObject(groupUin);
                int status = groupData.optInt("status", 1);  // 默认1，兼容旧配置
                if (status == 0) {
                    MessageSender.log("[DailyTask] 群 " + groupUin + " 发送状态为禁用，跳过调度");
                    continue;
                }
                scheduleNextExecution(groupUin);
            } catch (Exception e) {
                MessageSender.log("[DailyTask] 群 " + groupUin + " 配置异常: " + e.toString());
            }
        }
    }


    /**
     * 为指定群规划下一次每日消息发送。
     * 逻辑说明：
     * 1. 检查日期是否变更，若是则将 is_sent_today 重置为 false，表示今天可发送。
     * 2. 解析目标发送时间，并与当前时间比较。
     * 3. 如果当前时间已过目标时间：
     *    - 若今天尚未发送，立即发送（delay=0，实现补发）
     *    - 若今天已发送，则将任务推迟到明天的同一时间。
     * 4. 如果当前时间未到目标时间，则计算剩余等待时间作为延迟。
     * 5. 执行发送后更新状态，并递归调用自身以安排下一天的发送。
     * @param groupUin 目标群号
     */
    private void scheduleNextExecution(final String groupUin) {
        // 检查是否已禁用
        if (memoryConfig.has(groupUin)) {
            int status = memoryConfig.getJSONObject(groupUin).optInt("status", 1);
            if (status == 0) {
                MessageSender.log("[DailyTask] 群 " + groupUin + " 已被禁用，取消下次调度");
                return; // 不再递归调用，调度终止
            }
        }

        try {
            if (!memoryConfig.has(groupUin)) return;
            JSONObject groupData = memoryConfig.getJSONObject(groupUin);
            
            String targetTimeStr = groupData.getString("time");
            String message = groupData.getString("message");
            boolean isSentToday = groupData.getBoolean("is_sent_today");
            String lastUpdateDate = groupData.getString("last_update_date");

            Date now = new Date();
            String todayStr = dateFmt.format(now);

            // 跨天处理：如果记录的日期与今天不一致，重置发送状态
            if (!todayStr.equals(lastUpdateDate)) {
                isSentToday = false;
                lastUpdateDate = todayStr;
                // 回滚优化: 调用回保存到磁盘的方法
                updateStatusAndSave(groupUin, false, todayStr);
            }

            // 构造今天的目标时间日历对象
            Calendar targetCal = Calendar.getInstance();
            targetCal.setTime(timeFmt.parse(targetTimeStr));

            Calendar currentCal = Calendar.getInstance();
            // 将目标时间的年月日设置为今天
            targetCal.set(Calendar.YEAR, currentCal.get(Calendar.YEAR));
            targetCal.set(Calendar.MONTH, currentCal.get(Calendar.MONTH));
            targetCal.set(Calendar.DAY_OF_MONTH, currentCal.get(Calendar.DAY_OF_MONTH));

            long targetTimeMillis = targetCal.getTimeInMillis();
            long currentTimeMillis = currentCal.getTimeInMillis();
            long delayMillis;

            // 比较当前时间与目标时间
            if (currentTimeMillis >= targetTimeMillis) {
                // 当前时间已过或刚好等于目标时间
                if (!isSentToday) {
                    // 今天还没发送过，立即执行（延迟为0）
                    delayMillis = 0; 
                } else {
                    // 今天已经发送过，将发送推迟到明天同一时刻
                    targetCal.add(Calendar.DAY_OF_MONTH, 1);
                    delayMillis = targetCal.getTimeInMillis() - currentTimeMillis;
                }
            } else {
                // 目标时间还未到，计算剩余等待时间
                delayMillis = targetTimeMillis - currentTimeMillis;
            }

            // 记录日志说明计划延迟
            MessageSender.log(String.format("[DailyTask] 群 %s 计划在 %d 秒后触发发送 (状态: %s)", 
                              groupUin, delayMillis / 1000, isSentToday ? "已完成，计划明天" : "待发送"));

            // 安排一次性定时任务
            scheduler.schedule(() -> {
                try {
                    String executeDateStr = dateFmt.format(new Date());
                    // 标记今日已发送，并持久化
                    updateStatusAndSave(groupUin, true, executeDateStr);

                    // 执行消息发送
                    MessageSender.sendGroupMessage(groupUin, message);
                    MessageSender.log("[DailyTask] 已成功向群 " + groupUin + " 发送每日消息");

                    // 发送完成后，规划下一次发送（即明天的发送）
                    scheduleNextExecution(groupUin);
                } catch (Exception e) {
                    MessageSender.log("[DailyTask 线程内部错误] " + e.toString());
                }
            }, delayMillis, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            MessageSender.log("[DailyTask 逻辑异常] 群 " + groupUin + " 处理失败: " + e.toString());
        }
    }

    /**
     * 停止每日任务调度。
     * 直接关闭调度器并中断所有等待中的任务。
     * 注意：由于每次状态更新都会即时持久化，因此无需在停止时额外保存。
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }
}
