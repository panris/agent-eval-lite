package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ShareController {

    private final ReportService reportService;

    public ShareController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/share/{shareId}")
    public String showSharedReport(@PathVariable String shareId, Model model) {
        String reportId = reportService.resolveShareId(shareId);
        if (reportId == null) {
            return "redirect:/";
        }
        
        Map<String, Object> report = reportService.getReport(reportId);
        if (report == null) {
            return "redirect:/";
        }
        
        // 将嵌套的 evaluation map 拍平，避免 Thymeleaf 访问嵌套 Map 时出错
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawEvals = (List<Map<String, Object>>) report.get("evaluations");
        List<Map<String, Object>> evaluations = rawEvals == null ? List.of() : rawEvals.stream().map(ev -> {
            Map<String, Object> flat = new java.util.LinkedHashMap<>(ev);
            @SuppressWarnings("unchecked")
            Map<String, Object> agentOutput = (Map<String, Object>) ev.get("agentOutput");
            flat.put("agentOutputText", agentOutput == null ? null : agentOutput.get("output"));
            return flat;
        }).collect(Collectors.toList());
        
        model.addAttribute("reportId", reportId);
        model.addAttribute("summary", report.get("summary"));
        model.addAttribute("timestamp", report.get("timestamp"));
        model.addAttribute("evaluations", evaluations);
        model.addAttribute("totalTestCases", report.getOrDefault("totalTestCases", 0));
        model.addAttribute("passedTestCases", report.getOrDefault("passedTestCases", 0));
        model.addAttribute("failedTestCases", report.getOrDefault("failedTestCases", 0));
        
        return "share";
    }
}
