package menu;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ClearLogHandler {

    /**
     * 清空日志文件 (pluginPath/log.txt)
     */
    public void handleClearLog(int chatType, String peerUin, String peerName) {
        File logFile = new File(pluginPath, "log.txt");
        FileWriter fw = null;
        try {
            if (!logFile.exists()) {
                // 日志文件不存在则创建
                logFile.createNewFile();
                qqToast(2, "日志文件不存在，已新建空日志");
                log("日志文件不存在，已创建新日志文件");
                return;
            }
            // 清空文件内容（覆盖为空）
            fw = new FileWriter(logFile);
            fw.write("");  // 写入空字符串
            fw.flush();
            qqToast(2, "日志已清空");
            log("用户手动清空了日志文件");
        } catch (IOException e) {
            qqToast(1, "清空日志失败: " + e.getMessage());
            log("清空日志异常: " + e.getMessage());
        } finally {
            if (fw != null) {
                try { fw.close(); } catch (IOException ignored) {}
            }
        }
    }
}