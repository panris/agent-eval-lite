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
                row.createCell(0).setCellValue(tc.getId() != null ? tc.getId() : "");
                row.createCell(1).setCellValue(tc.getName() != null ? tc.getName() : "");
                row.createCell(2).setCellValue(tc.getInput() != null ? tc.getInput() : "");
                row.createCell(3).setCellValue(tc.getExpected() != null ? tc.getExpected() : "");
                row.createCell(4).setCellValue(tc.getGroupId() != null ? tc.getGroupId() : "");
                row.createCell(5).setCellValue(tc.getCreatedAt() != null ? tc.getCreatedAt().toString() : "");
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
    
    private String getCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }
}
