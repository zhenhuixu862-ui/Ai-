package com.bear.mcp.single.core.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 在只读挂载的 YanQue 源码仓库中执行受限查询命令。 */
@Component
public class RepositoryCommandTools {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("rg", "sed", "nl", "head", "tail", "wc");
    private static final int MAX_ARGS = 30;
    private static final int MAX_ARG_LENGTH = 1_000;
    private static final int MAX_OUTPUT_LINES = 200;
    private static final int MAX_OUTPUT_CHARS = 40_000;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            ".env", "id_rsa", "id_ed25519", "credentials", "credential", "secrets", "secret");

    private final Map<String, Path> repositories;

    public RepositoryCommandTools(
            @Value("${YANQUE_SOURCE_ROOT:./demo-repository}") String yanqueSourceRoot) {
        this.repositories = Map.of("yanque-platform", Path.of(yanqueSourceRoot).toAbsolutePath().normalize());
    }

    @Tool(name = "run_repository_command",
            description = "在服务器只读挂载的 yanque-platform 源码中执行受限检索命令。仅支持 rg、sed、nl、head、tail、wc")
    public Map<String, Object> runRepositoryCommand(
            @ToolParam(description = "仓库名称，当前只允许 yanque-platform") String repository,
            @ToolParam(description = "只读命令：rg、sed、nl、head、tail、wc") String command,
            @ToolParam(description = "命令参数数组；路径必须相对于仓库，禁止绝对路径和 ../", required = false)
            List<String> args) {
        Path repositoryRoot = validateRepository(repository);
        String safeCommand = validateCommand(command);
        List<String> safeArgs = validateArgs(repositoryRoot, safeCommand, args == null ? List.of() : args);

        List<String> invocation = new ArrayList<>();
        invocation.add(safeCommand);
        invocation.addAll(safeArgs);

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(invocation);
            builder.directory(repositoryRoot.toFile());
            builder.redirectErrorStream(true);
            process = builder.start();
            Process runningProcess = process;
            CompletableFuture<OutputCapture> outputFuture = CompletableFuture.supplyAsync(
                    () -> readLimitedOutput(runningProcess));

            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("源码查询超过5秒，已终止");
            }
            OutputCapture output = outputFuture.get(1, TimeUnit.SECONDS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("repository", repository);
            result.put("command", safeCommand);
            result.put("args", safeArgs);
            result.put("exitCode", process.exitValue());
            result.put("truncated", output.truncated());
            result.put("output", output.text());
            return result;
        } catch (TimeoutException e) {
            throw new IllegalStateException("读取源码查询结果超时", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("源码查询被中断", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException("执行源码查询失败：" + e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private Path validateRepository(String repository) {
        Path root = repositories.get(repository);
        if (root == null) {
            throw new IllegalArgumentException("不允许访问仓库：" + repository);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("源码仓库未挂载或目录不存在：" + repository);
        }
        return root;
    }

    private String validateCommand(String command) {
        if (command == null || !ALLOWED_COMMANDS.contains(command)) {
            throw new IllegalArgumentException("不允许执行命令：" + command);
        }
        return command;
    }

    private List<String> validateArgs(Path root, String command, List<String> args) {
        if (args.size() > MAX_ARGS) {
            throw new IllegalArgumentException("命令参数不能超过" + MAX_ARGS + "个");
        }
        List<String> result = new ArrayList<>(args.size());
        for (String arg : args) {
            if (arg == null || arg.length() > MAX_ARG_LENGTH || arg.indexOf('\0') >= 0
                    || arg.contains("\n") || arg.contains("\r")) {
                throw new IllegalArgumentException("命令参数为空、过长或包含非法字符");
            }
            validatePathBoundary(root, arg);
            validateSensitivePath(arg);
            result.add(arg);
        }
        validateCommandOptions(command, result);
        return List.copyOf(result);
    }

    private void validatePathBoundary(Path root, String arg) {
        String normalizedSeparators = arg.replace('\\', '/');
        if (normalizedSeparators.startsWith("/")
                || normalizedSeparators.matches("^[A-Za-z]:.*")
                || List.of(normalizedSeparators.split("/")).contains("..")) {
            throw new IllegalArgumentException("禁止绝对路径和上级目录路径：" + arg);
        }
        Path candidate = root.resolve(arg).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("路径超出仓库范围：" + arg);
        }
        if (Files.exists(candidate)) {
            try {
                if (!candidate.toRealPath().startsWith(root.toRealPath())) {
                    throw new IllegalArgumentException("路径通过符号链接超出仓库范围：" + arg);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("无法校验路径：" + arg, e);
            }
        }
    }

    private void validateSensitivePath(String arg) {
        String lower = arg.replace('\\', '/').toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (SENSITIVE_NAMES.contains(fileName)
                || fileName.endsWith(".pem") || fileName.endsWith(".key")
                || fileName.endsWith(".p12") || fileName.endsWith(".pfx")
                || fileName.endsWith(".jks")) {
            throw new IllegalArgumentException("禁止读取敏感文件：" + arg);
        }
    }

    private void validateCommandOptions(String command, List<String> args) {
        if ("rg".equals(command) && args.stream().anyMatch(arg ->
                arg.equals("--pre") || arg.startsWith("--pre=") || arg.equals("--pre-glob")
                        || arg.equals("--follow") || arg.equals("-L") || arg.equals("--hidden")
                        || arg.equals("--no-ignore") || arg.matches("^-[^-]*u.*"))) {
            throw new IllegalArgumentException("rg 禁止预处理器、隐藏文件、忽略规则绕过和跟随符号链接选项");
        }
        if ("sed".equals(command)) {
            int expressionIndex = !args.isEmpty() && "-n".equals(args.get(0)) ? 1 : 0;
            if (args.size() <= expressionIndex || !args.get(expressionIndex).matches("^\\d+(,\\d+)?p$")) {
                throw new IllegalArgumentException("sed 只允许读取行范围，例如 -n 60,90p src/main/java/Example.java");
            }
        }
        if ("tail".equals(command) && args.stream().anyMatch(arg ->
                arg.equals("-f") || arg.startsWith("--follow") || arg.matches("^-[^-]*f.*"))) {
            throw new IllegalArgumentException("tail 禁止持续跟踪选项 -f");
        }
    }

    private OutputCapture readLimitedOutput(Process process) {
        StringBuilder output = new StringBuilder();
        int lines = 0;
        boolean truncated = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lines >= MAX_OUTPUT_LINES || output.length() + line.length() + 1 > MAX_OUTPUT_CHARS) {
                    truncated = true;
                    continue;
                }
                output.append(line).append('\n');
                lines++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取源码查询输出失败", e);
        }
        return new OutputCapture(output.toString(), truncated);
    }

    private record OutputCapture(String text, boolean truncated) {}
}
