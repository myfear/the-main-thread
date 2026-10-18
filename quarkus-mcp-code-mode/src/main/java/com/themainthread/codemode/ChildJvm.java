package com.themainthread.codemode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class ChildJvm {
    private ChildJvm() {
    }

    static List<String> command(String mainClass, String... args) throws IOException {
        String classpath = System.getProperty("codemode.worker.classpath");
        if (classpath == null) {
            String dependencies = Files.readString(Path.of(System.getProperty("codemode.dependencies",
                    "target/runtime-classpath.txt"))).trim();
            String classes = Path.of(System.getProperty("codemode.classes", "target/classes")).toAbsolutePath().toString();
            String tests = Path.of("target/test-classes").toAbsolutePath().toString();
            classpath = classes + File.pathSeparator + tests + File.pathSeparator + dependencies;
        }
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx128m", "-XX:MaxMetaspaceSize=96m", "-XX:MaxDirectMemorySize=16m",
                "-XX:+ExitOnOutOfMemoryError", "-Dfile.encoding=UTF-8", "-cp",
                classpath, mainClass));
        command.addAll(List.of(args));
        return List.copyOf(command);
    }

    static Process start(List<String> command, Path directory) throws IOException {
        var builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        return builder.start();
    }

    static void stop(Process process) {
        if (process == null) {
            return;
        }
        // Capture descendants while their parent is still alive, before they can be reparented.
        List<ProcessHandle> descendants = List.of();
        boolean treeInspectionFailed = false;
        try {
            descendants = process.descendants().toList();
        } catch (RuntimeException e) {
            // A restricted macOS sandbox can deny sysctl. Still kill our direct child.
            treeInspectionFailed = true;
        }
        descendants.reversed().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                throw new CodeModeFailure("CLEANUP_FAILED", "Child process did not exit after termination.");
            }
            for (var descendant : descendants) {
                try {
                    descendant.onExit().get(2, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                    throw new CodeModeFailure("CLEANUP_FAILED", "A child descendant did not terminate.");
                }
            }
            process.getInputStream().close();
            process.getOutputStream().close();
            process.getErrorStream().close();
            if (treeInspectionFailed) {
                throw new CodeModeFailure("CLEANUP_FAILED", "Process-tree inspection was denied by the operating system.");
            }
        } catch (InterruptedException e) {
            interrupted = true;
            throw new CodeModeFailure("CLEANUP_FAILED", "Interrupted while reaping a child process.");
        } catch (IOException e) {
            throw new CodeModeFailure("CLEANUP_FAILED", "Could not close child process pipes.");
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
