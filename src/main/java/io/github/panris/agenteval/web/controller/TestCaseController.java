package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.web.Constants;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.web.dto.TestCaseRequest;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试用例 CRUD 操作。
 */
@RestController
@RequestMapping("/api/testcases")
public class TestCaseController {

    private static final Logger log = LoggerFactory.getLogger(TestCaseController.class);

    private final TestCaseRepository repository;

    public TestCaseController(TestCaseRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建新的测试用例。
     */
    @Operation(summary = "创建新的测试用例")
    @PostMapping
    public Map<String, Object> createTestCase(@RequestBody TestCaseRequest request) {
        Map<String, Object> valError = validateInput(request);
        if (valError != null) return valError;
        TestCaseEntity testCase = new TestCaseEntity(
                request.getName(),
                request.getInput(),
                request.getExpected()
        );
        testCase.setGroupId(request.getGroupId());
        testCase.setProject(request.getProject());
        testCase.setModule(request.getModule());
        testCase.setFunction(request.getFunction());
        testCase.setDescription(request.getDescription());
        testCase.setMetadata(request.getMetadata());

        TestCaseEntity saved = repository.saveTestCase(testCase);

        return Map.of("success", true, "testCase", saved);
    }

    /**
     * 分页列出测试用例，支持关键词过滤。
     */
    @Operation(summary = "分页列出测试用例，支持关键词过滤")
    @GetMapping
    public Map<String, Object> listTestCases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword
    ) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > Constants.MAX_BATCH_SIZE) size = Constants.MAX_BATCH_SIZE;

        List<TestCaseEntity> testCases;
        int total;

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            testCases = repository.findAllTestCases().stream()
                    .filter(tc ->
                            (tc.getName() != null && tc.getName().toLowerCase().contains(kw)) ||
                            (tc.getInput() != null && tc.getInput().toLowerCase().contains(kw)) ||
                            (tc.getExpected() != null && tc.getExpected().toLowerCase().contains(kw)) ||
                            (tc.getDescription() != null && tc.getDescription().toLowerCase().contains(kw)) ||
                            (tc.getMetadata() != null && tc.getMetadata().toString().toLowerCase().contains(kw))
                    )
                    .toList();
            total = testCases.size();
            int from = (page - 1) * size;
            if (from >= testCases.size()) {
                testCases = List.of();
            } else {
                testCases = testCases.subList(from, Math.min(from + size, testCases.size()));
            }
        } else {
            testCases = repository.findAllTestCasesPage(page, size);
            total = repository.countAllTestCases();
        }

        int totalPages = (int) Math.ceil((double) total / size);

        return Map.of(
                "success", true,
                "testCases", testCases,
                "total", total,
                "page", page,
                "size", size,
                "totalPages", totalPages
        );
    }

    /**
     * 获取所有三维分组去重值（projects/modules/functions）。
     */
    @Operation(summary = "获取所有三维分组去重值（projects/modules/functions）")
    @GetMapping("/dimensions")
    public Map<String, Object> getDimensions() {
        return Map.of(
                "success", true,
                "projects", repository.findDistinctProjects(),
                "modules", repository.findDistinctModules(),
                "functions", repository.findDistinctFunctions()
        );
    }

    /**
     * 获取全部测试用例（不分页，供评测页面使用）。
     */
    @Operation(summary = "获取全部测试用例（不分页）")
    @GetMapping("/all")
    public Map<String, Object> getAllTestCases() {
        List<TestCaseEntity> testCases = repository.findAllTestCases();
        return Map.of("success", true, "testCases", testCases, "total", testCases.size());
    }

    /**
     * 获取指定测试用例详情。
     */
    @Operation(summary = "获取指定测试用例详情")
    @GetMapping("/{id}")
    public Map<String, Object> getTestCase(@PathVariable String id) {
        return repository.findTestCaseById(id)
                .map(tc -> Map.<String, Object>of("success", true, "testCase", tc))
                .orElse(Map.of("success", false, "error", "测试用例不存在"));
    }

    /**
     * 更新指定测试用例内容。
     */
    @Operation(summary = "更新指定测试用例内容")
    @PutMapping("/{id}")
    public Map<String, Object> updateTestCase(
            @PathVariable String id,
            @RequestBody TestCaseRequest request
    ) {
        Map<String, Object> valError = validateInput(request);
        if (valError != null) return valError;
        return repository.findTestCaseById(id)
                .map(tc -> {
                    tc.setName(request.getName());
                    tc.setInput(request.getInput());
                    tc.setExpected(request.getExpected());
                    if (request.getGroupId() != null) tc.setGroupId(request.getGroupId());
                    if (request.getProject() != null) tc.setProject(request.getProject());
                    if (request.getModule() != null) tc.setModule(request.getModule());
                    if (request.getFunction() != null) tc.setFunction(request.getFunction());
                    if (request.getDescription() != null) tc.setDescription(request.getDescription());
                    if (request.getMetadata() != null) tc.setMetadata(request.getMetadata());
                    TestCaseEntity saved = repository.saveTestCase(tc);
                    return Map.<String, Object>of("success", true, "testCase", saved);
                })
                .orElse(Map.of("success", false, "error", "测试用例不存在"));
    }

    /**
     * 删除指定测试用例（软删除）。
     */
    @Operation(summary = "删除指定测试用例（软删除）")
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTestCase(@PathVariable String id) {
        Optional<TestCaseEntity> opt = repository.findTestCaseById(id);
        if (opt.isEmpty()) {
            return Map.of("success", false, "error", "测试用例不存在");
        }
        TestCaseEntity tc = opt.get();
        if (tc.getDeleted() != null && tc.getDeleted()) {
            return Map.of("success", false, "error", "测试用例已被删除");
        }
        repository.deleteTestCase(id);
        return Map.of("success", true, "message", "测试用例已删除，可在回收站中恢复");
    }

    /**
     * 恢复已删除的测试用例。
     */
    @Operation(summary = "恢复已删除的测试用例")
    @PostMapping("/{id}/restore")
    public Map<String, Object> restoreTestCase(@PathVariable String id) {
        Optional<TestCaseEntity> opt = repository.findTestCaseById(id);
        if (opt.isEmpty()) {
            return Map.of("success", false, "error", "测试用例不存在");
        }
        TestCaseEntity tc = opt.get();
        if (tc.getDeleted() == null || !tc.getDeleted()) {
            return Map.of("success", false, "error", "测试用例未被删除");
        }
        repository.restoreTestCase(id);
        return Map.of("success", true, "message", "测试用例已恢复");
    }

    /**
     * 强制删除测试用例（永久删除，不可恢复）。
     */
    @Operation(summary = "强制删除测试用例（永久删除，不可恢复）")
    @DeleteMapping("/{id}/force")
    public Map<String, Object> forceDeleteTestCase(@PathVariable String id) {
        Optional<TestCaseEntity> opt = repository.findTestCaseById(id);
        if (opt.isEmpty()) {
            return Map.of("success", false, "error", "测试用例不存在");
        }
        repository.forceDeleteTestCase(id);
        return Map.of("success", true, "message", "测试用例已永久删除");
    }

    /**
     * 获取已删除的测试用例（回收站）。
     */
    @Operation(summary = "获取已删除的测试用例（回收站）")
    @GetMapping("/deleted")
    public Map<String, Object> listDeletedTestCases() {
        List<TestCaseEntity> deleted = repository.findDeletedTestCases();
        return Map.of("success", true, "testCases", deleted, "total", deleted.size());
    }

    /**
     * 输入校验（static 供 BatchController 复用）。
     */
    static Map<String, Object> validateInput(TestCaseRequest request) {
        if (request.getInput() == null || request.getInput().isBlank()) {
            return ApiResponse.error("输入不能为空");
        }
        if (request.getExpected() == null || request.getExpected().isBlank()) {
            return ApiResponse.error("期望输出不能为空");
        }
        if (request.getInput().length() > Constants.MAX_INPUT_LENGTH
                || request.getExpected().length() > Constants.MAX_INPUT_LENGTH) {
            return ApiResponse.error("输入或期望输出不能超过 " + Constants.MAX_INPUT_LENGTH + " 字符");
        }
        return null;
    }
}
