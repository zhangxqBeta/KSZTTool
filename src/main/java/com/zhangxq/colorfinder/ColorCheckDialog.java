package com.zhangxq.colorfinder;

import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ColorCheckDialog extends JDialog {
    private static final String COLOR_PATTERN = "^#([0-9a-fA-F]{8}|[0-9a-fA-F]{6})$";
    private JPanel contentPane;
    private JButton btnSearch;
    private JButton btnAdd;
    private JTextField tfLightColor;
    private JTextField tfNightColor;
    private JTextField tfResult;
    private final Project project;
    private final java.util.List<File> colorFileList = new ArrayList<>();
    private final List<File> nightColorFileList = new ArrayList<>();

    public ColorCheckDialog(Project project) {
        this.project = project;
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnSearch);

        setSize(new Dimension(400, 300));
        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 200, kit.getScreenSize().height / 2 - 150);

        btnSearch.addActionListener(e -> {
            String lightColor = tfLightColor.getText().trim();
            String nightColor = tfNightColor.getText().trim();

            if (!Pattern.matches(COLOR_PATTERN, lightColor) || !Pattern.matches(COLOR_PATTERN, nightColor)) {
                NotifyUtil.notify("颜色格式不正确", project);
                return;
            }

            List<String> result = findColor(lightColor, nightColor);
            if (result.size() > 0) {
                tfResult.setText(result.get(0));
            }
        });

        btnAdd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

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

    private List<String> findColor(String lightColor, String nightColor) {
//        File file = new File(project.getBasePath() + "/app/src/main/res");
        File file = new File("/Users/zhangxq/work/litmatch_app/app/src/main/res");
        if (file.exists()) {
            findFile(file);
        }
        if (colorFileList.size() > 0 && nightColorFileList.size() > 0) {
            // 拿到所有白天模式颜色
            Map<String, String> lightColorMap = getColorMap(colorFileList);
            // 拿到所有暗色模式颜色
            Map<String, String> nightColorMap = getColorMap(nightColorFileList);

            List<String> matchLightColor = getColorNames(lightColor.toLowerCase(), lightColorMap);
            List<String> matchNightColor = getColorNames(nightColor.toLowerCase(), nightColorMap);
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

    private List<String> getColorNames(String color, Map<String, String> colorMap) {
        List<String> result = new ArrayList<>();
        for (String name : colorMap.keySet()) {
            String value = formatColor(colorMap.get(name).toLowerCase());
            color = formatColor(color);

            if (value.equals(color)) {
                result.add(name);
            }
        }
        return result;
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

    private void findFile(File file) {
        File[] files = file.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                findFile(f);
            } else {
                if (f.getName().startsWith("color")) {
                    if (file.getName().contains("night")) {
                        nightColorFileList.add(f);
                    } else {
                        colorFileList.add(f);
                    }
                }
            }
        }
    }
}
