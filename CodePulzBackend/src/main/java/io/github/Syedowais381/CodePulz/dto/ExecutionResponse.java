package io.github.Syedowais381.CodePulz.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResponse {
    private String output;
    private String error;
    private long executionTimeMs;
    private String sessionId; // For interactive execution sessions
    private boolean isWaitingForInput; // Indicates if process is waiting for user input
    private boolean isComplete; // Indicates if execution is complete
}