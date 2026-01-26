package com.zhangxq.stringhandler;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.zhangxq.NotifyUtil;
import kotlin.Pair;
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
    private final List<String> keys = List.of("SA", "AR", "ID", "JP", "MY", "BR", "RU", "TH", "TR", "VN", "TW", "IE", "IN", "MM", "JP");
    private final List<String> extraKeys = List.of("EN", "EG", "AE", "ES", "MX", "CO", "PT");
    private final Map<String, List<String>> regionMap = Map.of("SA", List.of("EG", "AE"), "AR", List.of("ES", "MX", "CO"), "BR", List.of("PT")); // 相同地区映射表
    private final List<String> languagePaths = List.of("values-ar-rSA", "values-es-rAR", "values-in-rID", "values-ja-rJP", "values-ms-rMY", "values-pt-rBR", "values-ru-rRU", "values-th-rTH", "values-tr-rTR", "values-vi-rVN", "values-zh-rTW", "values-en-rIN", "values-hi-rIN", "values-my-rMM", "values-ja-rJP");
    private static final String ANDROID = "android";
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
        if (rootFiles != null) {
            for (File item : rootFiles) {
                filterModulePath(item, selectFileList);
            }
        }

        if (selectFileList.isEmpty()) {
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
        Pair<Integer, Row> titleRow = findTitleRow(sheet);
        if (titleRow == null) {
            NotifyUtil.notifyError("未找到标题行！！", project);
            return;
        }

        // 优先处理标题行，放在放在数组第一行
        result.add(new ArrayList<>());
        for (int j = 0; j < titleRow.getSecond().getPhysicalNumberOfCells(); j++) {
            Cell cell = titleRow.getSecond().getCell(j);
            if (cell != null) {
                String cellValue = "";
                if (cell.getCellType() == CellType.STRING) {
                    cellValue = cell.getStringCellValue();
                } else if (cell.getCellType() == CellType.NUMERIC) {
                    cellValue = cell.getNumericCellValue() + "";
                }
                if (cellValue == null || cellValue.isEmpty()) cellValue = "";
                cellValue = cellValue.trim();
                result.get(0).add(cellValue);
            } else {
                result.get(0).add("");
            }
        }

        // 处理其他行
        for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row row = sheet.getRow(i);
            if (i == titleRow.getFirst()) continue;
            if (row == null) break;
            result.add(new ArrayList<>());
            for (int j = 0; j < titleRow.getSecond().getPhysicalNumberOfCells(); j++) {
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

        if (!result.isEmpty()) {
            NotifyUtil.notify("excel解析完成", project);
            validData(result);
        } else {
            NotifyUtil.notifyError("解析数据为空", project);
        }
    }

    /**
     * 找到标题行包含，一般包含 Tags、Android、EN、MY、PH.......
     * @param sheet
     * @return
     */
    private Pair<Integer, Row> findTitleRow(Sheet sheet) {
        for (int i = 0; i < sheet.getPhysicalNumberOfRows(); i++) {
            Row itemRow = sheet.getRow(i);
            boolean hasAndroid = false;
            boolean hasEn = false;
            for (int j = 0; j < itemRow.getPhysicalNumberOfCells(); j++) {
                Cell cell = itemRow.getCell(j);
                if (cell.getCellType() == CellType.STRING) {
                    String cellValue = cell.getStringCellValue();
                    if ("android".equalsIgnoreCase(cellValue)) {
                        hasAndroid = true;
                    }
                    if ("en".equalsIgnoreCase(cellValue)) {
                        hasEn = true;
                    }
                }
            }
            if (hasAndroid && hasEn) return new Pair<>(i, itemRow);
        }
        return null;
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

        boolean exitAndroid = false;
        boolean exitLanguage = false;
        for (int i = 0; i < tops.size(); i++) {
            String top = tops.get(i);
            // 如果是 android，或者包含在预定义语言类型中，就是有效数据，需要保存
            if (ANDROID.equalsIgnoreCase(top) || keys.contains(top.toUpperCase()) || extraKeys.contains(top.toUpperCase())) {
                if (columnMap.containsValue(top)) {
                    NotifyUtil.notifyError(top + "列重复了，请检查文档", project);
                    return;
                } else {
                    dataMap.put(top, new ArrayList<>());
                    columnMap.put(i, top);
                }

                if (ANDROID.equalsIgnoreCase(top)) {
                    exitAndroid = true;
                }

                if (keys.contains(top.toUpperCase()) || extraKeys.contains(top.toUpperCase())) {
                    exitLanguage = true;
                }
            }
        }
        if (!exitAndroid) {
            NotifyUtil.notifyError("未找到 android 列，请检查文档", project);
            return;
        }
        if (!exitLanguage) {
            NotifyUtil.notifyError("未找到有效语言列，请检查文档", project);
            return;
        }

        for (int i = 1; i < data.size(); i++) {
            List<String> datum = data.get(i);
            for (int j = 0; j < datum.size(); j++) {
                if (columnMap.containsKey(j)) {
                    String cell = datum.get(j);
                    String key = columnMap.get(j);
                    if (key.equalsIgnoreCase(ANDROID)) {
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
        if (enList == null || enList.isEmpty()) {
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
            // 对多地区语言进行补全（比如SA如果是空的，那就用EG或者AE进行补全，三者是同一个地区的）
            for (String regionKey: regionMap.keySet()) {
                List<String> list = dataMap.get(regionKey);
                if (list == null || list.isEmpty() || isAllEmpty(list)) {
                    List<String> regionList = regionMap.get(regionKey);
                    for (String region: regionList) {
                        List<String> regionDataList = dataMap.get(region);
                        if (regionDataList != null && !regionDataList.isEmpty() && !isAllEmpty(regionDataList)) {
                            dataMap.put(regionKey, regionDataList);
                            break;
                        }
                    }
                }
            }

            // 初始化多语言 values 文件夹
            for (String key : dataMap.keySet()) {
                if (keys.contains(key)) {
                    int index = keys.indexOf(key);
                    if (index > 0) {
                        String languagePath = languagePaths.get(index);
                        File languageFile = new File(destPath + "/" + languagePath);
                        if (!languageFile.exists()) {
                            boolean ignore = languageFile.mkdir();
                        }
                    }
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
                    String mapKey = "null";
                    if (languagePaths.contains(pathName)) {
                        mapKey = keys.get(languagePaths.indexOf(pathName));
                    }
                    if (item.getName().equals("values") || dataMap.containsKey(mapKey)) {
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

                            List<String> newStrings = dataMap.get(mapKey);
                            if (item.getName().equals("values")) newStrings = dataMap.get("EN");
                            if (newStrings != null && newStrings.size() == tags.size()) {
                                for (int i = 0; i < newStrings.size(); i++) {
                                    String name = tags.get(i);
                                    if (name.startsWith("*")) {
                                        removeElementByName(name.replace("*", ""), root);
                                    } else {
                                        String content = newStrings.get(i);
                                        if (content == null || content.isEmpty()) continue;
                                        content = content.replace("\"", "\\\"");
                                        content = content.replace("'", "\\'");
                                        content = replacePercent(content);
                                        Element element = getElementByName(name, root);
                                        element.addContent(content);
                                    }
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

    private boolean isAllEmpty(List<String> list) {
        for (String item: list) {
            if (item != null && !item.isEmpty()) return false;
        }
        return true;
    }

    /**
     * 根据name属性获取element，存在则清空内容返回，没有则创建一个新的返回
     * @param name
     * @param root
     * @return
     */
    private Element getElementByName(String name, Element root) {
        for(Element element : root.getChildren()) {
            String eleName = element.getAttributeValue("name");
            if (eleName.equals(name)) {
                element.removeContent();
                return element;
            }
        }
        Element stringItem = new Element("string");
        stringItem.setAttribute("name", name);
        root.addContent(stringItem);
        return stringItem;
    }

    private void removeElementByName(String name, Element root) {
        Element targetElement = null;
        for(Element element : root.getChildren()) {
            if (name.equals(element.getAttributeValue("name"))) {
                targetElement = element;
                break;
            }
        }
        if (targetElement != null) {
            root.removeContent(targetElement);
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
                if (dataMap.get(key) == null || dataMap.get(key).size() <= i || dataMap.get(key).get(i).isEmpty()) continue;
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
            if (children != null) {
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

    private String replacePercent(String input) {
        if (!input.contains("%s") && !input.contains("%d")) return input;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (currentChar == '%') {
                if (i + 1 >= input.length() || (input.charAt(i + 1) != 's' && input.charAt(i + 1) != 'd')) {
                    result.append("%%");
                } else {
                    result.append(currentChar);
                }
            } else {
                result.append(currentChar);
            }
        }
        return result.toString();
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
            return name.equals("build.gradle") || name.equals("build.gradle.kts");
        }
    }

    private static class SrcNameFilter implements FilenameFilter {
        @Override
        public boolean accept(File dir, String name) {
            return name.equals("src");
        }
    }
}
