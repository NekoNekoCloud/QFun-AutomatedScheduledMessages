package utils;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MessageSender {
    
    private static BiConsumer<String, String> action;
    private static Consumer<String> logger;

    // 接收从 main.java 传过来的 发送方法 和 日志方法
    public static void init(BiConsumer<String, String> senderAction, Consumer<String> logAction) {
        action = senderAction;
        logger = logAction;
    }

    public static void sendGroupMessage(String groupUin, String message) {
        if (action != null) {
            try {
                action.accept(groupUin, message);
            } catch (Exception e) {
                log("[Sender 异常] 发送消息失败 (" + groupUin + "): " + e.getMessage());
            }
        }
    }

    // 开放给其他类使用的统一日志入口
    public static void log(String msg) {
        if (logger != null) {
            try {
                logger.accept(msg);
            } catch (Exception e) {}
        } else {
            System.out.println(msg);
        }
    }
}
