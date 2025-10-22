package io.github.Syedowais381.CodePulz.service;



import io.github.Syedowais381.CodePulz.dto.ExecutionRequest;
import io.github.Syedowais381.CodePulz.dto.ExecutionResponse;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {

 public ExecutionResponse executeCode(ExecutionRequest request) {
     // --- THIS IS THE DANGER ZONE ---
     // TODO: This is where all the Docker sandbox logic will go.
     
     // For now, we'll just return a fake, hard-coded response
     // so we can test the API.
     
     String fakeOutput = "Hello from the backend! Your code isn't running yet.";
     return new ExecutionResponse(fakeOutput, "", 0L);
 }
}