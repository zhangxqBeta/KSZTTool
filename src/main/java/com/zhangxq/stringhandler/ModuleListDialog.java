package com.zhangxq.stringhandler;

import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class ModuleListDialog extends JDialog {
    private JPanel contentPane;
    private JLabel jLabelTitle;
    private JScrollPane jScrollPanel;

    public ModuleListDialog(List<String> moduleNames, Callback callback) {
        setContentPane(contentPane);
        setModal(true);

        jLabelTitle.setFont(new Font(Font.DIALOG, Font.BOLD, 17));
        setSize(new Dimension(300, 500));

        Toolkit kit = Toolkit.getDefaultToolkit();
        setLocation(kit.getScreenSize().width / 2 - 150, kit.getScreenSize().height / 2 - 250);

        jScrollPanel.setSize(new Dimension(300, 500));
        ListModel<String> jListModel =  new DefaultComboBoxModel<>(moduleNames.toArray(new String[0]));
        JBList<String> jbList = new JBList<>(jListModel);
        jScrollPanel.setViewportView(jbList);
        jbList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    dispose();
                    if (callback != null) callback.onItemClick(jbList.getSelectedIndex());
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
