package io.github.panris.agenteval.web.controller;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.github.panris.agenteval.service.ReportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
public class PdfController {
    private static final Logger log = LoggerFactory.getLogger(PdfController.class);

    private final TestCaseRepository testCaseRepository;
    private final ReportService reportService;

    public PdfController(TestCaseRepository testCaseRepository,
                         ReportService reportService) {
        this.testCaseRepository = testCaseRepository;
        this.reportService = reportService;
    }

    /**
     * 生成中文支持的 PDF
     */
    @Operation(summary = "导出评测报告为 PDF（含中文支持）")
    @GetMapping("/reports/{id}/export/pdf")
    public ResponseEntity<Resource> exportReportPdf(@PathVariable String id) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // 中文字体查找：跨平台支持
            BaseFont bfChinese = findChineseFont();
            
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();
            
            // 字体样式
            Font titleFont = new Font(bfChinese, 18, Font.BOLD);
            Font headerFont = new Font(bfChinese, 12, Font.BOLD);
            Font normalFont = new Font(bfChinese, 10, Font.NORMAL);
            Font smallFont = new Font(bfChinese, 8, Font.NORMAL);
            
            // 标题
            Paragraph title = new Paragraph("Agent Eval Lite - 评测报告", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);
            
            // 报告基本信息
            document.add(new Paragraph("报告 ID: " + id, normalFont));
            document.add(new Paragraph("生成时间: " + new Date(), normalFont));
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("─".repeat(50), smallFont));
            document.add(new Paragraph(" ", normalFont));
            
            // 获取真实报告数据
            Map<String, Object> report = getReportData(id);
            
            if (report != null) {
                // 统计信息
                int totalCases = (Integer) report.getOrDefault("totalCases", 0);
                int passed = (Integer) report.getOrDefault("passed", 0);
                double passRate = ((Number) report.getOrDefault("passRate", 0)).doubleValue();
                double avgScore = ((Number) report.getOrDefault("avgScore", 0)).doubleValue();
                
                // 统计信息表格
                PdfPTable infoTable = new PdfPTable(4);
                infoTable.setWidthPercentage(100);
                infoTable.setSpacingBefore(10f);
                
                addTableCell(infoTable, "测试用例", String.valueOf(totalCases), headerFont, normalFont);
                addTableCell(infoTable, "通过", String.valueOf(passed), headerFont, normalFont);
                addTableCell(infoTable, "通过率", String.format("%.1f%%", passRate), headerFont, normalFont);
                addTableCell(infoTable, "平均评分", String.format("%.2f", avgScore), headerFont, normalFont);
                document.add(infoTable);
                
                // 执行时间
                Object execTime = report.get("executionTimeMs");
                if (execTime instanceof Number) {
                    document.add(new Paragraph("执行耗时: " + execTime + " ms", normalFont));
                }
                
                // 时间戳
                Object ts = report.get("timestamp");
                if (ts instanceof Date) {
                    document.add(new Paragraph("评测时间: " + ts, normalFont));
                }
                
                // 分组维度信息
                StringBuilder gm = new StringBuilder();
                Object grp = report.get("group");
                if (grp != null && !String.valueOf(grp).trim().isEmpty()) gm.append("分组=").append(grp).append("  ");
                Object proj = report.get("project");
                if (proj != null && !String.valueOf(proj).trim().isEmpty()) gm.append("项目=").append(proj).append("  ");
                Object mod = report.get("module");
                if (mod != null && !String.valueOf(mod).trim().isEmpty()) gm.append("模块=").append(mod).append("  ");
                Object fn = report.get("function");
                if (fn != null && !String.valueOf(fn).trim().isEmpty()) gm.append("功能=").append(fn).append("  ");
                if (gm.length() > 0) {
                    document.add(new Paragraph("分组维度: " + gm.toString().trim(), normalFont));
                }
                
                document.add(new Paragraph(" ", normalFont));
                
                // 评测结果详情
                document.add(new Paragraph("评测结果详情", headerFont));
                document.add(new Paragraph(" ", normalFont));
                
                @SuppressWarnings("unchecked")
                java.util.List<Map<String, Object>> results = 
                    (java.util.List<Map<String, Object>>) report.getOrDefault("results", java.util.List.of());
                
                if (!results.isEmpty()) {
                    PdfPTable resultsTable = new PdfPTable(4);
                    resultsTable.setWidthPercentage(100);
                    resultsTable.setSpacingBefore(5f);
                    resultsTable.setWidths(new float[]{2.5f, 1f, 1f, 3f});
                    
                    // 表头
                    Font tableHeaderFont = new Font(bfChinese, 10, Font.BOLD, Color.WHITE);
                    PdfPCell h1 = headerCell("用例名称", tableHeaderFont, new Color(102, 126, 234));
                    PdfPCell h2 = headerCell("状态", tableHeaderFont, new Color(102, 126, 234));
                    PdfPCell h3 = headerCell("评分", tableHeaderFont, new Color(102, 126, 234));
                    PdfPCell h4 = headerCell("逐评分器", tableHeaderFont, new Color(102, 126, 234));
                    resultsTable.addCell(h1);
                    resultsTable.addCell(h2);
                    resultsTable.addCell(h3);
                    resultsTable.addCell(h4);
                    
                    // 数据行
                    for (Map<String, Object> result : results) {
                        boolean isPassed = Boolean.TRUE.equals(result.get("passed"));
                        Font rowFont = new Font(bfChinese, 9, Font.NORMAL);
                        
                        resultsTable.addCell(new Phrase((String) result.getOrDefault("name", ""), rowFont));
                        
                        String statusText = isPassed ? "✓ 通过" : "✗ 失败";
                        PdfPCell statusCell = new PdfPCell(new Phrase(statusText, rowFont));
                        statusCell.setBackgroundColor(isPassed ? new Color(200, 255, 200) : new Color(255, 200, 200));
                        resultsTable.addCell(statusCell);
                        
                        Object scoreObj = result.get("score");
                        double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0;
                        resultsTable.addCell(new Phrase(String.format("%.2f", score), rowFont));
                        
                        // 逐评分器明细
                        StringBuilder sb = new StringBuilder();
                        Object srObj = result.get("scorerResults");
                        if (srObj instanceof Map) {
                            for (Map.Entry<String, Object> se : ((Map<String, Object>) srObj).entrySet()) {
                                if (se.getValue() instanceof Map) {
                                    Map<String, Object> sr = (Map<String, Object>) se.getValue();
                                    double s = sr.get("score") instanceof Number
                                        ? ((Number) sr.get("score")).doubleValue() : 0;
                                    boolean sp = Boolean.TRUE.equals(sr.get("passed"));
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append(se.getKey()).append(":").append(String.format("%.2f", s))
                                        .append(sp ? " ✓" : " ✗");
                                }
                            }
                        }
                        resultsTable.addCell(new Phrase(sb.length() > 0 ? sb.toString() : "-", smallFont));
                    }
                    
                    document.add(resultsTable);
                    
                    // Agent 输出摘录
                    document.add(new Paragraph(" ", normalFont));
                    document.add(new Paragraph("Agent 输出摘录", headerFont));
                    document.add(new Paragraph(" ", normalFont));
                    
                    for (int i = 0; i < results.size(); i++) {
                        Map<String, Object> r = results.get(i);
                        String name = (String) r.getOrDefault("name", "");
                        String output = (String) r.getOrDefault("output", "");
                        boolean passed2 = Boolean.TRUE.equals(r.get("passed"));
                        String label = passed2 ? "✓ " : "✗ ";
                        
                        Paragraph p = new Paragraph(label + name, new Font(bfChinese, 9, Font.BOLD));
                        p.setSpacingBefore(5);
                        document.add(p);
                        
                        Paragraph outP = new Paragraph("  " + output, new Font(bfChinese, 8, Font.NORMAL));
                        outP.setSpacingAfter(3);
                        document.add(outP);
                    }
                }
            } else {
                document.add(new Paragraph("未找到报告数据", normalFont));
            }
            
            // 页脚
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("─".repeat(50), smallFont));
            Paragraph footer = new Paragraph("由 Agent Eval Lite 生成 | " + new Date(), smallFont);
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
            log.error("Failed to generate PDF for report {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private PdfPCell headerCell(String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(8);
        return cell;
    }
    
    private void addTableCell(PdfPTable table, String label, String value, Font headerFont, Font normalFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, headerFont));
        labelCell.setBorderWidth(0);
        labelCell.setPadding(8);
        labelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(labelCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, normalFont));
        valueCell.setBorderWidth(0);
        valueCell.setPadding(8);
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(valueCell);
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getReportData(String reportId) {
        Map<String, Object> rawReport = reportService.getReport(reportId);
        if (rawReport == null) return null;
        
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", reportId);
        
        // 摘要
        Map<String, Object> summary = (Map<String, Object>) rawReport.getOrDefault("summary", Map.of());
        double passRate = ((Number) summary.getOrDefault("pass_rate", 0)).doubleValue();
        double avgScore = ((Number) summary.getOrDefault("average_score", 0)).doubleValue();
        int totalCases = ((Number) summary.getOrDefault("total_test_cases", 0)).intValue();
        int passedCases = ((Number) summary.getOrDefault("passed_test_cases", 0)).intValue();
        
        report.put("totalCases", totalCases);
        report.put("passed", passedCases);
        report.put("passRate", passRate);
        report.put("avgScore", avgScore);
        
        // 时间戳
        Object timestamp = rawReport.get("timestamp");
        if (timestamp instanceof Number) {
            report.put("timestamp", new Date(((Number) timestamp).longValue()));
        }
        
        // 执行时间
        Object execTime = rawReport.get("executionTimeMs");
        if (execTime instanceof Number) {
            report.put("executionTimeMs", ((Number) execTime).longValue());
        }
        
        // 评测结果（带用例名称）
        java.util.List<Map<String, Object>> evaluations = 
            (java.util.List<Map<String, Object>>) rawReport.getOrDefault("evaluations", java.util.List.of());
        
        java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
        int idx = 1;
        for (Map<String, Object> ev : evaluations) {
            Map<String, Object> item = new LinkedHashMap<>();
            String tcId = String.valueOf(ev.get("testCaseId"));

            // 优先级: testCaseName（实体名称） > testCaseInput（输入文本） > 仓库查找 > fallback
            Object rawName = ev.get("testCaseName");
            String caseName;
            if (rawName != null && !rawName.toString().isBlank()) {
                caseName = rawName.toString();
            } else {
                Object rawInput = ev.get("testCaseInput");
                if (rawInput != null && !rawInput.toString().isEmpty()) {
                    String inp = rawInput.toString();
                    caseName = inp.length() > 40 ? inp.substring(0, 40) + "..." : inp;
                } else {
                    caseName = testCaseRepository.findTestCaseById(tcId)
                        .map(tc -> {
                            String in = tc.getInput();
                            return (in != null && in.length() > 40) ? in.substring(0, 40) + "..." : in;
                        })
                        .orElse("用例 #" + idx);
                }
            }
            
            item.put("name", caseName);
            item.put("testCaseId", tcId);
            item.put("passed", ev.get("passed"));
            
            Object score = ev.get("overallScore");
            item.put("score", score instanceof Number ? score : 0);
            
            // 优先读取顶层 output（新序列化格式），兼容旧的嵌套 agentOutput 结构
            String output = "";
            Object outObj = ev.get("output");
            if (outObj != null) {
                output = String.valueOf(outObj);
            } else {
                Object agentOutputObj = ev.get("agentOutput");
                if (agentOutputObj instanceof Map) {
                    Object rawOut = ((Map<?, ?>) agentOutputObj).get("output");
                    output = rawOut != null ? String.valueOf(rawOut) : "";
                }
            }
            item.put("output", output.length() > 200 ? output.substring(0, 200) + "..." : output);
            item.put("scorerResults", ev.get("scorerResults"));
            
            results.add(item);
            idx++;
        }
        report.put("results", results);
        
        return report;
    }

    /**
     * 跨平台中文字体查找：macOS → Linux(Noto/WQY) → Docker → 回退 Helvetica
     */
    private BaseFont findChineseFont() {
        String[] fontPaths = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/System/Library/Fonts/AppleSDGothicNeo.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansSC-Regular.otf",
            "/usr/share/fonts/opentype/noto/NotoSansSC-Regular.otf",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
            "/usr/share/fonts/wqy-microhei/wqy-microhei.ttc"
        };
        for (String path : fontPaths) {
            if (Files.exists(Paths.get(path))) {
                try {
                    return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                } catch (Exception ignored) {}
            }
        }
        // 全部失败，回退 Helvetica（仅英文）
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("无法创建基础字体", e);
        }
    }
}
