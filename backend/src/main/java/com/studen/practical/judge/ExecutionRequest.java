package com.studen.practical.judge;

import com.studen.practical.CodingLanguage;
import java.nio.file.Path;
import java.util.UUID;

/**
 * One compile-or-run call's inputs. {@code workspaceDir} is a per-execution host temp directory
 * (created and destroyed by {@code com.studen.practical.execution.ExecutionOrchestrator}) that gets
 * bind-mounted into the container -- never a path derived from student input, always
 * server-generated ({@code Files.createTempDirectory}).
 */
public record ExecutionRequest(UUID executionId, CodingLanguage language, String sourceCode, Path workspaceDir) {
}
