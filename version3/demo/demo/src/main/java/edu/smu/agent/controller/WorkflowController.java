package edu.smu.agent.controller;

import edu.smu.agent.service.DifyWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkflowController {

    private final DifyWorkflowService difyWorkflowService;

    /**
     * 执行工作流（阻塞模式）
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            @SuppressWarnings("unchecked")
            Map<String, Object> inputs = (Map<String, Object>) request.get("inputs");

            if (userId == null || inputs == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户ID与inputs不能为空"));
            }

            Map<String, Object> data = difyWorkflowService.executeBlocking(inputs, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("执行工作流失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 执行工作流（流式模式）
     */
    @PostMapping("/execute-stream")
    public ResponseEntity<Map<String, Object>> executeStream(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            @SuppressWarnings("unchecked")
            Map<String, Object> inputs = (Map<String, Object>) request.get("inputs");

            if (userId == null || inputs == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用户ID与inputs不能为空"));
            }

            Map<String, Object> data = difyWorkflowService.executeStreaming(inputs, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("执行工作流（流式）失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 停止工作流
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(@RequestBody Map<String, Object> request) {
        try {
            String taskId = (String) request.get("taskId");
            String userId = (String) request.get("userId");
            if (taskId == null || userId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "taskId 与 userId 不能为空"));
            }

            Map<String, Object> data = difyWorkflowService.stopWorkflow(taskId, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("停止工作流失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取工作流运行状态
     */
    @GetMapping("/runs/{workflowRunId}")
    public ResponseEntity<Map<String, Object>> getRun(@PathVariable String workflowRunId) {
        try {
            Map<String, Object> data = difyWorkflowService.getWorkflowRun(workflowRunId);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取工作流运行状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取工作流日志
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            Map<String, Object> data = difyWorkflowService.getWorkflowLogs(start, end, page, pageSize);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.putAll(data);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("获取工作流日志失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
