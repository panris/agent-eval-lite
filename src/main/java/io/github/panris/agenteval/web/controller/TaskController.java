package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.service.AsyncEvalService;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 异步评测任务 Controller：从 EvalController 拆分出来，
 * 负责异步任务的查询和列表。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final AsyncEvalService asyncEvalService;

    public TaskController(AsyncEvalService asyncEvalService) {
        this.asyncEvalService = asyncEvalService;
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询异步评测任务状态")
    public Map<String, Object> getTaskStatus(@PathVariable String taskId) {
        AsyncEvalService.TaskStatus status = asyncEvalService.getStatus(taskId);
        if (status == null) {
            return ApiResponse.error("任务不存在");
        }
        return Map.of(
                "success", true,
                "taskId", status.taskId,
                "status", status.status,
                "reportId", status.reportId != null ? status.reportId : "",
                "error", status.error != null ? status.error : "",
                "totalCases", status.totalCases,
                "completedCases", status.completedCases,
                "createdAt", status.createdAt,
                "completedAt", status.completedAt,
                "timeoutSeconds", status.timeoutSeconds
        );
    }

    @GetMapping
    @Operation(summary = "查询所有异步评测任务（最近 50 条）")
    public List<Map<String, Object>> listTasks() {
        return asyncEvalService.getAllStatuses().stream()
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .limit(50)
                .map(s -> Map.<String, Object>of(
                        "taskId", s.taskId,
                        "status", s.status,
                        "reportId", s.reportId != null ? s.reportId : "",
                        "totalCases", s.totalCases,
                        "completedCases", s.completedCases,
                        "createdAt", s.createdAt,
                        "completedAt", s.completedAt
                ))
                .toList();
    }
}
