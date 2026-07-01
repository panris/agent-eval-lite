package io.github.panris.agenteval.web.controller;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PdfController {

    /**
     * 生成中文支持的 PDF
     */
    @GetMapping("/reports/{id}/export/pdf")
    public ResponseEntity<Resource> exportReportPdf(@PathVariable String id) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 使用 Arial Unicode MS 字体（系统自带）
            BaseFont bfChinese;
            try {
                // 尝试使用系统自带的中文字体
                bfChinese = BaseFont.createFont("/System/Library/Fonts/Supplemental/Arial Unicode.ttf", 
                    BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                // 回退到 Helvetica（不支持中文但能显示英文）
                bfChinese = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            }
            
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();
            
            // 标题样式
            Font titleFont = new Font(bfChinese, 18, Font.BOLD);
            Font headerFont = new Font(bfChinese, 12, Font.BOLD);
            Font normalFont = new Font(bfChinese, 10, Font.NORMAL);
            Font smallFont = new Font(bfChinese, 8, Font.NORMAL);
            
            // 标题
            Paragraph title = new Paragraph("Agent Eval Lite - 评测报告", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);
            
            // 报告信息
            document.add(new Paragraph("报告 ID: " + id, normalFont));
            document.add(new Paragraph("生成时间: " + new java.util.Date(), normalFont));
            document.add(new Paragraph(" ", normalFont));
            
            // 分隔线
            document.add(new Paragraph("━".repeat(50), normalFont));
            document.add(new Paragraph(" ", normalFont));
            
            // 获取报告数据
            Map<String, Object> report = getReportData(id);
            
            if (report != null) {
                // 基本信息表格
                PdfPTable infoTable = new PdfPTable(2);
                infoTable.setWidthPercentage(100);
                
                Object totalCases = report.getOrDefault("totalCases", 0);
                Object passed = report.getOrDefault("passed", 0);
                Object passRate = report.getOrDefault("passRate", 0);
                Object avgScore = report.getOrDefault("avgScore", 0);
                
                addTableRow(infoTable, "测试用例数", String.valueOf(totalCases), headerFont, normalFont);
                addTableRow(infoTable, "通过数", String.valueOf(passed), headerFont, normalFont);
                addTableRow(infoTable, "通过率", String.format("%.1f%%", ((Number) passRate).doubleValue()), headerFont, normalFont);
                addTableRow(infoTable, "平均评分", String.format("%.2f", ((Number) avgScore).doubleValue()), headerFont, normalFont);
                document.add(infoTable);
                document.add(new Paragraph(" ", normalFont));
                
                // 评测结果表格
                document.add(new Paragraph("评测结果详情", headerFont));
                document.add(new Paragraph(" ", normalFont));
                
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> results = (java.util.List<Map<String, Object>>) report.getOrDefault("results", new java.util.ArrayList<>());
                
                if (!results.isEmpty()) {
                    PdfPTable resultsTable = new PdfPTable(4);
                    resultsTable.setWidthPercentage(100);
                    
                    // 表头
                    Font tableHeaderFont = new Font(bfChinese, 10, Font.BOLD, Color.WHITE);
                    PdfPCell cell1 = new PdfPCell(new Phrase("用例名称", tableHeaderFont));
                    cell1.setBackgroundColor(new Color(102, 126, 234));
                    PdfPCell cell2 = new PdfPCell(new Phrase("状态", tableHeaderFont));
                    cell2.setBackgroundColor(new Color(102, 126, 234));
                    PdfPCell cell3 = new PdfPCell(new Phrase("评分", tableHeaderFont));
                    cell3.setBackgroundColor(new Color(102, 126, 234));
                    PdfPCell cell4 = new PdfPCell(new Phrase("耗时", tableHeaderFont));
                    cell4.setBackgroundColor(new Color(102, 126, 234));
                    
                    resultsTable.addCell(cell1);
                    resultsTable.addCell(cell2);
                    resultsTable.addCell(cell3);
                    resultsTable.addCell(cell4);
                    
                    // 数据行
                    for (Map<String, Object> result : results) {
                        boolean isPassed = Boolean.TRUE.equals(result.get("passed"));
                        Font resultFont = new Font(bfChinese, 9, Font.NORMAL);
                        
                        resultsTable.addCell(new Phrase((String) result.getOrDefault("name", ""), resultFont));
                        
                        String statusText = isPassed ? "✓ 通过" : "✗ 失败";
                        PdfPCell statusCell = new PdfPCell(new Phrase(statusText, resultFont));
                        statusCell.setBackgroundColor(isPassed ? new Color(200, 255, 200) : new Color(255, 200, 200));
                        resultsTable.addCell(statusCell);
                        
                        Object scoreObj = result.getOrDefault("score", 0);
                        double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0;
                        resultsTable.addCell(new Phrase(String.format("%.2f", score), resultFont));
                        
                        Object durationObj = result.getOrDefault("duration", 0);
                        double duration = durationObj instanceof Number ? ((Number) durationObj).doubleValue() : 0;
                        resultsTable.addCell(new Phrase(String.format("%.0fms", duration), resultFont));
                    }
                    
                    document.add(resultsTable);
                }
            } else {
                document.add(new Paragraph("未找到报告数据", normalFont));
            }
            
            // 页脚
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("━".repeat(50), smallFont));
            Paragraph footer = new Paragraph("由 Agent Eval Lite 生成 | " + new java.util.Date(), smallFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);
            
            document.close();
            
            ByteArrayResource resource = new ByteArrayResource(baos.toByteArray());
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"report_" + id + "_" + System.currentTimeMillis() + ".pdf\"")
                .body(resource);
                
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private void addTableRow(PdfPTable table, String label, String value, Font headerFont, Font normalFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, headerFont));
        labelCell.setBorderWidth(0);
        labelCell.setPadding(5);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, normalFont));
        valueCell.setBorderWidth(0);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }
    
    private Map<String, Object> getReportData(String reportId) {
        // 从 EvalController 获取报告数据
        // 这里简化处理，实际应该通过服务层获取
        Map<String, Object> report = new HashMap<>();
        report.put("id", reportId);
        report.put("totalCases", 4);
        report.put("passed", 3);
        report.put("passRate", 75.0);
        report.put("avgScore", 85.5);
        
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        results.add(Map.of("name", "测试用例A", "passed", true, "score", 95.0, "duration", 150L));
        results.add(Map.of("name", "测试用例B", "passed", true, "score", 88.0, "duration", 120L));
        results.add(Map.of("name", "测试用例C", "passed", true, "score", 82.0, "duration", 180L));
        results.add(Map.of("name", "测试用例D", "passed", false, "score", 65.0, "duration", 200L));
        report.put("results", results);
        
        return report;
    }
}
