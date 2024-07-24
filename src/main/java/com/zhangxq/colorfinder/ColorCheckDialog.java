package com.zhangxq.colorfinder;

import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;
import kotlin.Triple;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class ColorCheckDialog extends JDialog {
    private static final String COLOR_PATTERN = "^#([0-9a-fA-F]{8}|[0-9a-fA-F]{6})$";
    private JPanel contentPane;
    private JButton btnSearch;
    private JTextField tfLightColor;
    private JTextField tfNightColor;
    private JTextArea taResult;
    private JScrollPane jsPanel;
    private final Project project;
    private final List<File> allColorFileList = new ArrayList<>();

    public ColorCheckDialog(Project project) {
        this.project = project;
        setContentPane(contentPane);
        getRootPane().setDefaultButton(btnSearch);

        setMinimumSize(new Dimension(400, 190));
        setMaximumSize(new Dimension(400, 190));
        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 200, kit.getScreenSize().height / 2 - 100);

        jsPanel.setMinimumSize(new Dimension(280, 200));
        jsPanel.setVisible(false);
        taResult.setEditable(false);

        btnSearch.addActionListener(e -> {
            String lightColor = tfLightColor.getText().trim();
            String nightColor = tfNightColor.getText().trim();

            if (!Pattern.matches(COLOR_PATTERN, lightColor) || !Pattern.matches(COLOR_PATTERN, nightColor)) {
                NotifyUtil.notify("颜色格式不正确，示例：#ffffff", project);
                return;
            }

            Triple<List<File>, List<File>, List<File>> files = findFile(project.getBasePath() + "/app/src/main/res");
            Triple<List<File>, List<File>, List<File>> filesLib = findFile(findStyleLibResPath());
            files.getFirst().addAll(filesLib.getFirst());
            files.getSecond().addAll(filesLib.getSecond());
            files.getThird().addAll(filesLib.getThird());
            List<File> lightColorFileList = files.getFirst();
            List<File> nightColorFileList = files.getSecond();
            List<File> singleColorFileList = files.getThird();
            List<String> result = findColor(lightColor, nightColor, lightColorFileList, nightColorFileList, singleColorFileList);
            if (!result.isEmpty()) {
                setMinimumSize(new Dimension(400, 390));
                setMaximumSize(new Dimension(400, 390));
                jsPanel.setVisible(true);
                StringBuilder resultContent = new StringBuilder();
                resultContent.append("查询到以下可用颜色：").append("\n");
                for (String str : result) {
                    resultContent.append(str).append("\n");
                }
                if (result.size() > 1) {
                    resultContent.append("\n").append("靓仔，顺便把重复颜色合并一下 (๑•̀ㅂ•́)و✧");
                }
                taResult.setText(resultContent.toString());
            } else {
                dispose();
                new ColorAddDialog(lightColor, nightColor, project).setVisible(true);
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
        contentPane.registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    // 寻找 style 库 res 目录的路径
    private String findStyleLibResPath() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            File baseFile = new File(project.getBasePath());
            File parentFile = baseFile.getParentFile();
            List<File> styleLibFiles = new ArrayList<>();
            findStyleLib(parentFile, styleLibFiles, 0);
            if (styleLibFiles.isEmpty()) {
                NotifyUtil.notifyError("lit-styles 目录未找到", project);
                return "";
            } else {
                return styleLibFiles.get(0).getPath() + "/src/main/res";
            }
        } else {
            return "";
        }
    }

    private void findStyleLib(File file, List<File> targetList, int layer) {
        // 递归到第 5 层就停止，减少无意义递归
        if (layer > 5) return;
        if ("lit-styles".equals(file.getName())) {
            targetList.add(file);
        } else {
            layer++;
            File[] files = file.listFiles();
            if (files != null) {
                for (File item : files) {
                    findStyleLib(item, targetList, layer);
                }
            }
        }
    }

    private Triple<List<File>, List<File>, List<File>> findFile(String path) {
        List<File> allColorFileList = new ArrayList<>();
        if (path == null || path.isEmpty()) return new Triple<>(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        File file = new File(path);
        if (file.exists()) {
            findFile(file, allColorFileList);
        }

        List<File> lightColorFileList = new ArrayList<>(); // 亮色模式颜色文件列表
        List<File> nightColorFileList = new ArrayList<>(); // 暗色模式颜色文件列表
        List<File> singleColorFileList = new ArrayList<>(); // 通用颜色文件列表

        // 第一遍循环过滤出暗色模式颜色文件
        Set<String> lightNightNames = new HashSet<>();
        for (File item : allColorFileList) {
            if (item.getParentFile() != null && item.getParentFile().getName().contains("night")) {
                nightColorFileList.add(item);
                lightNightNames.add(item.getName());
            }
        }
        // 第二遍循环过滤出与暗色模式文件对应的亮色模式颜色文件
        for (File item : allColorFileList) {
            if (lightNightNames.contains(item.getName()) && !nightColorFileList.contains(item)) {
                lightColorFileList.add(item);
            }
        }
        // 第三遍循环过滤出其他颜色文件
        for (File item : allColorFileList) {
            if (!lightColorFileList.contains(item) && !nightColorFileList.contains(item)) {
                singleColorFileList.add(item);
            }
        }
        return new Triple<>(lightColorFileList, nightColorFileList, singleColorFileList);
    }

    private List<String> findColor(String lightColor, String nightColor, List<File> lightColorFileList, List<File> nightColorFileList, List<File> singleColorFileList) {
        // 拿到所有单色颜色
        Map<String, String> singleColorMap = getColorMap(singleColorFileList);
        if (!lightColorFileList.isEmpty() && !nightColorFileList.isEmpty()) {
            // 拿到所有亮色模式颜色
            Map<String, String> lightColorMap = getColorMap(lightColorFileList);
            // 拿到所有暗色模式颜色
            Map<String, String> nightColorMap = getColorMap(nightColorFileList);

            List<String> matchLightColor = getColorNames(lightColor.toLowerCase(), lightColorMap, singleColorMap);
            List<String> matchNightColor = getColorNames(nightColor.toLowerCase(), nightColorMap, singleColorMap);
            List<String> result = new ArrayList<>();

            for (String out : matchLightColor) {
                for (String inner : matchNightColor) {
                    if (out.equals(inner)) {
                        result.add(inner);
                    }
                }
            }
            return result;
        }

        return new ArrayList<>();
    }

    /**
     * 获取匹配到的色值名称
     * @param color
     * @param colorMap
     * @param singleColorMap
     * @return
     */
    private List<String> getColorNames(String color, Map<String, String> colorMap, Map<String, String> singleColorMap) {
        List<String> result = new ArrayList<>();
        for (String name : colorMap.keySet()) {
            // 获取色值
            String value = colorMap.get(name);
            // 获取真实颜色值，有的颜色是 @color/text_main
            value = findTrueColor(value, colorMap, singleColorMap).toLowerCase();

            // 对颜色格式化
            value = formatColor(value);
            color = formatColor(color);

            if (value.equals(color)) {
                result.add(name);
            }
        }
        return result;
    }

    /**
     * 获取真实颜色值，有的颜色是 @color/text_main
     * @param color
     * @return
     */
    private String findTrueColor(String color, Map<String, String> colorMap, Map<String, String> singleColorMap) {
        if (color.startsWith("@color/")) {
            String name = color.substring(7);

            String resultColor = colorMap.get(name);
            if (resultColor != null) {
                return findTrueColor(resultColor, colorMap, singleColorMap);
            }

            resultColor = singleColorMap.get(name);
            if (resultColor != null) {
                return findTrueColor(resultColor, colorMap, singleColorMap);
            }

            NotifyUtil.notify("未找到" + color, project);
        }
        return color;
    }

    /**
     * 规范color格式
     * @param color
     * @return
     */
    private String formatColor(String color) {
        if (color.length() == 9 && color.startsWith("#ff")) {
            return "#" + color.substring(3);
        }
        return color;
    }

    /**
     * 解析出所有颜色名称和色值映射
     *
     * @param files
     * @return
     */
    private Map<String, String> getColorMap(List<File> files) {
        Map<String, String> nightColorMap = new HashMap<>();
        for (File fileColor : files) {
            try {
                SAXBuilder builder = new SAXBuilder();
                Document doc = builder.build(fileColor);
                Element root = doc.getRootElement();
                for(Element element : root.getChildren()) {
                    String name = element.getAttributeValue("name");
                    if (name != null) {
                        nightColorMap.put(name, element.getValue());
                    }
                }
            } catch (Exception e) {
                NotifyUtil.notifyError(fileColor.getName() + "解析出错：" + e.getMessage(), project);
            }
        }
        return nightColorMap;
    }

    private void findFile(File file, List<File> fileList) {
        File[] files = file.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findFile(f, fileList);
            } else {
                if (f.getName().contains("color") && f.getName().endsWith("xml")) {
                    fileList.add(f);
                }
            }
        }
    }
}
