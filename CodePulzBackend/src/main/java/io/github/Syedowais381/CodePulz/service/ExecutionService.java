package io.github.Syedowais381.CodePulz.service;
import io.github.Syedowais381.CodePulz.dto.ExecutionRequest;
import io.github.Syedowais381.CodePulz.dto.ExecutionResponse;
import io.github.Syedowais381.CodePulz.dto.ExecutionSession;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExecutionService {
    
 private static final long EXECUTION_TIMEOUT_SECONDS = 60;
 private static final long OUTPUT_READ_TIMEOUT_SECONDS = 5;
 private static final long SESSION_CLEANUP_DELAY_SECONDS = 300; // Clean up sessions after 5 minutes of inactivity
 
 // Store active execution sessions
 private final ConcurrentHashMap<String, ExecutionSession> activeSessions = new ConcurrentHashMap<>();
 private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

 /**
  * Start interactive execution - creates a session and starts the process
  * Returns initial output if available
  */
 public ExecutionResponse startInteractiveExecution(ExecutionRequest request) {
     long startTime = System.currentTimeMillis();
     String sessionId = UUID.randomUUID().toString();

     // 1. Create a unique temporary directory
     Path tempDir = createTempDirectory();
     if (tempDir == null) {
         return new ExecutionResponse("", "Error: Could not create temp directory", 0, null, false, false);
     }

     try {
         // 2. Determine filename and build Docker command based on language
         String filename = getFilenameForLanguage(request.getLanguage());
         String[] command = buildDockerCommand(request.getLanguage(), tempDir, filename);

         // 3. Save the user's code to the specific file
         Path sourceFile = tempDir.resolve(filename);
         Files.writeString(sourceFile, request.getCode());

         // 4. Run the command using ProcessBuilder
         ProcessBuilder processBuilder = new ProcessBuilder(command);
         Process process = processBuilder.start();

         // 5. Create session with process (stdin stays open for interactive input)
         ExecutionSession session = new ExecutionSession(sessionId, process, tempDir);
         activeSessions.put(sessionId, session);

         // 6. Schedule cleanup after inactivity
         scheduleSessionCleanup(sessionId);

         // 7. Poll for initial output with multiple attempts (Docker startup + compilation takes time)
         String initialOutput = "";
         String initialError = "";
         String previousOutput = "";
         int maxAttempts = 35; // Poll up to 35 times (7 seconds total)
         boolean foundOutput = false;
         int pollsSinceLastChange = 0;
         
         for (int i = 0; i < maxAttempts; i++) {
             try {
                 Thread.sleep(200); // Wait 200ms between polls
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 break;
             }
             
             initialOutput = session.getCurrentOutput();
             initialError = session.getCurrentError();
             
             // Check if output changed (new output arrived)
             boolean outputChanged = !initialOutput.equals(previousOutput);
             
             // If process completed or died, return immediately
             if (session.isComplete() || !session.isAlive()) {
                 break;
             }
             
             // If we see an error, the process might have crashed - wait a bit and return
             if (!initialError.isEmpty() && initialError.contains("Exception")) {
                 // Give it one more poll to capture full error
                 try {
                     Thread.sleep(200);
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                 }
                 initialOutput = session.getCurrentOutput();
                 initialError = session.getCurrentError();
                 break;
             }
             
             // If we got output, mark it
             if (!initialOutput.isEmpty() || !initialError.isEmpty()) {
                 foundOutput = true;
             }
             
             if (outputChanged) {
                 previousOutput = initialOutput;
                 pollsSinceLastChange = 0; // Reset counter when output changes
             } else {
                 pollsSinceLastChange++; // Count polls with no change
             }
             
             // If we found output and haven't seen changes for 7 polls (1.4 seconds), 
             // give it a bit more time to ensure program has reached the blocking read state
             // This prevents returning too early before Scanner is ready to read
             if (foundOutput && pollsSinceLastChange >= 7) {
                 // Wait a bit more to ensure the program has reached the Scanner.nextInt() call
                 // and is actually blocking waiting for input, not about to throw an exception
                 // This extra wait helps ensure stdin is properly set up for blocking reads
                 try {
                     Thread.sleep(400); // Additional wait to ensure stdin is in blocking state
                 } catch (InterruptedException e) {
                     Thread.currentThread().interrupt();
                 }
                 // Double-check for any errors that might have occurred during the wait
                 initialError = session.getCurrentError();
                 if (!initialError.isEmpty()) {
                     initialOutput = session.getCurrentOutput();
                 }
                 break;
             }
         }

         long duration = System.currentTimeMillis() - startTime;
         boolean isComplete = session.isComplete() || !session.isAlive();
         boolean isWaitingForInput = !isComplete && session.isAlive();

         return new ExecutionResponse(initialOutput, initialError, duration, sessionId, isWaitingForInput, isComplete);

     } catch (IllegalArgumentException e) {
         return new ExecutionResponse("", e.getMessage(), 0, null, false, false);
     } catch (IOException e) {
         return new ExecutionResponse("", "Server Error: " + e.getMessage(), 0, null, false, false);
     }
 }

 /**
  * Send input to a running session and get updated output
  */
    public ExecutionResponse sendInputToSession(String sessionId, String input, boolean raw) {
     ExecutionSession session = activeSessions.get(sessionId);
     if (session == null) {
         return new ExecutionResponse("", "Session not found or expired", 0, sessionId, false, true);
     }

     if (session.isComplete() || !session.isAlive()) {
         // Session completed, return final output
         return getSessionOutput(session);
     }

        // Capture previous output so we can wait for new output after sending input
        String previousOutput = session.getCurrentOutput();

    // Send input to the process (raw -> no newline appended)
    session.sendInput(input, raw);

        // Poll for output after sending input (with multiple attempts for slower responses)
        try {
            int maxAttempts = 30; // Poll up to 30 times (6 seconds total)
            for (int i = 0; i < maxAttempts; i++) {
                Thread.sleep(200); // Wait 200ms between polls

                // Check if output has changed compared to previous
                String currentOutput = session.getCurrentOutput();
                if (!currentOutput.equals(previousOutput) || session.isComplete() || !session.isAlive()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return getSessionOutput(session);
 }

 /**
  * Get current output from a session
  */
 public ExecutionResponse getSessionStatus(String sessionId) {
     ExecutionSession session = activeSessions.get(sessionId);
     if (session == null) {
         return new ExecutionResponse("", "Session not found or expired", 0, sessionId, false, true);
     }
     return getSessionOutput(session);
 }

    /**
     * Return the ExecutionSession for a given sessionId, or null if not found.
     * This is used by the WebSocket handler to attach to an existing session.
     */
    public ExecutionSession getSessionById(String sessionId) {
        return activeSessions.get(sessionId);
    }

 /**
  * Helper to get current output from session
  */
 private ExecutionResponse getSessionOutput(ExecutionSession session) {
     long duration = System.currentTimeMillis() - session.getStartTime();
     boolean isComplete = session.isComplete() || !session.isAlive();
     boolean isWaitingForInput = !isComplete && session.isAlive();

     // Get current accumulated output
     String output = session.getCurrentOutput();
     String error = session.getCurrentError();

     // Clean up if complete
     if (isComplete) {
         cleanupSession(session.getSessionId());
     }

     return new ExecutionResponse(output, error, duration, session.getSessionId(), isWaitingForInput, isComplete);
 }

 /**
  * Schedule cleanup of a session after inactivity
  */
 private void scheduleSessionCleanup(String sessionId) {
     cleanupExecutor.schedule(() -> {
         ExecutionSession session = activeSessions.get(sessionId);
         if (session != null && (session.isComplete() || !session.isAlive())) {
             cleanupSession(sessionId);
         }
     }, SESSION_CLEANUP_DELAY_SECONDS, TimeUnit.SECONDS);
 }

 /**
  * Clean up a session
  */
 private void cleanupSession(String sessionId) {
     ExecutionSession session = activeSessions.remove(sessionId);
     if (session != null) {
         session.close();
         deleteDirectory(session.getTempDir().toFile());
     }
 }

 /**
  * Original non-interactive execution method (for backward compatibility)
  * Can still be used for programs that don't need interactive input
  */
 public ExecutionResponse executeCode(ExecutionRequest request) {
     // If input is provided upfront, use non-interactive mode
     if (request.getInput() != null && !request.getInput().isEmpty()) {
         return executeCodeNonInteractive(request);
     }
     // Otherwise, start interactive session
     return startInteractiveExecution(request);
 }

 /**
  * Non-interactive execution (original behavior)
  */
 private ExecutionResponse executeCodeNonInteractive(ExecutionRequest request) {
     long startTime = System.currentTimeMillis();

     Path tempDir = createTempDirectory();
     if (tempDir == null) {
         return new ExecutionResponse("", "Error: Could not create temp directory", 0, null, false, false);
     }

     try {
         String filename = getFilenameForLanguage(request.getLanguage());
         String[] command = buildDockerCommand(request.getLanguage(), tempDir, filename);

         Path sourceFile = tempDir.resolve(filename);
         Files.writeString(sourceFile, request.getCode());

         ProcessBuilder processBuilder = new ProcessBuilder(command);
         Process process = processBuilder.start();

         // Write input upfront if provided
         try (PrintWriter stdinWriter = new PrintWriter(
                 new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8), true)) {
             if (request.getInput() != null && !request.getInput().isEmpty()) {
                 stdinWriter.print(request.getInput());
             }
             stdinWriter.flush();
         }

         CompletableFuture<String> outputFuture = readStream(new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)));
         CompletableFuture<String> errorFuture = readStream(new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)));

         boolean finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

         String output = "";
         String error = "";

         if (!finished) {
             process.destroyForcibly();
             error = "Execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds.";
         } else {
             output = outputFuture.get(OUTPUT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
             error = errorFuture.get(OUTPUT_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
         }

         long duration = System.currentTimeMillis() - startTime;
         return new ExecutionResponse(output, error, duration, null, false, true);

     } catch (IllegalArgumentException e) {
         return new ExecutionResponse("", e.getMessage(), 0, null, false, false);
     } catch (IOException | InterruptedException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
         return new ExecutionResponse("", "Server Error: " + e.getMessage(), 0, null, false, false);
     } finally {
         deleteDirectory(tempDir.toFile());
     }
 }

 /**
  * Determines the correct filename for a given language.
  */
 private String getFilenameForLanguage(String language) {
     switch (language.toLowerCase()) {
         case "java":
             return "Main.java";
         case "python":
             return "script.py";
         case "javascript":
             return "index.js";
         case "cpp":
             return "main.cpp";
         case "c": // <<< ADD THIS CASE
             return "main.c";
         case "csharp":
             return "Program.cs";
         case "go":
             return "main.go";
         default:
             throw new IllegalArgumentException("Unsupported language: " + language);
     }
 }

 /**
  * Builds the complete, sandboxed docker command for a given language.
  */
 /**
  * Builds the complete, sandboxed docker command for a given language.
  */
    private String[] buildDockerCommand(String language, Path tempDir, String filename) {
        String mountPath = tempDir.toAbsolutePath().toString();

        List<String> command = new ArrayList<>();
        command.addAll(Arrays.asList("docker", "run", "--rm"));
        command.add("-i"); // keep stdin open
        // Removed -t flag entirely as it causes issues with non-TTY input
        command.addAll(Arrays.asList(
            "--cpus=0.5",
            "--memory=256m",
            "--workdir", "/app",
            "-v", mountPath + ":/app",
            // Add pseudo-TTY disable flag to ensure consistent behavior
            "-e", "PYTHONUNBUFFERED=1",  // Force unbuffered Python output
            "-e", "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8" // Force Java UTF-8
        ));

     switch (language.toLowerCase()) {
         case "java":
             command.add("openjdk:17-slim");
             command.addAll(Arrays.asList("sh", "-c", "javac Main.java && java Main"));
             break;
         case "python":
             command.add("python:3.10-slim");
             command.addAll(Arrays.asList("python", filename));
             break;
         case "javascript":
             command.add("node:18-slim");
             command.addAll(Arrays.asList("node", filename));
             break;
         case "cpp":
             command.add("gcc:latest");
             command.addAll(Arrays.asList("sh", "-c", "g++ " + filename + " -o myapp && ./myapp"));
             break;
         case "c": // <<< ADD THIS CASE
             command.add("gcc:latest");
             command.addAll(Arrays.asList("sh", "-c", "gcc " + filename + " -o myapp && ./myapp"));
             break;
         case "csharp":
             // *** FIXED COMMAND ***
             // Create project files, then run. The user's Program.cs is already mounted.
             command.add("mcr.microsoft.com/dotnet/sdk:7.0");
             command.addAll(Arrays.asList("sh", "-c",
                     "dotnet new console --force > /dev/null && dotnet run"));
             break;
         case "go":
             command.add("golang:1.20");
             command.addAll(Arrays.asList("go", "run", filename));
             break;
         default:
             throw new IllegalArgumentException("Unsupported language: " + language);
     }

     return command.toArray(new String[0]);
 }

 // --- Helper Methods (Unchanged) ---

 private Path createTempDirectory() {
     try {
         String tempDirName = "codepulz-run-" + UUID.randomUUID();
         // Use system's temp directory
         return Files.createTempDirectory(tempDirName);
     } catch (IOException e) {
         return null;
     }
 }

 private CompletableFuture<String> readStream(BufferedReader reader) {
     return CompletableFuture.supplyAsync(() -> {
         StringBuilder sb = new StringBuilder();
         try (reader) {
             String line;
             while ((line = reader.readLine()) != null) {
                 sb.append(line).append("\n");
             }
         } catch (IOException e) {
             return "Error reading stream: " + e.getMessage();
         }
         return sb.toString().trim();
     });
 }

 private void deleteDirectory(File directory) {
     File[] allContents = directory.listFiles();
     if (allContents != null) {
         for (File file : allContents) {
             deleteDirectory(file);
         }
     }
     directory.delete();
 }
}