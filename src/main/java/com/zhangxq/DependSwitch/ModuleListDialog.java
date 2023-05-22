package com.zhangxq.DependSwitch;

import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

public class ModuleListDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonReset;
    private JScrollPane jScrollPanel;

    String[] cNames = {"依赖", "开关"};
    Object[][] rowData = {
            {"com.lit.app.component:imageviewer", 1},
            {"2019T-US002414T", 2},
            {"2019T-US002415T", 3},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4},
            {"2019T-US002416T", 4}
    };

    public ModuleListDialog(List<ModuleItemConfig> list, Callback callback) {
        setContentPane(contentPane);
        setModal(true);

        setSize(new Dimension(500, 500));

        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 150, kit.getScreenSize().height / 2 - 250);

        DefaultTableModel model=new DefaultTableModel(rowData, cNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JBTable table = new JBTable(model);

        // 设置行高
        table.setRowHeight(30);

        // 设置第一列宽度
        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setPreferredWidth(400);

        // 设置表头居中
        DefaultTableCellRenderer render = new DefaultTableCellRenderer();
        render.setHorizontalAlignment(SwingConstants.CENTER);
        table.getTableHeader().setDefaultRenderer(render);

        // 设置第二列为checkbox
        cm.getColumn(1).setCellRenderer(new CheckBoxRender());

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
