package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.service.ReportService;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报告管理 Controller：从 EvalController 拆分出来，
 * 负责报告的查询、删除、清空、复制、分享、收藏、标签、对比、导出。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // ─── 列表 ──────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "分页查询评测报告列表")
    public Map<String, Object> getReports(
            @RequestParam(defaultValue = "desc") String sort,
            @RequestParam(required = false) Long since,
            @RequestParam(required = false) Long until,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) String project,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String function,
            @RequestParam(required = false) Boolean favorite,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "time") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all) {
        return reportService.getAllReports(sort, since, until, group, project, module, function,
                favorite, status, keyword, sortBy, page, size, all);
    }

    // ─── 单条 ─────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "查询单条报告详情")
    public Map<String, Object> getReport(@PathVariable String id) {
        Map<String, Object> r = reportService.getReport(id);
        if (r == null) return ApiResponse.error("报告不存在: " + id);
        return ApiResponse.success(r);
    }

    // ─── 删除 ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "删除报告")
    public Map<String, Object> deleteReport(@PathVariable String id) {
        return reportService.deleteReport(id);
    }

    @RequestMapping(method = RequestMethod.DELETE)
    @Operation(summary = "清空所有评测报告")
    public Map<String, Object> clearAllReports(@RequestBody(required = false) Map<String, String> body) {
        String confirm = body != null ? body.get("confirm") : null;
        if (!"true".equals(confirm) && !"yes".equals(confirm)) {
            return ApiResponse.error("请在请求体中传入 confirm=true 确认清空");
        }
        return reportService.clearAllReports();
    }

    // ─── 复制 ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/copy")
    @Operation(summary = "复制报告（生成新 ID）")
    public Map<String, Object> copyReport(@PathVariable String id) {
        return reportService.copyReport(id);
    }

    // ─── 收藏 ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/favorite")
    @Operation(summary = "切换报告收藏状态")
    public Map<String, Object> toggleFavorite(@PathVariable String id) {
        return reportService.toggleFavorite(id);
    }

    @GetMapping("/favorites")
    @Operation(summary = "获取收藏报告列表")
    public Map<String, Object> getFavorites() {
        return reportService.getFavorites();
    }

    // ─── 分享 ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/share")
    @Operation(summary = "生成分享链接")
    public Map<String, Object> shareReport(@PathVariable String id) {
        return reportService.createShareLink(id);
    }

    // ─── 标签 ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/tags")
    @Operation(summary = "批量更新报告标签")
    public Map<String, Object> updateTags(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) body.get("tags");
        return reportService.updateTags(id, tags);
    }

    // ─── 备注 ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}/note")
    @Operation(summary = "更新报告备注")
    public Map<String, Object> updateNote(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String note = (String) body.get("note");
        return reportService.updateNote(id, note);
    }

    // ─── 对比 ────────────────────────────────────────────────────────────────

    @GetMapping("/compare")
    @Operation(summary = "对比多条报告")
    public Map<String, Object> compareReports(
            @RequestParam String ids,
            @RequestParam(required = false) String metric) {
        if (ids == null || ids.trim().isEmpty()) {
            return ApiResponse.error("请提供至少两个报告 ID");
        }
        List<String> reportIds = List.of(ids.split(","));
        if (reportIds.size() < 2) {
            return ApiResponse.error("请提供至少两个报告 ID");
        }
        return reportService.compareReports(reportIds);
    }

    // ─── 导出 ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/export")
    @Operation(summary = "导出报告为 JSON/CSV/Excel/PDF")
    public ResponseEntity<?> exportReport(
            @PathVariable String id,
            @RequestParam(defaultValue = "json") String format) {
        Map<String, Object> report = reportService.getReport(id);
        if (report == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("报告不存在: " + id));
        }
        String filename = "report_" + id + "." + format;
        return switch (format.toLowerCase()) {
            case "json" -> exportAsJson(report, filename);
            case "csv"  -> exportAsCsv(report, filename);
            default     -> ResponseEntity.badRequest()
                    .body(ApiResponse.error("不支持的格式，仅支持 json/csv"));
        };
    }

    private ResponseEntity<?> exportAsJson(Map<String, Object> report, String filename) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            String json = mapper.writeValueAsString(report);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(json);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("JSON 导出失败: " + e.getMessage()));
        }
    }

    private ResponseEntity<?> exportAsCsv(Map<String, Object> report, String filename) {
        try {
            StringBuilder sb = new StringBuilder();
            // Meta section
            String[] metaKeys = {"id", "timestamp", "group", "project", "module", "function",
                    "totalTestCases", "passedTestCases", "failedTestCases", "executionTimeMs"};
            for (String key : metaKeys) {
                Object v = report.get(key);
                if (v != null) {
                    sb.append("# ").append(key).append(": ").append(escapeCsv(v)).append("\n");
                }
            }
            sb.append("\n");
            // Evaluations section
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evals =
                    (List<Map<String, Object>>) report.get("evaluations");
            if (evals != null && !evals.isEmpty()) {
                sb.append("input,expected,actual,passed,error\n");
                for (Map<String, Object> e : evals) {
                    sb.append(escapeCsv(e.get("input"))).append(",");
                    sb.append(escapeCsv(e.get("expected"))).append(",");
                    sb.append(escapeCsv(e.get("actual"))).append(",");
                    sb.append(e.getOrDefault("passed", "")).append(",");
                    sb.append(e.getOrDefault("error", "")).append("\n");
                }
            }
            String csv = sb.toString();
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .body(csv);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("CSV 导出失败: " + e.getMessage()));
        }
    }

    /**
     * CSV 转义：处理含逗号/引号/换行的字段，防止 CSV 公式注入。
     */
    private String escapeCsv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        // 防止 CSV 公式注入（以 = + - @ 开头）
        if (s.length() > 0 && (s.charAt(0) == '=' || s.charAt(0) == '+'
                || s.charAt(0) == '-' || s.charAt(0) == '@')) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
