package com.zhangxq.dependswitch;

import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class ModuleListDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonReset;
    private JScrollPane jScrollPanel;
    private JButton buttonCancel;
    String[] cNames = {"依赖", "开关"};
    public ModuleListDialog(List<ModuleItemConfig> list, Callback callback) {
        setContentPane(contentPane);
        setModal(true);

        setSize(new Dimension(500, 500));

        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 150, kit.getScreenSize().height / 2 - 250);

        Object[][] rowData = new Object[list.size()][2];
        for (int i = 0; i < list.size(); i++) {
            rowData[i][0] = list.get(i).module_name;
            rowData[i][1] = list.get(i).useLocal;
        }

        DefaultTableModel model=new DefaultTableModel(rowData, cNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return Boolean.class;
                return String.class;
            }
        };
        JBTable table = new JBTable(model);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                int row = table.getSelectedRow();
                boolean isCheck = !(boolean) rowData[row][1];
                rowData[row][1] = isCheck;
                table.updateUI();

                for (ModuleItemConfig item : list) {
                    if (item.module_name.equals(rowData[row][0])) {
                        item.useLocal = isCheck;
                    }
                }
            }
        });

        // 设置行高
        table.setRowHeight(30);

        // 设置第一列宽度
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(450);
        cm.getColumn(1).setPreferredWidth(50);

        // 设置表头居中
        DefaultTableCellRenderer render = new DefaultTableCellRenderer();
        render.setHorizontalAlignment(SwingConstants.CENTER);
        table.getTableHeader().setDefaultRenderer(render);

        // 设置第二列为checkbox
        cm.getColumn(1).setCellRenderer((table1, value, isSelected, hasFocus, row, column) -> {
            JCheckBox checkBox = new JCheckBox();
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
            checkBox.setSelected((Boolean) rowData[row][1]);
            return checkBox;
        });

        jScrollPanel.setViewportView(table);

        buttonOK.addActionListener(e -> {
            dispose();
            if (callback != null) {
                callback.onRefresh(list);
            }
        });
        buttonReset.addActionListener(e -> {
            dispose();
            if (callback != null) {
                callback.onReset();
            }
        });
        buttonCancel.addActionListener(e -> dispose());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onCancel() {
        dispose();
    }

    public interface Callback {
        void onReset();
        void onRefresh(List<ModuleItemConfig> list);
    }
}
