package com.zhangxq.DependSwitch;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CheckBoxRender implements TableCellRenderer {
    private JPanel jPanel;
    private JCheckBox jCheckBox;

    public CheckBoxRender() {
        initJPanel();
        initButton();
        jPanel.add(jCheckBox);
    }

    private void initButton() {
        jCheckBox = new JCheckBox();
        jCheckBox.setBounds(2, 3, 80, 30);
        jCheckBox.addActionListener(
                new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        System.out.println("" + e.getActionCommand());
                        System.out.println(jCheckBox.getText());
                    }
                });
    }

    private void initJPanel() {
        jPanel = new JPanel();
        jPanel.setLayout(null);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        return jCheckBox;
    }
}