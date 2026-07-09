package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/testcases")
public class ExcelController {
    private static final Logger log = LoggerFactory.getLogger(ExcelController.class);

    private final TestCaseRepository testCaseRepository;

    public ExcelController(TestCaseRepository testCaseRepository) {
        this.testCaseRepository = testCaseRepository;
    }

    /**
     * 导出所有测试用例为 Excel
     */
    @GetMapping("/export/excel")
    public ResponseEntity<Resource> exportExcel() {
        try {
            List<TestCaseEntity> cases = testCaseRepository.findAllTestCases();
            
            // Create workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("测试用例");
            
            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID", "名称", "输入", "期望输出", "分组", "创建时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            
            // Create data rows
            int rowNum = 1;
            for (TestCaseEntity tc : cases) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(sanitizeExcelCell(tc.getId()));
                row.createCell(1).setCellValue(sanitizeExcelCell(tc.getName()));
                row.createCell(2).setCellValue(sanitizeExcelCell(tc.getInput()));
                row.createCell(3).setCellValue(sanitizeExcelCell(tc.getExpected()));
                row.createCell(4).setCellValue(sanitizeExcelCell(tc.getGroupId()));
                row.createCell(5).setCellValue(sanitizeExcelCell(tc.getCreatedAt() != null ? tc.getCreatedAt().toString() : ""));
            }
            
            // Auto size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            
            // Write to bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();
            
            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"testcases_" + System.currentTimeMillis() + ".xlsx\"")
                .body(resource);
                
        } catch (Exception e) {
            log.error("Failed to export Excel: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导入测试用例从 Excel
     */
    @PostMapping("/import/excel")
    public ResponseEntity<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        
        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "文件不能为空");
            return ResponseEntity.badRequest().body(result);
        }
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int imported = 0;
            int skipped = 0;
            List<String> errors = new ArrayList<>();
            
            // Skip header row
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    String name = getCellValue(row.getCell(1));
                    String input = getCellValue(row.getCell(2));
                    String expected = getCellValue(row.getCell(3));
                    String group = getCellValue(row.getCell(4));
                    
                    if (name == null || name.trim().isEmpty()) {
                        skipped++;
                        continue;
                    }
                    
                    TestCaseEntity tc = new TestCaseEntity();
                    tc.setName(name);
                    tc.setInput(input != null ? input : "");
                    tc.setExpected(expected != null ? expected : "");
                    tc.setGroupId(group != null && !group.isEmpty() ? group : null);
                    
                    testCaseRepository.saveTestCase(tc);
                    imported++;
                    
                } catch (Exception e) {
                    log.warn("Row {} import skipped: {}", i + 1, e.getMessage());
                    errors.add("行 " + (i + 1) + ": " + e.getMessage());
                }
            }
            
            result.put("success", true);
            result.put("imported", imported);
            result.put("skipped", skipped);
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to import Excel: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "导入失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 防止 Excel 公式注入：对以 = + - @ 开头的单元格值前置单引号，
     * 使 Excel 将其视为文本而非公式。POI 的 String 单元格本身按类型存储为文本、
     * 不具备公式执行风险，此处为纵深防御，与 CSV 导出策略保持一致。
     */
    private String sanitizeExcelCell(String value) {
        if (value == null) return "";
        if (value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")) {
            return "'" + value;
        }
        return value;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                // 优先取公式缓存值；无法读取时退回公式文本并做注入防护（前置单引号），
                // 避免把可执行公式表达式当作普通数据写入用例。
                try {
                    yield switch (cell.getCachedFormulaResultType()) {
                        case STRING -> sanitizeExcelCell(cell.getStringCellValue());
                        case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
                        case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                        default -> sanitizeExcelCell(cell.getCellFormula());
                    };
                } catch (Exception e) {
                    yield sanitizeExcelCell(cell.getCellFormula());
                }
            }
            default -> null;
        };
    }
}
