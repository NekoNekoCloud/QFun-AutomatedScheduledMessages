package bootstrap;

import autotask.DailyTask;
import autotask.IntervalTask;
import utils.MessageSender;

public class Initializer {
    private IntervalTask intervalTask;
    private DailyTask dailyTask;
    private String pluginPath;

    public DailyTask getDailyTask() { return dailyTask; }
    public IntervalTask getIntervalTask() { return intervalTask; }

    public Initializer(String pluginPath) {
        this.pluginPath = pluginPath;
    }

    public void start() {
        try {
            log("正在初始化定时发送组件 (from Initializer)...");

            // 悬浮窗菜单按钮
            addItem("复制配置路径", "onCopyConfigPathClick");
            addItem("切换当前群的定时状态", "onToggleGroupStatusClick");
            addItem("管理当前群定时任务", "onManageGroupTask");
            addItem("清空日志", "onClearLogClick");


            // 注入 sendMsg 和全局的 log 方法
            MessageSender.init(
                (groupUin, message) -> { sendMsg(groupUin, message, 2); },
                (msg) -> { log(msg); } // 桥接全局日志
            );

            intervalTask = new IntervalTask(pluginPath);
            intervalTask.start();

            dailyTask = new DailyTask(pluginPath);
            dailyTask.start();

            qqToast(2, "自动化定时框架启动成功！");

        } catch (Exception e) {
            log("启动失败: " + e.getMessage());
        }
    }

    public void stop() {
        if (intervalTask != null) {
            intervalTask.stop();
        }
        if (dailyTask != null) {
            dailyTask.stop();
        }
        log("所有定时器线程池已销毁，脚本安全退出 (from Initializer)");
        qqToast(0, "定时发送插件已停止");
    }
}
