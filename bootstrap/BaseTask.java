package bootstrap;

import java.io.*;
import java.nio.file.*;
import org.json.*;
import utils.MessageSender;

public abstract class BaseTask {
    protected String pluginPath;
    protected File configFile;
    protected JSONObject memoryConfig;
    protected String taskTag;          // 日志标签，如 "[DailyTask]"
    protected String configFileName;   // 配置文件名，如 "daily_config.json"
    protected int threadPoolSize;      // 线程池大小

    public BaseTask(String pluginPath, String configFileName, String taskTag, int threadPoolSize) {
        this.pluginPath = pluginPath;
        this.configFileName = configFileName;
        this.taskTag = taskTag;
        this.threadPoolSize = threadPoolSize;
        this.configFile = new File(pluginPath + "/config", configFileName);
    }

    /**
     * 由子类提供默认配置 JSON 对象（当配置文件不存在时使用）
     */
    protected abstract JSONObject createDefaultConfig();

    /**
     * 由子类实现具体的启动逻辑（因为 IntervalTask 和 DailyTask 的调度方式不同）
     */
    public abstract void start();

    /**
     * 通用的配置加载逻辑
     */
    protected void loadConfig() {
        try {
            File configDir = new File(pluginPath + "/config");
            if (!configDir.exists()) configDir.mkdirs();

            if (!configFile.exists()) {
                memoryConfig = createDefaultConfig();
                saveConfigToDisk();
                MessageSender.log(taskTag + " 生成了默认配置文件");
                return;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            memoryConfig = new JSONObject(content);
            MessageSender.log(taskTag + " 配置文件读取成功，包含 " + memoryConfig.length() + " 个群配置");

        } catch (Exception e) {
            MessageSender.log(taskTag + " 致命错误：读取或解析 JSON 失败: " + e.toString());
        }
    }

    /**
     * 通用的磁盘保存方法
     */
    protected synchronized void saveConfigToDisk() {
        try (FileWriter fw = new FileWriter(configFile)) {
            fw.write(memoryConfig.toString(4));
        } catch (Exception e) {
            MessageSender.log(taskTag + " 错误：保存文件到磁盘失败: " + e.toString());
        }
    }

    // 获取配置对象，方便子类访问
    public JSONObject getMemoryConfig() {
        return memoryConfig;
    }

    // 通用的日志方法（可选，子类也可直接使用 MessageSender）
    protected void log(String msg) {
        MessageSender.log(taskTag + " " + msg);
    }
}