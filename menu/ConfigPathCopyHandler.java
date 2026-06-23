package menu;

public class ConfigPathCopyHandler {

    // 处理“复制配置路径”菜单点击
    public void handleCopyConfigPath(int chatType, String peerUin, String peerName) {
        try {
            String configDirPath = pluginPath + "/config";

            android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                    context.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("configPath", configDirPath);
            clipboard.setPrimaryClip(clip);

            qqToast(2, "路径已复制：\n" + configDirPath);
            log("用户点击菜单，已复制路径: " + configDirPath);

        } catch (Exception e) {
            qqToast(1, "复制路径失败");
            log("复制剪贴板异常: " + e.getMessage());
        }
    }


}