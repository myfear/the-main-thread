package com.themainthread.city;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@TopCommand
@Command(name = "software-city", version = "Software City 0.1.0", mixinStandardHelpOptions = true,
        description = "Explore committed Java source without executing or modifying its repository.",
        subcommands = SoftwareCity.Analyze.class)
public class SoftwareCity implements Runnable {
    @Spec
    CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    @Dependent
    @Unremovable
    @Command(name = "analyze", mixinStandardHelpOptions = true,
            description = "Export a schema-valid snapshot of a locally available Git commit.")
    public static class Analyze implements Callable<Integer> {
        private final GitSnapshotAnalyzer analyzer;
        private final CityJson json;

        @Spec
        CommandSpec spec;
        @Option(names = "--repo", required = true, description = "Existing local Git repository.")
        Path repo;
        @Option(names = "--ref", defaultValue = "HEAD", description = "Local commit/ref (default: HEAD).")
        String ref;
        @Option(names = "--out", required = true, description = "JSON destination outside the analyzed repository.")
        Path output;
        @Option(names = "--include", description = "Source glob; repeatable. Replaces the default source pattern.")
        List<String> includes = new ArrayList<>();
        @Option(names = "--exclude", description = "Exclude glob; repeatable. Exclusions win.")
        List<String> excludes = new ArrayList<>();
        @Option(names = "--strict", description = "Fail without exporting if any source cannot be analyzed.")
        boolean strict;
        @Option(names = "--verbose", description = "Print informational diagnostics and elapsed time to stderr.")
        boolean verbose;

        public Analyze(GitSnapshotAnalyzer analyzer, CityJson json) {
            this.analyzer = analyzer;
            this.json = json;
        }

        @Override
        public Integer call() {
            long start = System.nanoTime();
            try {
                var document = analyzer.analyze(repo, ref, output, includes, excludes, strict);
                json.write(document, output);
                for (var diagnostic : document.diagnostics()) {
                    if (verbose || !diagnostic.severity().equals("info")) {
                        spec.commandLine().getErr().printf("%s %s %s%s: %s%n", diagnostic.severity(),
                                diagnostic.code(), diagnostic.path(),
                                diagnostic.line() == null ? "" : ":" + diagnostic.line(),
                                diagnostic.message());
                    }
                }
                spec.commandLine().getOut().printf("Exported %d types at %s%s%n", document.types().size(),
                        document.revisions().head().commit(),
                        document.summary().incomplete() ? " (INCOMPLETE; see diagnostics)" : "");
                if (verbose) {
                    spec.commandLine().getErr().printf("Analysis and export: %.3f s%n", (System.nanoTime() - start) / 1e9);
                }
                return 0;
            } catch (IllegalArgumentException e) {
                spec.commandLine().getErr().println("Invalid argument: " + e.getMessage());
                return CommandLine.ExitCode.USAGE;
            } catch (IOException e) {
                spec.commandLine().getErr().println("Analysis failed: " + e.getMessage());
                return 1;
            }
        }
    }
}
