package com.zhangxq.stringhandler;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.*;

public class StringHandlerAction extends AnAction {
    private static final Logger logger = Logger.getInstance(StringHandlerAction.class);
    private final Set<String> keys = new HashSet<>(Arrays.asList("EN", "SA", "AR", "ID", "JP", "MY", "BR", "RU", "TH", "TR", "VN", "TW"));
    private static final String TAGS = "Tags";
    public static String fileName = "string_auto.xml";
    private Project project;

    @Override
    public void actionPerformed(AnActionEvent e) {
        project = e.getProject();
        if (project != null) {
            new SetNameDialog(project, name -> {
                fileName = "string_" + name + ".xml";
                fileChoose(project.getBasePath());
            }).setVisible(true);
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
            parseXls(projectPath, file);
        }
    }

    public void parseXls(String projectPath, File excel) {
        try {
            String[] split = excel.getName().split("\\.");
            if (split.length < 2) {
                NotifyUtil.notifyError("操作失败：文件名格式错误[" + excel.getName() + "]", project);
                return;
            }
            Workbook wb;
            if ("xls".equals(split[1])) {
                FileInputStream fileInputStream = new FileInputStream(excel);
                wb = new HSSFWorkbook(fileInputStream);
            } else if ("xlsx".equals(split[1])) {
                wb = new XSSFWorkbook(excel);
            } else {
                NotifyUtil.notifyError("操作失败：非excel文件后缀！！", project);
                return;
            }

            // 展示sheet列表，选择一个
            int sheetNum = wb.getNumberOfSheets();
            if (sheetNum > 1) {
                String[] sheetNames = new String[sheetNum];
                for (int i = 0; i < sheetNum; i++) {
                    sheetNames[i] = wb.getSheetName(i);
                }
                JDialog dialog = new SheetListDialog(sheetNames, position -> parseSheet(projectPath, wb.getSheetAt(position)));
                dialog.setVisible(true);
            } else {
                parseSheet(projectPath, wb.getSheetAt(0));
            }

            wb.close();
        } catch (Exception e) {
            NotifyUtil.notifyError("解析异常：" + e.getMessage(), project);
        }
    }

    /**
     * 解析 sheet
     * @param projectPath
     * @param sheet
     */
    private void parseSheet(String projectPath, Sheet sheet) {
        List<List<String>> result = new ArrayList<>();
        Row firstRow = sheet.getRow(0);
        if (firstRow == null) {
            NotifyUtil.notifyError("首行为空！！", project);
            return;
        }

        for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
            result.add(new ArrayList<>());
            Row row = sheet.getRow(i);
            if (row == null) break;
            for (int j = 0; j < firstRow.getPhysicalNumberOfCells(); j++) {
                Cell cell = row.getCell(j);
                if (cell != null) {
                    cell.setCellType(CellType.STRING);
                    String cellValue = cell.getStringCellValue();
                    if (cellValue == null || cellValue.isEmpty()) cellValue = "";
                    if (i == 0) cellValue = cellValue.trim();
                    result.get(i).add(cellValue);
                } else {
                    result.get(i).add("");
                }
            }
        }

        if (result.size() > 0) {
            NotifyUtil.notify("excel解析完成", project);
            setValuesToProject(projectPath, result);
        } else {
            NotifyUtil.notifyError("解析数据为空", project);
        }
    }

    /**
     * 根据解析出的sheet结果，导入到excel中
     * @param projectPath
     * @param data
     */
    private void setValuesToProject(final String projectPath, List<List<String>> data) {
        // key = "EN", value = ["aa", "bb", "cc", "dd"]
        // key = "BR", value = ["ee", "ff", "gg", "hh"]
        // key = "AR", value = ["ii", "jj", "kk", "ll"]
        // ......
        Map<String, List<String>> dataMap = new HashMap<>(); // 保存过滤后的有效数据
        // key = 3, value = "Tags"
        // key = 4, value = "EN"
        // key = 5, value = "MY"
        // ......
        Map<Integer, String> columnMap = new HashMap<>(); // 保存行号和语言的对应关系
        // ["name1", "name2", "name3", "name4"]
        List<String> tags = new ArrayList<>(); // 保存自定义tag列表
        // ["功能模块", "页面", "中文", "Tags", "EN", "MY"]
        List<String> tops = data.get(0); // excel第一行

        for (int i = 0; i < tops.size(); i++) {
            String top = tops.get(i);
            if (top.equals(TAGS) || keys.contains(top)) { // 如果是 Tags，或者包含在预定义语言类型中，就是有效数据，需要保存
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
                        if (cell != null && !cell.isEmpty()) {
                            tags.add(cell);
                        } else {
                            break;
                        }
                    } else {
                        dataMap.get(columnMap.get(j)).add(cell);
                    }
                }
            }
        }

        List<String> enList = dataMap.get("EN");
        if (enList == null || enList.size() == 0) {
            NotifyUtil.notifyError("EN列不能为空", project);
            return;
        }

        checkFormat(dataMap, new ActionDialog.Callback() {
            @Override
            public void onContinue() {
                String filePath = projectPath + "/app/src/main/res";
//                String filePath = "/Users/zhangxq/work/litmatch_app/app/src/main/res";
                File file = new File(filePath);
                FilenameFilter filenameFilter = new StringsNameFilter();
                if (file.exists()) {
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File item : files) {
                            String pathName = item.getName();
                            String pathNameSuffix = pathName.substring(pathName.length() - 2);
                            if (item.getName().equals("values") || keys.contains(pathNameSuffix)) {
                                logger.debug("目录：" + item.getAbsolutePath());
                                File[] stringFiles = item.listFiles(filenameFilter);

                                try {
                                    SAXBuilder builder = new SAXBuilder();
                                    File fileNew;
                                    Document doc;
                                    Element root;
                                    if (stringFiles == null || stringFiles.length == 0) {
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
                                        fileNew = stringFiles[0];
                                        doc = builder.build(fileNew);
                                        root = doc.getRootElement();
                                    }

                                    List<String> newStrings = dataMap.get(pathNameSuffix);
                                    if (item.getName().equals("values")) newStrings = dataMap.get("EN");
                                    if (newStrings != null && newStrings.size() == tags.size()) {
                                        // 更新操作
                                        for(Element element : root.getChildren()) {
                                            for (int i = 0; i < newStrings.size(); i++) {
                                                String content = newStrings.get(i);
                                                if (content == null || content.length() == 0) continue;
                                                String name = element.getAttributeValue("name");
                                                if (name.equals(tags.get(i))) {// 发现已经存在同名属性，做更新操作
                                                    content = content.replace("\"", "\\\"");
                                                    content = content.replace("'", "\\'");
                                                    element.removeContent();
                                                    element.addContent(content);
                                                    newStrings.set(i, ""); // 命中更新后，内容设置为空，防止后续再次插入
                                                }
                                            }
                                        }

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
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    NotifyUtil.notifyError("插入异常：" + e.getMessage(), project);
                                }
                            }
                        }
                        NotifyUtil.notify("操作完成", project);
                    } else {
                        NotifyUtil.notifyError("res 为空目录", project);
                    }
                } else {
                    NotifyUtil.notifyError("res 目录不存在", project);
                }
            }

            @Override
            public void onCancel() {
                NotifyUtil.notify("已取消", project);
            }
        });
    }

    private void checkFormat(Map<String, List<String>> dataMap, ActionDialog.Callback callback) {
        int size = dataMap.get("EN").size();
        // 检查 %s 个数是否一致
        boolean isWrong = doCheck(size, "%s", dataMap, callback);
        if (isWrong) return;
        // 检查 %d 个数是否一致
        isWrong = doCheck(size, "%d", dataMap, callback);
        if (isWrong) return;

        // 检查没问题，继续导入
        callback.onContinue();
    }

    private boolean doCheck(int size, String formatStr, Map<String, List<String>> dataMap, ActionDialog.Callback callback) {
        for (int i = 0; i < size; i++) {
            // 计算英语翻译中 %s 个数
            int countSEN = getSubStrCount(dataMap.get("EN").get(i), formatStr);
            for (String key : dataMap.keySet()) {
                if (dataMap.get(key) == null || dataMap.get(key).size() <= i || dataMap.get(key).get(i).length() == 0) continue;
                String content = dataMap.get(key).get(i);
                // 计算其他翻译中 %s 个数
                int countSItem = getSubStrCount(content, formatStr);
                // 如果 %s 个数不相等，则存在问题
                if (countSItem != countSEN) {
                    String title;
                    if (countSItem < countSEN) {
                        // 如果当前翻译 %s 个数少于英语翻译，提示当前翻译有误
                        title = "<html><body>国家：" + key + "<br>内容：" + content + "<br>" + "错误：[可能缺少" + (countSEN - countSItem) + "个 " + formatStr + "]" + "<body></html>";
                    } else {
                        // 如果当前翻译 %s 个数大于英语翻译，提示当前英语有误
                        title = "<html><body>国家：EN<br>内容：" + dataMap.get("EN").get(i) + "<br>" + "错误：[可能缺少" + (countSItem - countSEN) + "个 " + formatStr + "]" + "<body></html>";
                    }
                    new ActionDialog(title, callback).setVisible(true);
                    return true;
                }
            }
        }
        return false;
    }

    private int getSubStrCount(String content, String subStr) {
        int count = 0;
        for (int i = 0; i <= content.length() - subStr.length(); i++) {
            if (content.startsWith(subStr, i)) {
                count++;
            }
        }
        return count;
    }

    private static class StringsNameFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.equals(fileName);
        }
    }
}
