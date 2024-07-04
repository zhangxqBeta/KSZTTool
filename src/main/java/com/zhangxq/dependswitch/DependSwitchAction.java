package com.zhangxq.dependswitch;

import com.google.common.reflect.TypeToken;
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
            List<ModuleItemConfig> list = gson.fromJson(json, new TypeToken<List<ModuleItemConfig>>(){}.getType());
            new ModuleListDialog(list, new ModuleListDialog.Callback() {
                @Override
                public void onReset() {
                    chooseDir(currentFile);
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
        String json = gson.toJson(list);
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
            File[] rootFiles = rootFile.listFiles(pathname -> !pathname.getPath().equals(currentFile.getPath()));
            if (rootFiles == null || rootFiles.length == 0) {
                chooseDir(currentFile);
            } else {
                parseLocalModule(rootFiles);
            }
        }
    }

    private void chooseDir(File currentFile) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);

        // 打开文件选择框（线程将被阻塞, 直到选择框被关闭）
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (currentFile.getPath().equals(file.getPath())) {
                NotifyUtil.notifyError("不能选择当前工程", project);
            } else {
                File[] files = new File[1];
                files[0] = file;
                parseLocalModule(files);
            }
        }
    }

    private void parseLocalModule(File[] moduleFiles) {
        List<ModuleItemConfig> list = new ArrayList<>();
        for (File moduleFile : moduleFiles) {
            parseFile(moduleFile, list);
        }
        updateLocalJson(list);
        parseLocalJson();
    }

    private void parseFile(File file, List<ModuleItemConfig> list) {
        File[] buildFiles = file.listFiles((dir, name) -> name.equals("gradle.properties"));
        if (buildFiles != null && buildFiles.length > 0) {
            try {
                File buildFile = buildFiles[0];
                List<String> lines = Files.readAllLines(buildFile.toPath());
                String group = "";
                String pom_artifact_id = "";
                for (String line : lines) {
                    if (line.toLowerCase().contains("group")) {
                        String[] splits = line.split("=");
                        if (splits.length == 2) {
                            group = splits[1].trim();
                        }
                    }

                    if (line.toLowerCase().contains("pom_artifact_id") || line.toLowerCase().contains("artifactid")) {
                        String[] splits = line.split("=");
                        if (splits.length == 2) {
                            pom_artifact_id = splits[1].trim();
                        }
                    }
                }
                if (pom_artifact_id.length() > 0 && group.length() > 0) {
                    ModuleItemConfig itemConfig = new ModuleItemConfig();
                    itemConfig.module_name = file.getName();
                    itemConfig.module_dir = file.getParentFile().getAbsolutePath();
                    itemConfig.package_name = group + ":" + pom_artifact_id;
                    list.add(itemConfig);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        File[] dirs = file.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File item : dirs) {
                parseFile(item, list);
            }
        }
    }
}
