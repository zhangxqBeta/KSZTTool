package com.zhangxq.DependSwitch;

import com.google.gson.Gson;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class DependSwitchAction extends AnAction {
    private Project project;
    private File currentFile; // 工程目录文件
    private final Gson gson = new Gson();
    private AnActionEvent actionEvent;

    @Override
    public void actionPerformed(AnActionEvent e) {
        actionEvent = e;
        project = e.getProject();
        if (project == null) return;
        String basePath = project.getBasePath();
        if (basePath != null) {
            currentFile = new File(basePath); // 当前目录
            parseLocalJson();
        }
    }

    /**
     * 解析本地json配置文件
     */
    private void parseLocalJson() {
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

    /**
     * 展示解析结果
     * @param json
     */
    private void showResult(String json) {
        try {
            ModuleConfig moduleConfig = gson.fromJson(json, ModuleConfig.class);
            new ModuleListDialog(moduleConfig.localModules, new ModuleListDialog.Callback() {
                @Override
                public void onReset() {
                    findLocalModule();
                }

                @Override
                public void onRefresh(List<ModuleItemConfig> list) {
                    updateLocalJson(list);
                    ActionManager.getInstance().getAction("Android.SyncProject").actionPerformed(actionEvent);
                }
            }).setVisible(true);
        } catch (Exception e) {
            NotifyUtil.notifyError(e.getMessage(), project);
            findLocalModule();
        }
    }

    private void updateLocalJson(List<ModuleItemConfig> list) {
        ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.composingBuild = true;
        moduleConfig.localModules = list;
        String json = gson.toJson(moduleConfig);
        File[] moduleFiles = currentFile.listFiles(((dir, name) -> name.equals("local_module.json")));
        File fileJson;
        try {
            if (moduleFiles == null || moduleFiles.length == 0) {
                fileJson = new File(currentFile.getPath() + "/local_module.json");
                fileJson.createNewFile();
            } else {
                fileJson = moduleFiles[0];
            }
            Files.write(fileJson.toPath(), json.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void findLocalModule() {
        File rootFile = currentFile.getParentFile(); // 上级目录
        if (rootFile != null) {
            File[] rootFiles = rootFile.listFiles((dir, name) -> name.contains("litmatch_base"));
            if (rootFiles == null || rootFiles.length == 0) {
                chooseDir();
            } else {
                File baseModuleFile = rootFiles[0];
                parseLocalModule(baseModuleFile);
            }
        }
    }

    private void chooseDir() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        // 打开文件选择框（线程将被阻塞, 直到选择框被关闭）
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            parseLocalModule(file);
        }
    }

    private void parseLocalModule(File moduleFile) {
        File[] composeList = moduleFile.listFiles(File::isDirectory);
        if (composeList == null) {
            NotifyUtil.notifyError("未找到 module 目录，请重新选择路径！", project);
            chooseDir();
            return;
        }

        List<ModuleItemConfig> list = new ArrayList<>();

        for (File file : composeList) {
            parseFile(file, "", list);
        }

        updateLocalJson(list);
        parseLocalJson();
    }

    private void parseFile(File file, String composeName, List<ModuleItemConfig> list) {
        File[] buildFiles = file.listFiles((dir, name) -> name.equals("gradle.properties"));
        if (buildFiles == null || buildFiles.length == 0) return;
        try {
            File buildFile = buildFiles[0];
            List<String> lines = Files.readAllLines(buildFile.toPath());

            if (composeName == null || composeName.length() == 0) {
                String group = "";
                for (String line : lines) {
                    if (line.toLowerCase().contains("group")) {
                        String[] splits = line.split("=");
                        if (splits.length == 2) {
                            group = splits[1];
                        }
                    }
                }
                if (group != null && group.length() > 0) {
                    // 找到一个library集合
                    File[] composeList = file.listFiles(File::isDirectory);
                    if (composeList == null) return;
                    for (File item : composeList) {
                        parseFile(item, group, list);
                    }
                }
            } else {
                String pom_name = "";
                String pom_artifact_id = "";
                for (String line : lines) {
                    if (line.toLowerCase().contains("pom_name")) {
                        String[] splits = line.split("=");
                        if (splits.length == 2) {
                            pom_name = splits[1];
                        }
                    }

                    if (line.toLowerCase().contains("pom_artifact_id")) {
                        String[] splits = line.split("=");
                        if (splits.length == 2) {
                            pom_artifact_id = splits[1];
                        }
                    }
                }

                if (pom_name.length() > 0 && pom_artifact_id.length() > 0) {
                    ModuleItemConfig itemConfig = new ModuleItemConfig();
                    itemConfig.module_name = pom_name;
                    itemConfig.module_dir = file.getParentFile().getAbsolutePath();
                    itemConfig.package_name = composeName + ":" + pom_artifact_id;
                    list.add(itemConfig);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
