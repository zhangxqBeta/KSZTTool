package com.zhangxq.stringhandler;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;

public class SetNameDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JTextField textFieldName;

    public SetNameDialog(Project project, Callback callback) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        setSize(new Dimension(510, 120));
        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 255, kit.getScreenSize().height / 2 - 60);

        buttonOK.addActionListener(e -> {
            String name = textFieldName.getText().trim();
            if (name.length() == 0) {
                NotifyUtil.notifyError("输入内容不能为空", project);
            } else {
                dispose();
                if (callback != null) callback.onSetName(name);
            }
        });
    }

    public interface Callback {
        void onSetName(String name);
    }
}
