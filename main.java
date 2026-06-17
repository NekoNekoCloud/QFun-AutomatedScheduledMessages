import java.io.File;

// ---------------- 1. 动态加载依赖类 ----------------
// 注意：按被依赖的顺序加载
loadJava(pluginPath + "/MessageSender.java");
loadJava(pluginPath + "/IntervalTask.java");
loadJava(pluginPath + "/DailyTask.java");

// 声明全局变量，以便在 unLoadPlugin 中进行停止操作
IntervalTask intervalTask = null;
DailyTask dailyTask = null;

// ---------------- 2. 核心初始化函数 ----------------
void initPlugin() {
    try {
        log("正在初始化定时发送组件...");

        // 悬浮窗菜单按钮
        addItem("复制配置路径", "onCopyConfigPathClick");
        // 【修改这里】：同时注入 sendMsg 和全局的 log 方法
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
// ---------------- 3. 脚本卸载/停止回调 ----------------
// 必须严格实现此方法，防止线程池泄漏导致多次执行
void unLoadPlugin() {
    if (intervalTask != null) {
        intervalTask.stop();
    }
    if (dailyTask != null) {
        dailyTask.stop();
    }
    
    log("所有定时器线程池已销毁，脚本安全退出");
    qqToast(0, "定时发送插件已停止");
}

// ---------------- 5. 悬浮窗菜单回调 ----------------
void onCopyConfigPathClick(int chatType, String peerUin, String peerName) {
    try {
        // 拼接配置文件夹的绝对路径
        String configDirPath = pluginPath + "/config";
        
        // 调用 Android 原生剪贴板服务
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("configPath", configDirPath);
        clipboard.setPrimaryClip(clip);
        
        // 弹出成功提示
        qqToast(2, "路径已复制：\n" + configDirPath);
        log("用户点击菜单，已复制路径: " + configDirPath);
        
    } catch (Exception e) {
        qqToast(1, "复制路径失败");
        log("复制剪贴板异常: " + e.getMessage());
    }
}


// ---------------- 4. 脚本执行入口 ----------------
log("=========================");
log("脚本开始运行...");
initPlugin();
