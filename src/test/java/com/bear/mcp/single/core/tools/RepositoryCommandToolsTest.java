package com.bear.mcp.single.core.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RepositoryCommandToolsTest {
    @TempDir
    Path repository;

    @Test
    void rejectsUnknownRepositoryAndCommand() {
        RepositoryCommandTools tools = new RepositoryCommandTools(repository.toString());
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("other", "rg", List.of("text", ".")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "sh", List.of("-c", "whoami")));
    }

    @Test
    void rejectsTraversalSensitiveFilesAndMutatingOptions() {
        RepositoryCommandTools tools = new RepositoryCommandTools(repository.toString());
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "head", List.of("../secret.txt")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "head", List.of(".env")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "sed", List.of("-i", "s/a/b/", "a.txt")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "sed", List.of("-n", "e cat /etc/passwd", "a.txt")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "tail", List.of("-f", "app.log")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "rg", List.of("--pre", "cat", "text")));
        assertThrows(IllegalArgumentException.class,
                () -> tools.runRepositoryCommand("yanque-platform", "rg", List.of("--hidden", "password", ".")));
    }
}
