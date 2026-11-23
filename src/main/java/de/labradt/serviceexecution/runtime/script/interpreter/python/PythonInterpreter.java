package de.labradt.serviceexecution.runtime.script.interpreter.python;

import de.labradt.serviceexecution.core.api.enums.ExecutionCategory;
import de.labradt.serviceexecution.core.api.enums.TechnologyAdapter;
import de.labradt.serviceexecution.runtime.script.interpreter.IScriptInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs Python services in the script host: adds the technology SCRIPT/PYTHON to the Runtime.
 *
 * <p>The stored service file is either a plain Python script, or a whole project folder packed by the UI as a
 * zip application: a first line {@value #PYZ_HEADER} followed by the base64 of the .pyz (the folder with its
 * main.py and an added __main__.py). The service arguments arrive as environment variables, as with Bash.
 *
 * <p>The interpreter command is {@code runtime.python.command} (env RUNTIME_PYTHON_COMMAND), by default
 * {@code python3} ({@code py -3} on Windows); {@code runtime.python.timeout-seconds} bounds one run.
 */
@Slf4j
@Component
public class PythonInterpreter implements IScriptInterpreter {

    /** The technology this plugin adds: a new language in the fixed category SCRIPT. */
    public static final TechnologyAdapter PYTHON = TechnologyAdapter.define("PYTHON", ExecutionCategory.SCRIPT);

    static final String PYZ_HEADER = "# labradt:pyz base64";

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final List<String> command;
    private final long timeoutSeconds;

    public PythonInterpreter(@Value("${runtime.python.command:}") String command,
                             @Value("${runtime.python.timeout-seconds:300}") long timeoutSeconds) {
        String cmd = command == null || command.isBlank() ? (IS_WINDOWS ? "py -3" : "python3") : command.trim();
        this.command = List.of(cmd.split("\\s+"));
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public TechnologyAdapter getScriptType() {
        return PYTHON;
    }

    @Override
    public String execute(String scriptContent, Map<String, String> args) throws Exception {
        boolean packed = scriptContent.startsWith(PYZ_HEADER);
        Path file = Files.createTempFile("python_service_", packed ? ".pyz" : ".py");
        try {
            if (packed) {
                String body = scriptContent.substring(PYZ_HEADER.length()).replaceAll("\\s+", "");
                Files.write(file, Base64.getDecoder().decode(body));
            } else {
                Files.writeString(file, scriptContent, StandardCharsets.UTF_8);
            }
            List<String> cmd = new ArrayList<>(command);
            cmd.add(file.toAbsolutePath().toString());
            log.info("PythonInterpreter: running {} ({}, {} args)", cmd, packed ? "project" : "script", args.size());

            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.environment().putAll(args);
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            builder.environment().put("PYTHONUNBUFFERED", "1");
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException("Python service timed out after " + timeoutSeconds + " s");
            }
            String output = stdout.get().trim();
            String errors = stderr.get().trim();
            if (process.exitValue() != 0) {
                throw new RuntimeException("Python service failed with exit code " + process.exitValue()
                        + (errors.isEmpty() ? "" : "\nStderr: " + errors)
                        + (output.isEmpty() ? "" : "\nStdout: " + output));
            }
            if (!errors.isEmpty()) {
                log.warn("PythonInterpreter: stderr: {}", errors);
            }
            return output;
        } catch (IOException e) {
            throw new RuntimeException("Could not start Python (" + String.join(" ", command)
                    + "); install Python or set runtime.python.command: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static String read(InputStream stream) {
        try (stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            stream.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
