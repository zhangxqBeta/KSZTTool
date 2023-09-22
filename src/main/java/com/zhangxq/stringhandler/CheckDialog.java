package com.zhangxq.stringhandler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CheckDialog extends JDialog {
    private JPanel contentPane;
    private JButton btnCancel;
    private JButton btnContinue;
    private JLabel labelTitle;

    public CheckDialog(String title, Callback callback) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnContinue);

        labelTitle.setText(title);
        setSize(new Dimension(600, 150));

        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 300, kit.getScreenSize().height / 2 - 75);

        contentPane.setSize(new Dimension(600, 150));


        btnCancel.addActionListener(e -> {
            dispose();
            if (callback != null) callback.onCancel();
        });

        btnContinue.addActionListener(e -> {
            dispose();
            if (callback != null) callback.onContinue();
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
        contentPane.registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    public interface Callback {
        void onContinue();
        void onCancel();
    }
}
