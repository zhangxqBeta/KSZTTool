package com.zhangxq.colorfinder;

import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;

public class ColorAddDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JLabel lbColorLight;
    private JLabel lbColorNight;
    private JTextField tfName;

    public ColorAddDialog(String colorLight, String colorNight, Project project) {
        setContentPane(contentPane);
        getRootPane().setDefaultButton(buttonOK);

        setMinimumSize(new Dimension(400, 190));
        setMaximumSize(new Dimension(400, 190));
        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 150, kit.getScreenSize().height / 2 - 95);

        lbColorLight.setText("亮色：" + colorLight);
        lbColorNight.setText("暗色：" + colorNight);

        buttonOK.addActionListener(e -> {
            String name = tfName.getText().trim();
            if (name.length() == 0) {
                NotifyUtil.notify("请输入颜色名称", project);
                return;
            }

            String filePath = project.getBasePath() + "/app/src/main/res";
//            String filePath = "/Users/zhangxq/work/litmatch_app/app/src/main/res";
            File file = new File(filePath);
            if (file.exists()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File item : files) {
                        if (item.getName().equals("values")) {
                            addColor(item, project, colorLight, name);
                        }

                        if (item.getName().equals("values-night")) {
                            addColor(item, project, colorNight, name);
                        }
                    }
                }
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

    private void addColor(File dir, Project project, String color, String newName) {
        File[] colorsFiles = dir.listFiles(new ColorsNameFilter());
        try {
            SAXBuilder builder = new SAXBuilder();
            File fileNew;
            Document doc;
            Element root;
            if (colorsFiles == null || colorsFiles.length == 0) {
                fileNew = new File(dir.getAbsolutePath() + "/colors_auto.xml");
                boolean isSuccess = fileNew.createNewFile();
                if (!isSuccess) {
                    NotifyUtil.notifyError("创建文件失败", project);
                }
                doc = new Document();
                root = new Element("resources");
                doc.setRootElement(root);
            } else {
                fileNew = colorsFiles[0];
                doc = builder.build(fileNew);
                root = doc.getRootElement();
            }

            // 检查是否已存在
            for(Element element : root.getChildren()) {
                String name = element.getAttributeValue("name");
                if (name.equals(newName)) {
                    NotifyUtil.notifyError("名字已存在", project);
                    return;
                }
            }

            // 新增element
            Element stringItem = new Element("color");
            stringItem.setAttribute("name", newName);
            stringItem.addContent(color);
            root.addContent(stringItem);

            // 写入文件
            Format format= Format.getCompactFormat();
            format.setEncoding("utf-8");
            format.setIndent("    ");
            format.setLineSeparator("\n");
            XMLOutputter out = new XMLOutputter(format);
            out.output(doc, new FileOutputStream(fileNew));

            NotifyUtil.notify("操作成功", project);
            dispose();
        } catch (Exception ex) {
            NotifyUtil.notifyError("添加异常：" + ex.getMessage(), project);
        }
    }

    private static class ColorsNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals("colors_auto.xml");
        }
    }
}
