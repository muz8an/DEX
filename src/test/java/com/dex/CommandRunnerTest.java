package com.dex;

import com.dex.tools.CommandRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CommandRunnerTest {

    @Test
    public void testBlockedDangerousTokens() {
        String res1 = CommandRunner.runCommand("mvn test && del file.txt");
        assertTrue(res1.contains("Security Error: Execution blocked"));

        String res2 = CommandRunner.runCommand("git status | grep test");
        assertTrue(res2.contains("Security Error: Execution blocked"));

        String res3 = CommandRunner.runCommand("python app.py ; del *.*");
        assertTrue(res3.contains("Security Error: Execution blocked"));

        String res4 = CommandRunner.runCommand("javac Main.java > output.txt");
        assertTrue(res4.contains("Security Error: Execution blocked"));
    }

    @Test
    public void testBlockedUnapprovedExecutables() {
        String res1 = CommandRunner.runCommand("powershell -Command Get-Process");
        assertTrue(res1.contains("Security Error"));

        String res2 = CommandRunner.runCommand("del test.txt");
        assertTrue(res2.contains("Security Error"));

        String res3 = CommandRunner.runCommand("calc.exe");
        assertTrue(res3.contains("Security Error: Executable 'calc.exe' is not in the approved build tools allowlist"));
    }

    @Test
    public void testGitSubcommandRestrictions() {
        String blockedPush = CommandRunner.runCommand("git push origin main");
        assertTrue(blockedPush.contains("Git subcommand 'push' is restricted"));

        String blockedCommit = CommandRunner.runCommand("git commit -m test");
        assertTrue(blockedCommit.contains("Git subcommand 'commit' is restricted"));
    }

    @Test
    public void testAllowedToolExecution() {
        // git status or git --version should run cleanly
        String gitResult = CommandRunner.runCommand("git status");
        assertNotNull(gitResult);
        assertTrue(gitResult.contains("=== Command Report ==="));
        assertTrue(gitResult.contains("Command: git status"));
    }

    @Test
    public void testOutputDrainingWithoutDeadlock() {
        String logResult = CommandRunner.runCommand("git log");
        assertNotNull(logResult);
        assertTrue(logResult.contains("=== Command Report ==="));
        assertTrue(logResult.contains("Command: git log"));
    }
}
