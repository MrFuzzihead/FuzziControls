package com.mrfuzzihead.fuzzicontrols.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.mrfuzzihead.fuzzicontrols.FuzziControls;

/**
 * Collects in-game assertion results, writes a machine-readable report next to the run directory,
 * and exits the JVM with the failure count.
 *
 * <p>
 * The report is a small text file ({@code gametest-report.txt}) because the log can be interleaved
 * with Forge/FML output; CI can assert on the file in addition to the markers. Format:
 * </p>
 *
 * <pre>
 * SIDE client
 * ok   mixin: GuiScreenAccessors -&gt; GuiScreen
 * FAIL config mapping loaded
 * SUMMARY 2 1
 * FUZZICONTROLS_GAMETEST_FAILED
 * </pre>
 */
public final class GameTestReport {

    private final String side;

    private final List<String> lines = new ArrayList<>();

    private int checks;

    private int failures;

    public GameTestReport(String side) {
        this.side = side;
        lines.add("SIDE " + side);
    }

    /** Records one assertion and prints it immediately so a hung run is diagnosable. */
    public void check(String description, boolean passed) {
        checks++;
        if (!passed) failures++;
        String line = (passed ? "ok   " : "FAIL ") + description;
        lines.add(line);
        // [FC-GAMETEST] is intentionally short; the CI grep uses the marker lines below.
        System.out.println("[FC-GAMETEST] " + line);
    }

    /** @return how many assertions were executed */
    public int checkCount() {
        return checks;
    }

    /** @return how many assertions failed */
    public int failureCount() {
        return failures;
    }

    /** Writes the report, prints the summary/marker, and exits the JVM. Never returns. */
    public void finish() {
        String summary = "[FC-GAMETEST] " + (checks - failures) + "/" + checks + " checks passed (" + side + ")";
        System.out.println(summary);
        lines.add("SUMMARY " + checks + " " + failures);
        lines.add(summary);
        lines.add(failures == 0 ? GameTestMod.OK_MARKER : GameTestMod.FAIL_MARKER);
        writeReport();

        if (failures == 0) {
            System.out.println(GameTestMod.OK_MARKER);
        } else {
            FuzziControls.LOG.error("[FuzziControls] Game test: {} assertion(s) failed.", failures);
            System.out.println(GameTestMod.FAIL_MARKER + ": " + failures + " assertion(s) failed, see above");
        }
        System.out.flush();
        GameTestMod.exit(failures == 0 ? 0 : 1);
    }

    /** Writes the report to the current working directory (the Gradle run directory). */
    private void writeReport() {
        Path path = Paths.get("gametest-report.txt");
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
            System.out.println("[FC-GAMETEST] report written to " + path.toAbsolutePath());
        } catch (IOException e) {
            // A missing report must not hide a passing run, but make it visible.
            FuzziControls.LOG.warn("[FuzziControls] Could not write game-test report: {}", e.toString());
        }
    }
}
