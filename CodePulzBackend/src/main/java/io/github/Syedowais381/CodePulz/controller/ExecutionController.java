package io.github.Syedowais381.CodePulz.controller;



import io.github.Syedowais381.CodePulz.dto.ExecutionRequest;
import io.github.Syedowais381.CodePulz.dto.ExecutionResponse;
import io.github.Syedowais381.CodePulz.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin; // <-- 1. ADD THIS IMPORT

@RestController
@RequestMapping("/api/v1") // All endpoints in this class start with /api/v1
@Tag(name = "Code Execution", description = "API for compiling and running code")
@CrossOrigin(origins = {"https://codepulz.netlify.app", "http://localhost:5173", "http://127.0.0.1:5173"})// <-- 2. ADD THIS ANNOTATION
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
}