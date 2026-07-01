package io.github.panris.agenteval.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@Controller
public class ShareController {

    private final Map<String, Map<String, Object>> reportHistory;
    private final Map<String, String> sharedReports;

    public ShareController(Map<String, Map<String, Object>> reportHistory,
                           Map<String, String> sharedReports) {
        this.reportHistory = reportHistory;
        this.sharedReports = sharedReports;
    }

    @GetMapping("/share/{shareId}")
    public String showSharedReport(@PathVariable String shareId, Model model) {
        String reportId = sharedReports.get(shareId);
        if (reportId == null) {
            return "redirect:/";
        }
        
        Map<String, Object> report = reportHistory.get(reportId);
        if (report == null) {
            return "redirect:/";
        }
        
        model.addAttribute("reportId", reportId);
        model.addAttribute("summary", report.get("summary"));
        model.addAttribute("timestamp", report.get("timestamp"));
        model.addAttribute("evaluations", report.get("evaluations"));
        model.addAttribute("totalTestCases", report.getOrDefault("totalTestCases", 0));
        model.addAttribute("passedTestCases", report.getOrDefault("passedTestCases", 0));
        model.addAttribute("failedTestCases", report.getOrDefault("failedTestCases", 0));
        
        return "share";
    }
}
