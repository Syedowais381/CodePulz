package io.github.Syedowais381.CodePulz.dto;

import lombok.Data;

@Data // Lombok annotation to create getters, setters, etc.
public class ExecutionRequest {
    private String language;
    private String code;
    private String input; // Standard input for programs that read from console
}



