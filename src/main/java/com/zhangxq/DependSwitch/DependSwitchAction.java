package com.zhangxq.DependSwitch;

import com.google.gson.Gson;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class DependSwitchAction extends AnAction {
    private Project project;
    private File currentFile; // 工程目录文件

    @Override
    public void actionPerformed(AnActionEvent e) {
        project = e.getProject();
        if (project == null) return;
        String basePath = project.getBasePath();
        if (basePath != null) {
            currentFile = new File(basePath); // 当前目录
            File[] moduleFiles = currentFile.listFiles(((dir, name) -> name.equals("local_module.json")));
            if (moduleFiles == null || moduleFiles.length == 0) {
                // local_module.json 不存在
                findLocalModule();
            } else {
                File moduleFile = moduleFiles[0];
                byte[] bytes;
                try {
                    bytes = Files.readAllBytes(moduleFile.toPath());
                } catch (IOException ex) {
                    bytes = new byte[]{};
                }
                if (bytes.length > 0) {
                    String res = new String(bytes);
                    showResult(res);
                } else {
                    moduleFile.delete();
                    findLocalModule();
                }
            }
        }
    }

    private void showResult(String json) {
        try {
            Gson gson = new Gson();
            ModuleConfig moduleConfig = gson.fromJson(json, ModuleConfig.class);
            new ModuleListDialog(moduleConfig.localModules, new ModuleListDialog.Callback() {
                @Override
                public void onReset() {
                    findLocalModule();
                }

                @Override
                public void onRefresh(List<ModuleItemConfig> list) {
                    NotifyUtil.notify("refresh", project);
                }
            }).setVisible(true);
        } catch (Exception e) {
            NotifyUtil.notifyError(e.getMessage(), project);
            findLocalModule();
        }
    }

    private void findLocalModule() {
        File rootFile = currentFile.getParentFile(); // 上级目录
        if (rootFile != null) {
            File[] rootFiles = rootFile.listFiles((dir, name) -> name.contains("litmatch_base"));
            if (rootFiles == null || rootFiles.length == 0) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                fileChooser.setAcceptAllFileFilterUsed(false);

                // 打开文件选择框（线程将被阻塞, 直到选择框被关闭）
                int result = fileChooser.showOpenDialog(null);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    parseLocalModule(file);
                }
            } else {
                File baseModuleFile = rootFiles[0];
                parseLocalModule(baseModuleFile);
            }
        }
    }

    private void parseLocalModule(File muduleFile) {
        NotifyUtil.notify("parseLocalModule", project);
    }
}
