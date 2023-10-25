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
import java.io.*;
import java.util.*;

public class StringHandlerAction extends AnAction {
    private static final Logger logger = Logger.getInstance(StringHandlerAction.class);
    private final Set<String> keys = new HashSet<>(Arrays.asList("EN", "SA", "AR", "ID", "JP", "MY", "BR", "RU", "TH", "TR", "VN", "TW"));
    private final Set<String> languageKeys = new HashSet<>(Arrays.asList("ar-rSA", "es-rAR", "in-rID", "ja-rJP", "ms-rMY", "pt-rBR", "ru-rRU", "th-rTH", "tr-rTR", "vi-rVN", "zh-rTW"));
    private static final String TAGS = "android";
    private String destFileName;
    private String oldDestFileName; // 旧文件名（格式为：string_xxx.xml，新版本修复为：strings_xxx.xml）
    private String destPath;
    private Project project;
    private String projectPath;

    @Override
    public void actionPerformed(AnActionEvent e) {
        project = e.getProject();
        if (project != null) {
            projectPath = project.getBasePath();
            chooseDestPath();
        }
    }

    /**
     * 选择目标 module
     */
    private void chooseDestPath() {
        File rootFile = new File(projectPath);
        List<File> selectFileList = new ArrayList<>();
        File[] rootFiles = rootFile.listFiles(new DirectoryFilter());
        if (rootFiles != null && rootFiles.length > 0) {
            for (File item : rootFiles) {
                filterModulePath(item, selectFileList);
            }
        }

        if (selectFileList.size() == 0) {
            NotifyUtil.notifyError("未找到目标工程", project);
        } else if (selectFileList.size() == 1) {
            destPath = selectFileList.get(0).getPath() + "/src/main/res";
            setDestFileName();
        } else {
            List<String> moduleNames = new ArrayList<>();
            for (File item : selectFileList) {
                moduleNames.add(item.getName());
            }
            new ModuleListDialog(moduleNames, position -> {
                destPath = selectFileList.get(position).getPath() + "/src/main/res";
                setDestFileName();
            }).setVisible(true);
        }
    }

    /**
     * 设置目标文件名称
     */
    private void setDestFileName() {
        new SetNameDialog(project, name -> {
            destFileName = "strings_" + name + ".xml";
            oldDestFileName = "string_" + name + ".xml";
            fileChoose();
        }).setVisible(true);
    }

    /**
     * 选择 xls xlsx 文件
     */
    private void fileChoose() {
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
            parseXls(file);
        }
    }

    /**
     * 解析 xls xlsx 文件
     * @param excel
     */
    public void parseXls(File excel) {
        try {
            String name = excel.getName();
            Workbook wb;
            if (name.endsWith(".xls")) {
                FileInputStream fileInputStream = new FileInputStream(excel);
                wb = new HSSFWorkbook(fileInputStream);
            } else if (name.endsWith(".xlsx")) {
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
                new SheetListDialog(sheetNames, position -> parseSheet(wb.getSheetAt(position))).setVisible(true);
            } else {
                parseSheet(wb.getSheetAt(0));
            }

            wb.close();
        } catch (Exception e) {
            NotifyUtil.notifyError("解析异常：" + e.getMessage(), project);
        }
    }

    /**
     * 解析 sheet
     * @param sheet
     */
    private void parseSheet(Sheet sheet) {
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
                    String cellValue = "";
                    if (cell.getCellType() == CellType.STRING) {
                        cellValue = cell.getStringCellValue();
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        cellValue = cell.getNumericCellValue() + "";
                    }
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
            validData(result);
        } else {
            NotifyUtil.notifyError("解析数据为空", project);
        }
    }

    /**
     * 根据解析出的sheet结果，过滤出有效数据
     * @param data
     */
    private void validData(List<List<String>> data) {
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
            if (TAGS.equalsIgnoreCase(top)|| keys.contains(top.toUpperCase())) { // 如果是 Tags，或者包含在预定义语言类型中，就是有效数据，需要保存
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
                    if (key.equalsIgnoreCase(TAGS)) {
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

        checkFormat(dataMap, tags);
    }

    /**
     * 检查 %s %d 每个翻译个数是否一致
     * @param dataMap
     * @param tags
     */
    private void checkFormat(Map<String, List<String>> dataMap, List<String> tags) {
        int size = dataMap.get("EN").size();
        // 检查 %s 个数是否一致
        doCheck(size, "%s", dataMap, new CheckDialog.Callback() {
            @Override
            public void onContinue() {
                doCheck(size, "%d", dataMap, new CheckDialog.Callback() {
                    @Override
                    public void onContinue() {
                        importToDestPath(dataMap, tags);
                    }

                    @Override
                    public void onCancel() {
                        NotifyUtil.notify("已取消", project);
                    }
                });
            }

            @Override
            public void onCancel() {
                NotifyUtil.notify("已取消", project);
            }
        });
    }

    /**
     * 导入到xml文件里
     * @param dataMap
     * @param tags
     */
    private void importToDestPath(Map<String, List<String>> dataMap, List<String> tags) {
        File file = new File(destPath);
        // 初始化 res 文件夹
        if (!file.exists()) {
            boolean ignore = file.mkdir();
        }
        if (file.exists()) {
            // 初始化多语言 values 文件夹
            for (String languageKey : languageKeys) {
                File languageFile = new File(destPath + "/values-" + languageKey);
                if (!languageFile.exists()) {
                    boolean ignore = languageFile.mkdir();
                }
            }
            // 初始化 values 文件夹
            File enLanguageFile = new File(destPath + "/values");
            if (!enLanguageFile.exists()) {
                boolean ignore = enLanguageFile.mkdir();
            }

            File[] files = file.listFiles();
            if (files != null) {
                for (File item : files) {
                    String pathName = item.getName();
                    String pathNameSuffix = pathName.substring(pathName.length() - 2);
                    if (item.getName().equals("values") || keys.contains(pathNameSuffix)) {
                        logger.debug("目录：" + item.getAbsolutePath());

                        try {
                            SAXBuilder builder = new SAXBuilder();
                            File fileNew;
                            Document doc;
                            Element root;

                            File[] oldStringFiles = item.listFiles(new OldDestNameFilter());
                            if (oldStringFiles != null && oldStringFiles.length > 0) {
                                File oldStringFile = oldStringFiles[0];
                                boolean ignore = oldStringFile.renameTo(new File(item.getAbsolutePath() + "/" + destFileName));
                            }

                            File[] stringFiles = item.listFiles(new DestFileNameFilter());
                            if (stringFiles == null || stringFiles.length == 0) {
                                fileNew = new File(item.getAbsolutePath() + "/" + destFileName);
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

    /**
     * 具体 %x 检查逻辑
     * @param size
     * @param formatStr
     * @param dataMap
     * @return
     */
    private void doCheck(int size, String formatStr, Map<String, List<String>> dataMap, CheckDialog.Callback callback) {
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
                    new CheckDialog(title, callback).setVisible(true);
                    return;
                }
            }
        }
        callback.onContinue();
    }

    private void filterModulePath(File sourceDirectory, List<File> destFiles) {
        if (sourceDirectory == null || !sourceDirectory.isDirectory()) return;
        File[] settingFiles = sourceDirectory.listFiles(new SettingGradleNameFilter());
        if (settingFiles != null && settingFiles.length > 0) {
            File[] children = sourceDirectory.listFiles(new DirectoryFilter());
            if (children != null && children.length > 0) {
                for (File item : children) {
                    filterModulePath(item, destFiles);
                }
            }
        } else {
            File[] buildFiles = sourceDirectory.listFiles(new BuildGradleNameFilter());
            File[] srcFiles = sourceDirectory.listFiles(new SrcNameFilter());
            if (buildFiles != null && buildFiles.length == 1 && srcFiles != null && srcFiles.length == 1) {
                destFiles.add(sourceDirectory);
            }
        }
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

    private class DestFileNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals(destFileName);
        }
    }

    private class OldDestNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals(oldDestFileName);
        }
    }

    private static class DirectoryFilter implements FileFilter {
        @Override
        public boolean accept(File file) {
            return file.isDirectory();
        }
    }

    private static class SettingGradleNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals("settings.gradle");
        }
    }

    private static class BuildGradleNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals("build.gradle");
        }
    }

    private static class SrcNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals("src");
        }
    }
}
