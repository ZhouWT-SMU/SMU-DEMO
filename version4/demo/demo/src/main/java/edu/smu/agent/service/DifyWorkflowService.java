package edu.smu.agent.service;

import io.github.imfangs.dify.client.DifyClientFactory;
import io.github.imfangs.dify.client.DifyWorkflowClient;
import io.github.imfangs.dify.client.callback.WorkflowStreamCallback;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.event.*;
import io.github.imfangs.dify.client.model.workflow.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class DifyWorkflowService {

    @Value("${dify.base-url}")
    private String baseUrl;

    @Value("${dify.workflow-api-key}")
    private String workflowApiKey;

    public Map<String, Object> executeBlocking(Map<String, Object> inputs, String userId) {
        try (DifyWorkflowClient client = DifyClientFactory.createWorkflowClient(baseUrl, workflowApiKey)) {
            WorkflowRunRequest request = WorkflowRunRequest.builder()
                    .inputs(inputs)
                    .user(userId)
                    .responseMode(ResponseMode.BLOCKING)
                    .build();

            log.info("[Workflow] 发送阻塞执行: user={}, inputsKeys={}", userId, inputs != null ? inputs.keySet() : List.of());

            WorkflowRunResponse response = client.runWorkflow(request);

            Map<String, Object> result = new HashMap<>();
            result.put("mode", "blocking");
            result.put("workflowRunId", response.getWorkflowRunId());
            result.put("taskId", response.getTaskId());
            if (response.getData() != null) {
                result.put("data", response.getData());
            }
            return result;
        } catch (Exception e) {
            log.error("[Workflow] 执行阻塞失败: user={}", userId, e);
            throw new RuntimeException("执行工作流失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> executeStreaming(Map<String, Object> inputs, String userId) {
        try (DifyWorkflowClient client = DifyClientFactory.createWorkflowClient(baseUrl, workflowApiKey)) {
            WorkflowRunRequest request = WorkflowRunRequest.builder()
                    .inputs(inputs)
                    .user(userId)
                    .responseMode(ResponseMode.STREAMING)
                    .build();

            log.info("[Workflow] 发送流式执行: user={}, inputsKeys={}", userId, inputs != null ? inputs.keySet() : List.of());

            CountDownLatch finished = new CountDownLatch(1);
            StringBuilder textChunks = new StringBuilder();
            List<Object> nodeEvents = new ArrayList<>();
            AtomicReference<Exception> asyncError = new AtomicReference<>();

            client.runWorkflowStream(request, new WorkflowStreamCallback() {
                @Override
                public void onWorkflowStarted(WorkflowStartedEvent event) {
                    log.info("[Workflow] started: {}", event);
                }

                @Override
                public void onNodeStarted(NodeStartedEvent event) {
                    nodeEvents.add(event);
                    log.info("[Workflow] node started: {}", event);
                }

                @Override
                public void onWorkflowTextChunk(WorkflowTextChunkEvent event) {
                    if (event != null && event.getData() != null && event.getData().getText() != null) {
                        textChunks.append(event.getData().getText());
                    }
                    log.info("[Workflow] text chunk: {}", event);
                }

                @Override
                public void onNodeFinished(NodeFinishedEvent event) {
                    nodeEvents.add(event);
                    log.info("[Workflow] node finished: {}", event);
                }

                @Override
                public void onIterationStarted(IterationStartedEvent event) {
                    log.debug("iter start: {}", event);
                }

                @Override
                public void onIterationNext(IterationNextEvent event) {
                    log.debug("iter next: {}", event);
                }

                @Override
                public void onIterationCompleted(IterationCompletedEvent event) {
                    log.debug("iter done: {}", event);
                }

                @Override
                public void onLoopStarted(LoopStartedEvent event) {
                    log.debug("loop start: {}", event);
                }

                @Override
                public void onLoopNext(LoopNextEvent event) {
                    log.debug("loop next: {}", event);
                }

                @Override
                public void onLoopCompleted(LoopCompletedEvent event) {
                    log.debug("loop done: {}", event);
                }

                @Override
                public void onAgentLog(AgentLogEvent event) {
                    log.debug("agent log: {}", event);
                }

                @Override
                public void onTtsMessage(TtsMessageEvent event) {
                    log.debug("tts: {}", event);
                }

                @Override
                public void onTtsMessageEnd(TtsMessageEndEvent event) {
                    log.debug("tts end: {}", event);
                }

                @Override
                public void onWorkflowFinished(WorkflowFinishedEvent event) {
                    log.info("[Workflow] finished: {}", event);
                    finished.countDown();
                }

                @Override
                public void onError(ErrorEvent event) {
                    log.error("[Workflow] 流式错误: {}", event != null ? event.getMessage() : null);
                    asyncError.set(new RuntimeException("流式错误: " + (event != null ? event.getMessage() : "unknown")));
                    finished.countDown();
                }

                @Override
                public void onException(Throwable throwable) {
                    log.error("[Workflow] 流式异常: {}", throwable.getMessage(), throwable);
                    asyncError.set(new RuntimeException("流式异常: " + throwable.getMessage(), throwable));
                    finished.countDown();
                }

                @Override
                public void onPing(PingEvent event) {
                    log.debug("ping: {}", event);
                }
            });

            boolean done = finished.await(60, TimeUnit.SECONDS);
            Exception error = asyncError.get();
            if (error != null) {
                throw error;
            }
            if (!done && textChunks.length() == 0) {
                throw new RuntimeException("流式响应超时");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("mode", "streaming");
            result.put("text", textChunks.toString());
            result.put("nodes", nodeEvents);
            return result;
        } catch (Exception e) {
            log.error("[Workflow] 执行流式失败: user={}", userId, e);
            throw new RuntimeException("执行工作流（流式）失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> stopWorkflow(String taskId, String userId) {
        try (DifyWorkflowClient client = DifyClientFactory.createWorkflowClient(baseUrl, workflowApiKey)) {
            WorkflowStopResponse resp = client.stopWorkflow(taskId, userId);
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("userId", userId);
            result.put("status", resp != null ? "stopped" : "unknown");
            result.put("raw", resp);
            return result;
        } catch (Exception e) {
            log.error("[Workflow] 停止失败: taskId={}, userId={}", taskId, userId, e);
            throw new RuntimeException("停止工作流失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getWorkflowRun(String workflowRunId) {
        try (DifyWorkflowClient client = DifyClientFactory.createWorkflowClient(baseUrl, workflowApiKey)) {
            WorkflowRunStatusResponse status = client.getWorkflowRun(workflowRunId);
            Map<String, Object> result = new HashMap<>();
            result.put("workflowRunId", workflowRunId);
            result.put("status", status != null ? status.getStatus() : null);
            result.put("outputs", status != null ? status.getOutputs() : null);
            result.put("raw", status);
            return result;
        } catch (Exception e) {
            log.error("[Workflow] 获取运行状态失败: workflowRunId={}", workflowRunId, e);
            throw new RuntimeException("获取工作流运行状态失败: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getWorkflowLogs(String start, String end, Integer page, Integer pageSize) {
        try (DifyWorkflowClient client = DifyClientFactory.createWorkflowClient(baseUrl, workflowApiKey)) {
            WorkflowLogsResponse logs = client.getWorkflowLogs(start, end, page, pageSize);
            Map<String, Object> result = new HashMap<>();
            result.put("page", logs != null ? logs.getPage() : page);
            result.put("limit", logs != null ? logs.getLimit() : pageSize);
            result.put("total", logs != null ? logs.getTotal() : null);
            result.put("hasMore", logs != null ? logs.getHasMore() : null);
            result.put("data", logs != null ? logs.getData() : List.of());
            return result;
        } catch (Exception e) {
            log.error("[Workflow] 获取日志失败: start={}, end={}, page={}, pageSize={}", start, end, page, pageSize, e);
            throw new RuntimeException("获取工作流日志失败: " + e.getMessage(), e);
        }
    }
}