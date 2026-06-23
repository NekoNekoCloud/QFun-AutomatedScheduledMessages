import bootstrap.Initializer;
import menu.ClearLogHandler;
import menu.ConfigPathCopyHandler;
import menu.GroupTaskConfigHandler;
import menu.GroupTaskStatusToggleHandler;

// ---------------- 1. 动态加载依赖类 ----------------
// 注意：按被依赖的顺序加载
loadJar(pluginPath + "/libs/json-20260522.jar"); // 加载JSON库

loadJava(pluginPath + "/bootstrap/BaseTask.java");    // 先加载基类
loadJava(pluginPath + "/utils/MessageSender.java");

loadJava(pluginPath + "/autotask/IntervalTask.java");
loadJava(pluginPath + "/autotask/DailyTask.java");
loadJava(pluginPath + "/bootstrap/Initializer.java"); // 加载新的初始化类

loadJava(pluginPath + "/menu/ConfigPathCopyHandler.java");// 加载路径复制菜单类
loadJava(pluginPath + "/menu/GroupTaskStatusToggleHandler.java");// 加载状态修改菜单类
loadJava(pluginPath + "/menu/GroupTaskConfigHandler.java"); // 加载任务管理菜单类
loadJava(pluginPath + "/menu/ClearLogHandler.java"); // 加载清空日志菜单类


// 声明全局变量（不初始化）
Initializer initializer = null;
ConfigPathCopyHandler handleCopyConfigPath = null;
GroupTaskStatusToggleHandler handleToggleStatus = null;
GroupTaskConfigHandler groupTaskConfigHandler = null;
ClearLogHandler clearLogHandler = null;

// ---------------- 2. 核心初始化函数 ----------------
void initPlugin() {
    initializer = new Initializer(pluginPath);
    initializer.start();

    // 在 initializer 创建后再初始化菜单处理器
    handleCopyConfigPath = new ConfigPathCopyHandler();
    handleToggleStatus = new GroupTaskStatusToggleHandler(initializer);
    groupTaskConfigHandler = new GroupTaskConfigHandler(pluginPath, initializer.getDailyTask(), initializer.getIntervalTask());
    clearLogHandler = new ClearLogHandler();
}

// ---------------- 3. 脚本卸载/停止回调 ----------------
void unLoadPlugin() {
    if (initializer != null) {
        initializer.stop();
    }
}

// ---------------- 4. 悬浮窗菜单顶层回调：负责转发事件 ----------------
void onCopyConfigPathClick(int chatType, String peerUin, String peerName) {
    handleCopyConfigPath.handleCopyConfigPath(chatType, peerUin, peerName);
}
void onToggleGroupStatusClick(int chatType, String peerUin, String peerName) {
    handleToggleStatus.handleToggleStatus(chatType, peerUin, peerName);
}
void onManageGroupTask(int chatType, String peerUin, String peerName) {
    if (groupTaskConfigHandler != null) {
        groupTaskConfigHandler.showManagementDialog(chatType, peerUin, peerName);
    }
}

// ---------------- 5. 脚本执行入口 ----------------
log("=========================");
log("脚本开始运行...");
initPlugin();
