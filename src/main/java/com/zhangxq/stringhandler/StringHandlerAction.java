package com.zhangxq.stringhandler;

import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.util.*;

public class StringHandlerAction extends AnAction {
    private static final Logger logger = Logger.getInstance(StringHandlerAction.class);
    private final Set<String> keys = new HashSet<>(Arrays.asList("EN", "SA", "AR", "ID", "JP", "MY", "BR", "RU", "TH", "TR", "VN", "TW"));
    private static final String TAGS = "Tags";
    public static String fileName = "string_auto.xml";
    private static int defaultNum = 0;
    private Project project;

    @Override
    public void actionPerformed(AnActionEvent e) {
        project = e.getProject();
        if (project != null) {
            fileChoose(project.getBasePath());
        }
    }

    private void fileChoose(String projectPath) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileFilter(new FileNameExtensionFilter("File(*.xlsx, *.xls)", "xlsx", "xls"));

        // 打开文件选择框（线程将被阻塞, 直到选择框被关闭）
        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            logger.debug("项目目录：" + projectPath);
            logger.debug("选择文件：" + file.getAbsolutePath());
            List<List<String>> excelData = parseXls(file);
            setValuesToProject(projectPath, excelData);
        }
    }

    public List<List<String>> parseXls(File excel) {
        try {
            String[] split = excel.getName().split("\\.");
            if (split.length < 2) {
                notify("操作失败：文件名格式错误[" + excel.getName() + "]", NotificationType.ERROR);
                return null;
            }
            Workbook wb;
            if ("xls".equals(split[1])) {
                FileInputStream fileInputStream = new FileInputStream(excel);
                wb = new HSSFWorkbook(fileInputStream);
            } else if ("xlsx".equals(split[1])) {
                wb = new XSSFWorkbook(excel);
            } else {
                notify("操作失败：非excel文件后缀！！", NotificationType.ERROR);
                return null;
            }

            Sheet sheet = wb.getSheetAt(0);
            List<List<String>> result = new ArrayList<>();
            Row firstRow = sheet.getRow(0);
            if (firstRow == null) return null;
            for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                result.add(new ArrayList<>());
                for (int j = firstRow.getFirstCellNum(); j <= firstRow.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    String cellValue = cell == null ? "" : cell.getStringCellValue();
                    if (cellValue == null || cellValue.isEmpty()) cellValue = "";
                    result.get(i).add(cellValue);
                }
            }
            wb.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void setValuesToProject(String projectPath, List<List<String>> data) {
        projectPath = projectPath + "/app/src/main/res";
//        projectPath = "/Users/construct/work/litmatch_app/app/src/main/res";
        File file = new File(projectPath);
        FilenameFilter filenameFilter = new StringsNameFilter();
        if (file.exists()) {
            File[] files = file.listFiles();
            if (files != null && data != null && data.size() > 1) {
                Map<String, List<String>> dataMap = new HashMap<>();
                Map<Integer, String> columnMap = new HashMap<>();
                List<String> tags = new ArrayList<>();
                List<String> tops = data.get(0);
                for (int i = 0; i < tops.size(); i++) {
                    String top = tops.get(i);
                    if (top.equals(TAGS) || keys.contains(top)) {
                        dataMap.put(top, new ArrayList<>());
                        columnMap.put(i, top);
                    }
                }

                for (int i = 1; i < data.size(); i++) {
                    List<String> datum = data.get(i);
                    for (int j = 0; j < datum.size(); j++) {
                        if (columnMap.containsKey(j)) {
                            String cell = datum.get(j);
                            String key = columnMap.get(j);
                            if (key.equals(TAGS)) {
                                if (cell == null || cell.isEmpty()) cell = "tag_" + System.currentTimeMillis() + "_" + defaultNum++;
                                tags.add(cell);
                            } else {
                                dataMap.get(columnMap.get(j)).add(cell);
                            }
                        }
                    }
                }

                for (File item : files) {
                    String pathName = item.getName();
                    String pathNameSuffix = pathName.substring(pathName.length() - 2);
                    if (item.getName().equals("values") || keys.contains(pathNameSuffix)) {
                        logger.debug("目录：" + item.getAbsolutePath());
                        File[] stringfiles = item.listFiles(filenameFilter);

                        try {
                            SAXBuilder builder = new SAXBuilder();
                            File fileNew;
                            Document doc;
                            Element root;
                            if (stringfiles == null || stringfiles.length == 0) {
                                fileNew = new File(item.getAbsolutePath() + "/" + fileName);
                                boolean isSuccess = fileNew.createNewFile();
                                if (!isSuccess) continue;
                                doc = new Document();
                                root = new Element("resources");
                                Namespace tools = Namespace.getNamespace("tools", "http://schemas.android.com/tools");
                                root.addNamespaceDeclaration(tools);
                                root.setAttribute("ignore", "MissingTranslation", tools);
                                doc.setRootElement(root);
                            } else {
                                fileNew = stringfiles[0];
                                doc = builder.build(fileNew);
                                root = doc.getRootElement();
                            }

                            List<String> newStrings = dataMap.get(pathNameSuffix);
                            if (item.getName().equals("values")) newStrings = dataMap.get("EN");
                            if (newStrings != null && newStrings.size() == tags.size()) {
                                for (int i = 0; i < newStrings.size(); i++) {
                                    String content = newStrings.get(i);
                                    if (content == null || content.length() == 0) continue;
                                    content = content.replace("\"", "\\\"");
                                    content = content.replace("'", "\\'");
                                    Element stringItem = new Element("string");
                                    stringItem.setAttribute("name", tags.get(i));
                                    stringItem.addContent(content);
                                    root.addContent(stringItem);
                                }
                            }

                            Format format= Format.getCompactFormat();
                            format.setEncoding("utf-8");
                            format.setIndent("    ");
                            format.setLineSeparator("\n");

                            XMLOutputter out = new XMLOutputter(format);
                            out.output(doc, new FileOutputStream(fileNew));
                        } catch (JDOMException | IOException e) {
                            e.printStackTrace();
                            notify("操作异常：" + e.getMessage(), NotificationType.ERROR);
                        }
                    }
                }

                notify("操作完成", NotificationType.INFORMATION);
            }
        }
    }

    private static class StringsNameFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.equals(fileName);
        }
    }

    private void notify(String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("StringHandler")
                .createNotification(content, type)
                .notify(project);
    }
}
