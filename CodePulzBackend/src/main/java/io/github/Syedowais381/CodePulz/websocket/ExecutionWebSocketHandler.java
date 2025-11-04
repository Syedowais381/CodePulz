package io.github.Syedowais381.CodePulz.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.Syedowais381.CodePulz.dto.ExecutionSession;
import io.github.Syedowais381.CodePulz.service.ExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler that attaches to an existing ExecutionSession (created via HTTP /execute)
 * and streams stdout/stderr to the client, while accepting stdin frames from the client.
 *
 * Protocol (JSON text frames):
 * - Client -> Server: { "type": "stdin", "data": "...", "raw": false }
 * - Server -> Client: { "type": "stdout", "data": "..." }
 * - Server -> Client: { "type": "stderr", "data": "..." }
 * - Server -> Client: { "type": "exit", "code": 0 }
 */
@Component
public class ExecutionWebSocketHandler extends TextWebSocketHandler {

    private final ExecutionService executionService;
    private final ObjectMapper mapper = new ObjectMapper();

    // track pollers per websocket session
    private final Map<String, ScheduledFuture<?>> pollers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @Autowired
    public ExecutionWebSocketHandler(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extract sessionId from the URI: /ws/execute/{sessionId}
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        String execSessionId = parts[parts.length - 1];

        ExecutionSession execSession = executionService.getSessionById(execSessionId);
        if (execSession == null) {
            session.sendMessage(new TextMessage(mapper.createObjectNode()
                    .put("type", "error")
                    .put("message", "Execution session not found: " + execSessionId)
                    .toString()));
            session.close();
            return;
        }

        // Start a poller that checks for new output every 100ms and sends deltas
        final String wsId = session.getId();
        final Runnable poller = new Runnable() {
            private String lastOut = "";
            private String lastErr = "";

            private boolean lastWaitingForInput = false;
            
            @Override
            public void run() {
                try {
                    String out = execSession.getCurrentOutput();
                    String err = execSession.getCurrentError();

                    if (!out.equals(lastOut)) {
                        String delta = out.substring(lastOut.length());
                        lastOut = out;
                        session.sendMessage(new TextMessage(mapper.createObjectNode()
                                .put("type", "stdout")
                                .put("data", delta)
                                .toString()));
                    }
                    if (!err.equals(lastErr)) {
                        String delta = err.substring(lastErr.length());
                        lastErr = err;
                        session.sendMessage(new TextMessage(mapper.createObjectNode()
                                .put("type", "stderr")
                                .put("data", delta)
                                .toString()));
                    }

                    // Only send exit and close when process has truly finished
                    if (execSession.isComplete() && !execSession.isAlive()) {
                        session.sendMessage(new TextMessage(mapper.createObjectNode()
                                .put("type", "exit")
                                .put("code", 0)
                                .toString()));
                        session.close();
                    } else {
                        // Track the waiting state
                        long timeSinceLastOutput = System.currentTimeMillis() - execSession.getLastOutputTime();

                        // A program is considered waiting for input if:
                        // 1. It's alive and not complete
                        // 2. There has been no new output for a short time (give programs time to print prompts)
                        // 3. We've received some initial output
                        // Increase quiet period to avoid noisy false positives on some hosts/containers.
                        boolean waitingForInput = execSession.isAlive() &&
                                               !execSession.isComplete() &&
                                               timeSinceLastOutput > 300 && // 300ms quiet period
                                               !lastOut.isEmpty();

                        // Always send waiting status after input is processed
                        if (waitingForInput != lastWaitingForInput) {
                            session.sendMessage(new TextMessage(mapper.createObjectNode()
                                    .put("type", "status")
                                    .put("waitingForInput", waitingForInput)
                                    .toString()));
                            lastWaitingForInput = waitingForInput;
                            
                            // Reset the input state after sending input
                            if (!waitingForInput) {
                                lastOut = execSession.getCurrentOutput();
                                lastErr = execSession.getCurrentError();
                            }
                        }
                    }
                } catch (IOException e) {
                    // Ignore send errors; close will cleanup
                    try {
                        session.close();
                    } catch (IOException ex) {
                        // ignore
                    }
                }
            }
        };

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(poller, 0, 100, TimeUnit.MILLISECONDS);
        pollers.put(wsId, future);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.has("type") ? node.get("type").asText() : "stdin";

        // Extract execution session id from URI
        String path = session.getUri().getPath();
        String[] parts = path.split("/");
        String execSessionId = parts[parts.length - 1];
        ExecutionSession execSession = executionService.getSessionById(execSessionId);
        if (execSession == null) {
            session.sendMessage(new TextMessage(mapper.createObjectNode()
                    .put("type", "error")
                    .put("message", "Execution session not found")
                    .toString()));
            session.close();
            return;
        }

        if ("stdin".equals(type)) {
            String data = node.has("data") ? node.get("data").asText() : "";
            boolean raw = node.has("raw") && node.get("raw").asBoolean(false);
            execSession.sendInput(data, raw);
        } else if ("close".equals(type)) {
            execSession.close();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) throws Exception {
        ScheduledFuture<?> future = pollers.remove(session.getId());
        if (future != null) future.cancel(true);
    }
}
