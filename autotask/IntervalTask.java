package autotask;

import java.util.*;
import java.util.concurrent.*;

import bootstrap.BaseTask;
import org.json.*;
import utils.MessageSender;

/**
 * 间隔循环定时任务管理类。
 * 负责从配置文件读取各群的间隔发送设置，按固定时间间隔向指定群发送消息，
 * 并持久化记录上一次发送时间，以支持离线补发和精确间隔控制。
 * 最低间隔强制锁定为30秒，防止刷屏。
 */
public class IntervalTask extends BaseTask {
    /** 定时任务调度器，基于线程池实现周期任务 */
    private ScheduledExecutorService scheduler;

    /**
     * 构造函数，初始化调度器并设置配置文件路径。
     * @param pluginPath 插件根目录路径，配置文件夹将创建在其下的config子目录中
     */
    public IntervalTask(String pluginPath) {
        // 配置文件: interval_config.json, 标签: [IntervalTask], 线程池大小: 3
        super(pluginPath, "interval_config.json", "[IntervalTask]", 3);
        this.scheduler = Executors.newScheduledThreadPool(threadPoolSize);
    }

    protected JSONObject createDefaultConfig() {
        JSONObject cfg = new JSONObject();
        JSONObject groupCfg = new JSONObject();
        groupCfg.put("interval", "00-01-00");
        groupCfg.put("message", "默认间隔发送模板");
        groupCfg.put("last_sent_time", 0L);
        groupCfg.put("status", 1);   // 1-启用，0-禁用
        cfg.put("123456789", groupCfg);
        return cfg;
    }

    /**
     * 更新指定群的最后发送时间，并立即将内存配置写入磁盘文件。
     * 采用synchronized保证多线程环境下的数据一致性。
     * @param groupUin 目标群号
     * @param currentTime 当前系统时间戳（毫秒），作为新的last_sent_time值
     */
    private synchronized void updateLastSentTimeAndSave(String groupUin, long currentTime) {
        try {
            if (memoryConfig != null && memoryConfig.has(groupUin)) {
                // 更新内存中的时间戳
                memoryConfig.getJSONObject(groupUin).put("last_sent_time", currentTime);
                // 立即持久化到磁盘，防止数据丢失
                saveConfigToDisk();
            }
        } catch (Exception e) {
            MessageSender.log("[IntervalTask 错误] 更新并保存状态失败: " + e);
        }
    }

    /**
     * 解析间隔时间字符串为毫秒值。
     * 格式必须为 "HH-mm-ss"（时-分-秒），若解析失败或总时长低于30秒，则返回30秒对应的毫秒数。
     * 内置最低30秒强制锁，防止过于频繁的发送。
     * @param intervalStr 间隔字符串，如 "01-30-00" 表示1小时30分钟
     * @return 间隔毫秒数，最低30000毫秒
     */
    private long parseIntervalToMillis(String intervalStr) {
        String[] parts = intervalStr.split("-");
        if (parts.length == 3) {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            long totalSeconds = (hours * 3600L) + (minutes * 60L) + seconds;
            // 强制最低30秒间隔
            return Math.max(totalSeconds, 30L) * 1000L;
        }
        // 格式不正确时默认返回30秒
        return 30000L; 
    }

    /**
     * 启动所有群组的间隔定时任务。
     * 先加载配置，然后遍历每一个群配置，根据上次发送时间和间隔计算初次延迟，
     * 最后调度周期发送任务。若计算出的初次延迟为0，则立即执行第一次发送。
     */
    public void start() {
        loadConfig();
        if (memoryConfig == null) return; // 配置加载失败则直接退出

        Iterator<String> keys = memoryConfig.keys();
        long now = System.currentTimeMillis();

        while (keys.hasNext()) {
            String groupUin = keys.next();
            try {
                JSONObject groupData = memoryConfig.getJSONObject(groupUin);

                int status = groupData.optInt("status", 1);
                if (status == 0) {
                    log("群 " + groupUin + " 发送状态为禁用，跳过间隔调度");
                    continue;
                }

                String intervalStr = groupData.getString("interval");
                String message = groupData.getString("message");
                long lastSentTime = groupData.optLong("last_sent_time", 0L);

                // 将间隔字符串转换为毫秒
                long intervalMillis = parseIntervalToMillis(intervalStr);
                // 计算理论上下一次应发送的时间点
                long nextExpectedSendTime = lastSentTime + intervalMillis;

                // 初始延迟时间：若理论发送时间已过，则为0（立即发送）；否则为等待剩余时间
                long delayMillis = 0;
                if (now < nextExpectedSendTime) {
                    delayMillis = nextExpectedSendTime - now;
                }

                MessageSender.log(String.format("[IntervalTask] 群 %s 计划在 %d 秒后触发间隔发送", groupUin, delayMillis / 1000));

                // 开始周期任务
                triggerSendCycle(groupUin, message, intervalMillis, delayMillis);

            } catch (Exception e) {
                MessageSender.log("[IntervalTask 逻辑异常] 群 " + groupUin + " 处理失败: " + e.toString());
            }
        }
    }

    /**
     * 为指定群创建并启动一个固定频率的定时发送任务。
     * 每次执行时会更新last_sent_time并调用消息发送接口。
     * @param groupUin 目标群号
     * @param message 要发送的消息内容
     * @param intervalMillis 发送周期（毫秒）
     * @param initialDelay 首次执行的延迟时间（毫秒），用于对齐发送时间点
     */
    private void triggerSendCycle(final String groupUin, final String message, long intervalMillis, long initialDelay) {

        // 使用scheduleAtFixedRate保证严格的周期执行
        scheduler.scheduleAtFixedRate(() -> {
            // 发送前检查状态
            if (memoryConfig.has(groupUin)) {
                int status = memoryConfig.getJSONObject(groupUin).optInt("status", 1);
                if (status == 0) {
                    MessageSender.log("[IntervalTask] 群 " + groupUin + " 状态为禁用，跳过本次发送");
                    return; // 不发送，但周期任务继续
                }
            }

            try {
                // 更新发送时间并持久化，确保重启后能准确计算补发
                updateLastSentTimeAndSave(groupUin, System.currentTimeMillis());

                // 发送群消息
                MessageSender.sendGroupMessage(groupUin, message);
                
                MessageSender.log("[IntervalTask] 已成功向群 " + groupUin + " 发送间隔消息");

            } catch (Exception e) {
                // 捕获发送过程中可能出现的异常，避免一个群的失败影响其他群的任务
                MessageSender.log("[IntervalTask 线程内部错误] 发送异常: " + e.toString());
            }

        }, initialDelay, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止所有间隔任务并关闭调度器。
     * 注意：此时不会再执行额外的状态保存，因为每次发送时已实时持久化。
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            // 立即中断所有正在执行的任务，并清空等待队列
            scheduler.shutdownNow();
            // 回滚优化: 移除在停止时保存的逻辑
        }
    }
}
