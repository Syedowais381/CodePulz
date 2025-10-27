package io.github.Syedowais381.CodePulz.service;
import io.github.Syedowais381.CodePulz.dto.ExecutionRequest;
import io.github.Syedowais381.CodePulz.dto.ExecutionResponse;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ExecutionService {
    
 private static final long EXECUTION_TIMEOUT_SECONDS = 60;
 private static final long OUTPUT_READ_TIMEOUT_SECONDS = 5;

 public ExecutionResponse executeCode(ExecutionRequest request) {
     long startTime = System.currentTimeMillis();

     // 1. Create a unique temporary directory
     Path tempDir = createTempDirectory();
     if (tempDir == null) {
         return new ExecutionResponse("", "Error: Could not create temp directory", 0);
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

         // 5. Capture stdout and stderr asynchronously
         CompletableFuture<String> outputFuture = readStream(new BufferedReader(new InputStreamReader(process.getInputStream())));
         CompletableFuture<String> errorFuture = readStream(new BufferedReader(new InputStreamReader(process.getErrorStream())));

         // 6. Wait for the process to finish, with a timeout
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
         return new ExecutionResponse(output, error, duration);

     } catch (IllegalArgumentException e) {
         return new ExecutionResponse("", e.getMessage(), 0); // Handle unsupported language
     } catch (IOException | InterruptedException | java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
         return new ExecutionResponse("", "Server Error: " + e.getMessage(), 0);
     } finally {
         // 7. Clean up the temporary directory
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

     List<String> command = new ArrayList<>(Arrays.asList(
             "docker", "run", "--rm",
             "--cpus=0.5",
             "--memory=256m",
             "--workdir", "/app",
             "-v", mountPath + ":/app"
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