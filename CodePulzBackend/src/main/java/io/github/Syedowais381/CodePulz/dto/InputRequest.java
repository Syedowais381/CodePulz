package io.github.Syedowais381.CodePulz.dto;

import lombok.Data;

@Data
public class InputRequest {
    private String sessionId;
    private String input;
    // If true, send input without appending a newline. Useful for programs
    // that expect input without a trailing newline or when client wants fine
    // control over input formatting.
    private boolean raw = false;
}

