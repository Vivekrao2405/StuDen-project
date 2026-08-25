package com.studen.executionserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reaps workspace directories nobody has touched in a while -- the only cleanup signal is time
 * (this app is never told "the submission is done"), so an abandoned or crashed sequence of
 * compile()/run() calls never leaks disk space indefinitely.
 */
@Component
public class WorkspaceCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceCleanupTask.class);
    private static final String ACTIVITY_MARKER = ".last-activity";

    private final ExecutionServerProperties properties;

    public WorkspaceCleanupTask(ExecutionServerProperties properties) {
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "PT60S")
    public void sweep() {
        Path root = Path.of(properties.getWorkspaceContainerPath());
        if (!Files.isDirectory(root)) {
            return;
        }
        long ttlMillis = properties.getWorkspaceTtlSeconds() * 1000L;
        long now = System.currentTimeMillis();

        try (var children = Files.list(root)) {
            children.filter(Files::isDirectory).forEach(dir -> {
                if (isExpired(dir, now, ttlMillis)) {
                    deleteRecursively(dir);
                    log.info("Reaped expired workspace {}", dir.getFileName());
                }
            });
        } catch (IOException e) {
            log.warn("Workspace cleanup sweep failed: {}", e.getMessage());
        }
    }

    private boolean isExpired(Path dir, long now, long ttlMillis) {
        Path marker = dir.resolve(ACTIVITY_MARKER);
        try {
            long lastActivity = Files.exists(marker)
                    ? Long.parseLong(Files.readString(marker, StandardCharsets.UTF_8).trim())
                    : Files.getLastModifiedTime(dir).toMillis();
            return now - lastActivity > ttlMillis;
        } catch (Exception e) {
            // Unreadable/corrupt marker -- treat conservatively as expired so it doesn't stick around forever.
            return true;
        }
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort -- next sweep will retry.
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete workspace {}: {}", dir, e.getMessage());
        }
    }
}
