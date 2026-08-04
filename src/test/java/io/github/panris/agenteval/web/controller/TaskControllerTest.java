package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.service.AsyncEvalService;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for TaskController — no Spring context needed.
 * Controller is constructed directly with mocked AsyncEvalService.
 */
class TaskControllerTest {

    private TaskController controller;
    private AsyncEvalService mockAsyncEvalService;

    @BeforeEach
    void setUp() {
        mockAsyncEvalService = mock(AsyncEvalService.class);
        controller = new TaskController(mockAsyncEvalService);
    }

    @Test
    @DisplayName("GET /api/tasks → returns list of task statuses")
    void testListTasks() {
        AsyncEvalService.TaskStatus s1 = new AsyncEvalService.TaskStatus("task-1");
        s1.status = "COMPLETED";
        s1.totalCases = 2;
        s1.completedCases = 2;
        s1.createdAt = System.currentTimeMillis();

        AsyncEvalService.TaskStatus s2 = new AsyncEvalService.TaskStatus("task-2");
        s2.status = "RUNNING";
        s2.totalCases = 3;
        s2.completedCases = 1;
        s2.createdAt = System.currentTimeMillis();

        when(mockAsyncEvalService.getAllStatuses()).thenReturn(List.of(s1, s2));

        List<Map<String, Object>> tasks = controller.listTasks();

        assertEquals(2, tasks.size());
        assertEquals("task-1", tasks.get(0).get("taskId"));
        assertEquals("COMPLETED", tasks.get(0).get("status"));
        assertEquals("task-2", tasks.get(1).get("taskId"));
        assertEquals("RUNNING", tasks.get(1).get("status"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} → returns specific task status")
    void testGetTaskStatus() {
        AsyncEvalService.TaskStatus s = new AsyncEvalService.TaskStatus("task-xyz");
        s.status = "PENDING";
        s.totalCases = 5;
        s.completedCases = 0;
        s.createdAt = System.currentTimeMillis();

        when(mockAsyncEvalService.getStatus("task-xyz")).thenReturn(s);

        Map<String, Object> resp = controller.getTaskStatus("task-xyz");

        assertTrue((Boolean) resp.get("success"));
        assertEquals("task-xyz", resp.get("taskId"));
        assertEquals("PENDING", resp.get("status"));
    }

    @Test
    @DisplayName("GET /api/tasks/{id} with unknown id → returns error")
    void testGetTaskStatusNotFound() {
        when(mockAsyncEvalService.getStatus("unknown")).thenReturn(null);

        Map<String, Object> resp = controller.getTaskStatus("unknown");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("任务不存在", resp.get("error"));
    }
}
