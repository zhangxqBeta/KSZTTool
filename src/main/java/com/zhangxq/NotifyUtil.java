package com.zhangxq;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

public class NotifyUtil {
    public static void notify(String content, Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("KSZTTool")
                .createNotification(content, NotificationType.INFORMATION)
                .notify(project);
    }

    public static void notifyError(String content, Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("KSZTToolError")
                .createNotification(content, NotificationType.ERROR)
                .notify(project);
    }
}
