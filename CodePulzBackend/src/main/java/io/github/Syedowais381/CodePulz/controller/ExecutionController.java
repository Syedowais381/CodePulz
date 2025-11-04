package io.github.Syedowais381.CodePulz.controller;



import io.github.Syedowais381.CodePulz.dto.ExecutionRequest;
import io.github.Syedowais381.CodePulz.dto.ExecutionResponse;
import io.github.Syedowais381.CodePulz.dto.InputRequest;
import io.github.Syedowais381.CodePulz.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1") // All endpoints in this class start with /api/v1
@Tag(name = "Code Execution", description = "API for compiling and running code")
@CrossOrigin(origins = "https://codepulz.netlify.app", allowCredentials = "true", allowedHeaders = "*")
public class ExecutionController {

 // 1. We ask Spring to "inject" the service we just made
 private final ExecutionService executionService;

 @Autowired
 public ExecutionController(ExecutionService executionService) {
     this.executionService = executionService;
 }

 // 2. This method listens for POST requests at /api/v1/execute
 @PostMapping("/execute")
 @Operation(summary = "Execute a code snippet")
 public ExecutionResponse executeCode(@RequestBody ExecutionRequest request) {
     
     // 3. It immediately delegates the work to the service
     return executionService.executeCode(request);
 }

 // 4. Start interactive execution - creates a session
 @PostMapping("/execute/start")
 @Operation(summary = "Start interactive code execution session")
 public ExecutionResponse startInteractiveExecution(@RequestBody ExecutionRequest request) {
     return executionService.startInteractiveExecution(request);
 }

 // 5. Send input to a running session
 @PostMapping("/execute/{sessionId}/input")
 @Operation(summary = "Send input to an interactive execution session")
 public ExecutionResponse sendInput(@PathVariable String sessionId, @RequestBody InputRequest inputRequest) {
     // Validate sessionId matches
     if (!sessionId.equals(inputRequest.getSessionId())) {
         return new ExecutionResponse("", "Session ID mismatch", 0, sessionId, false, true);
     }
     return executionService.sendInputToSession(sessionId, inputRequest.getInput(), inputRequest.isRaw());
 }

 // 6. Get current status and output of a session
 @GetMapping("/execute/{sessionId}/status")
 @Operation(summary = "Get current status and output of an execution session")
 public ExecutionResponse getSessionStatus(@PathVariable String sessionId) {
     return executionService.getSessionStatus(sessionId);
 }
}