package com.zhangxq.stringhandler;

import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SheetListDialog extends JDialog {
    private JPanel contentPane;
    private JScrollPane jScrollPane;
    private JLabel jLabelTitle;

    public SheetListDialog(String[] sheetNames, Callback callback) {
        setContentPane(contentPane);
        setModal(true);

        jLabelTitle.setFont(new Font(Font.DIALOG, Font.BOLD, 17));
        setSize(new Dimension(300, 500));

        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 150, kit.getScreenSize().height / 2 - 250);

        jScrollPane.setSize(new Dimension(300, 500));
        ListModel<String> jListModel =  new DefaultComboBoxModel<>(sheetNames);
        JBList<String> jbList = new JBList<>(jListModel);
        jScrollPane.setViewportView(jbList);
        jbList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (callback != null) callback.onItemClick(jbList.getSelectedIndex());
                    dispose();
                }
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public interface Callback {
        void onItemClick(int position);
    }
}
