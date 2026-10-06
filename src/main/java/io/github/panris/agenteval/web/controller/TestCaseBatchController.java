package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.web.Constants;
import io.github.panris.agenteval.web.dto.ApiResponse;
import io.github.panris.agenteval.web.dto.TestCaseRequest;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 测试用例批量操作：批量导入、标签管理。
 */
@RestController
@RequestMapping("/api/testcases")
public class TestCaseBatchController {

    private static final Logger log = LoggerFactory.getLogger(TestCaseBatchController.class);

    private final TestCaseRepository repository;

    public TestCaseBatchController(TestCaseRepository repository) {
        this.repository = repository;
    }

    /**
     * 批量更新测试用例标签。
     */
    @Operation(summary = "批量更新测试用例标签")
    @PutMapping("/{id}/tags")
    public Map<String, Object> updateTags(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (body == null || !body.containsKey("tags")) {
            return ApiResponse.error("tags 不能为空");
        }
        Object tagsObj = body.get("tags");
        if (!(tagsObj instanceof List)) {
            return ApiResponse.error("tags 必须是一个列表");
        }
        Optional<TestCaseEntity> opt = repository.findTestCaseById(id);
        if (opt.isEmpty()) {
            return ApiResponse.error("测试用例不存在");
        }
        TestCaseEntity tc = opt.get();
        if (tc.getMetadata() == null) {
            tc.setMetadata(new java.util.LinkedHashMap<>());
        }
        tc.getMetadata().put("tags", tagsObj);
        tc.updateTimestamp();
        repository.saveTestCase(tc);
        return ApiResponse.success("tags", tc.getMetadata().get("tags"));
    }

    /**
     * 批量导入测试用例（Excel/CSV）。
     */
    @Operation(summary = "批量导入测试用例（Excel/CSV）")
    @PostMapping("/batch")
    public Map<String, Object> batchImport(@RequestBody List<TestCaseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.warn("batchImport: request list is empty");
            return ApiResponse.error("Request list is empty");
        }
        if (requests.size() > Constants.MAX_BATCH_SIZE) {
            log.warn("batchImport: batch size {} exceeds {} items", requests.size(), Constants.MAX_BATCH_SIZE);
            return ApiResponse.error("Batch size exceeds " + Constants.MAX_BATCH_SIZE + " items");
        }
        for (int i = 0; i < requests.size(); i++) {
            Map<String, Object> ve = TestCaseController.validateInput(requests.get(i));
            if (ve != null) {
                log.warn("batchImport: item {} validation failed", i);
                return ve;
            }
        }
        List<TestCaseEntity> testCases = requests.stream()
                .map(req -> {
                    TestCaseEntity tc = new TestCaseEntity(
                            req.getName(),
                            req.getInput(),
                            req.getExpected()
                    );
                    tc.setGroupId(req.getGroupId());
                    tc.setProject(req.getProject());
                    tc.setModule(req.getModule());
                    tc.setFunction(req.getFunction());
                    tc.setMetadata(req.getMetadata());
                    return tc;
                })
                .toList();

        List<TestCaseEntity> saved = repository.saveAllTestCases(testCases);

        return Map.of(
                "success", true,
                "imported", saved.size(),
                "testCases", saved
        );
    }
}
