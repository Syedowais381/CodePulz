package io.github.Syedowais381.CodePulz.dto;

import lombok.Data;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Data
public class ExecutionSession {
    private String sessionId;
    private Process process;
    private Path tempDir;
    private PrintWriter stdinWriter;
    private long startTime;
    private long lastOutputTime;
    private boolean isComplete;
    
    // Accumulated output and error
    private final AtomicReference<StringBuilder> outputBuffer = new AtomicReference<>(new StringBuilder());
    private final AtomicReference<StringBuilder> errorBuffer = new AtomicReference<>(new StringBuilder());
    // Track when streams reach EOF
    private final java.util.concurrent.atomic.AtomicBoolean stdoutClosed = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean stderrClosed = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final long EXECUTION_TIMEOUT_SECONDS = 60;

    public ExecutionSession(String sessionId, Process process, Path tempDir) {
        this.sessionId = sessionId;
        this.process = process;
        this.tempDir = tempDir;
        this.startTime = System.currentTimeMillis();
        this.lastOutputTime = System.currentTimeMillis();
        this.isComplete = false;
        
        // Create stdin writer that stays open for interactive input
        this.stdinWriter = new PrintWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true);
        
        // Start reading output and error streams asynchronously
        startReadingOutput();
        startReadingError();
        
        // Monitor process completion
        executorService.submit(() -> {
            try {
                boolean finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }
                // Append a clear message indicating the process has exited and its exit code
                try {
                    int exitCode = -1;
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException itse) {
                        // process not yet terminated; ignore
                    }
                    outputBuffer.get().append("\n[Process exited with code: ").append(exitCode).append("]\n");
                    lastOutputTime = System.currentTimeMillis();
                } catch (Exception ex) {
                    // ignore any logging errors
                }
                this.isComplete = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.isComplete = true;
            }
        });
    }

    private void startReadingOutput() {
        // Read raw bytes from stdout so we capture text even when there's no trailing newline
        executorService.submit(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buf = new byte[1024];
                int read;
                while ((read = in.read(buf)) != -1) {
                    if (read > 0) {
                        String s = new String(buf, 0, read, StandardCharsets.UTF_8);
                        outputBuffer.get().append(s);
                        lastOutputTime = System.currentTimeMillis();
                    }
                }
                // reached EOF on stdout
                stdoutClosed.set(true);
                // If stderr also closed and process not marked complete, mark it complete
                if (stderrClosed.get()) {
                    this.isComplete = true;
                }
            } catch (IOException e) {
                errorBuffer.get().append("Error reading output: ").append(e.getMessage()).append("\n");
            }
        });
    }

    private void startReadingError() {
        // Read stderr as raw bytes as well to ensure partial lines/errors are captured promptly
        executorService.submit(() -> {
            try (InputStream err = process.getErrorStream()) {
                byte[] buf = new byte[1024];
                int read;
                while ((read = err.read(buf)) != -1) {
                    if (read > 0) {
                        String s = new String(buf, 0, read, StandardCharsets.UTF_8);
                        errorBuffer.get().append(s);
                    }
                }
                // reached EOF on stderr
                stderrClosed.set(true);
                if (stdoutClosed.get()) {
                    this.isComplete = true;
                }
            } catch (IOException e) {
                // Ignore error reading errors
            }
        });
    }

    public String getCurrentOutput() {
        // Return the accumulated output as-is (do not trim). Trimming removes prompts
        // like "Enter a number: " which may not end with a newline.
        return outputBuffer.get().toString();
    }

    public String getCurrentError() {
        return errorBuffer.get().toString();
    }

    /**
     * Send input to the process. If raw is true, the input is sent without a newline.
     * If raw is false, a newline is appended (println) which is the common case for console input.
     */
    public void sendInput(String input, boolean raw) {
        if (stdinWriter != null && !isComplete) {
            if (raw) {
                stdinWriter.print(input);
            } else {
                stdinWriter.println(input);
            }
            stdinWriter.flush();
        }
    }

    public void close() {
        try {
            if (stdinWriter != null) {
                stdinWriter.close();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive() && !isComplete;
    }
}

