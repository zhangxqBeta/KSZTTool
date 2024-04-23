package com.zhangxq.logconvert;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.zhangxq.NotifyUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;

public class LogConvertAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(new FileNameExtensionFilter("File(*.log)", "log"));

        // 打开文件选择框（线程将被阻塞, 直到选择框被关闭）
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                JsonArray jsonArray = new JsonArray();
                String line;
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
                while((line = reader.readLine()) != null) {
                    String time = "";
                    String content = "";
                    try {
                        int start = line.indexOf('[');
                        int end = line.indexOf(']');
                        if (start == -1 || end == -1) continue;
                        time = line.substring(start + 1, end);

                        start = line.indexOf('[', end + 1);
                        end = line.indexOf(']', end + 1);
                        if (start == -1 || end == -1) continue;
                        content = line.substring(start + 1, end);
                    } catch (Exception e) {
                        continue;
                    }

                    String tag = "-";
                    String message = content;
                    if (content.startsWith("tag: ")) {
                        int start = content.indexOf("tag: ");
                        int end = content.indexOf(", content: ");
                        if (start != -1 && end != -1 && start + 5 < end) {
                            tag = content.substring(start + 5, end);
                        }

                        start = content.indexOf(", content: ");
                        if (start != -1 && start + 11 < content.length()) {
                            message = content.substring(start + 11);
                        }
                    } else {
                        int indexOfSpilt = content.indexOf(" -> ");
                        if (indexOfSpilt != -1) {
                            tag = content.substring(0, indexOfSpilt);
                            if (indexOfSpilt + 4 < content.length()) {
                                message = content.substring(indexOfSpilt + 4);
                            }
                        }
                    }
                    JsonObject itemHeaderTime = new JsonObject();
                    long seconds = format.parse(time).getTime() / 1000;
                    itemHeaderTime.addProperty("seconds", seconds);
                    itemHeaderTime.addProperty("nanos", 0);
                    JsonObject itemHeader = new JsonObject();
                    itemHeader.addProperty("logLevel", "INFO");
                    itemHeader.addProperty("applicationId", "-");
                    itemHeader.addProperty("tag", tag);
                    itemHeader.add("timestamp", itemHeaderTime);
                    JsonObject item = new JsonObject();
                    item.add("header", itemHeader);
                    item.addProperty("message", message);
                    jsonArray.add(item);
                }
                JsonObject jsonObject = new JsonObject();
                jsonObject.add("logcatMessages", jsonArray);
                String json = jsonObject.toString();
                File parentFile = file.getParentFile();
                File fileNew = new File(parentFile.getPath() + "/" + file.getName().replace(".log", "") + ".logcat");
                if (fileNew.exists()) fileNew.delete();
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileNew));
                bufferedWriter.write(json);
                bufferedWriter.flush();
                NotifyUtil.notify("日志转换完成", event.getProject());
                try {
                    Desktop.getDesktop().open(parentFile);
                } catch (Exception e) {
                    e.printStackTrace();
                    NotifyUtil.notifyError("打开目录异常：" + e.getMessage(), event.getProject());
                }
            } catch (Exception e) {
                e.printStackTrace();
                NotifyUtil.notifyError("日志转换异常：" + e.getMessage(), event.getProject());
            }
        }
    }
}
