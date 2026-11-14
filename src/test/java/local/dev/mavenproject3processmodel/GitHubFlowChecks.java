package local.dev.mavenproject3processmodel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import local.dev.mavenproject3processmodel.examples.GitHubFlowExample;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;

final class GitHubFlowChecks {
    private static int checks;
    private GitHubFlowChecks() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static Set<String> reached(ProcessModel model, Set<String> seeds, boolean reverse) {
        var visited = new HashSet<>(seeds);
        var pending = new ArrayDeque<>(seeds);
        while (!pending.isEmpty()) {
            String node = pending.remove();
            for (var flow : model.relationships()) {
                if ((reverse ? flow.targetElementId() : flow.sourceElementId()).equals(node)) {
                    String next = reverse ? flow.sourceElementId() : flow.targetElementId();
                    if (visited.add(next)) pending.add(next);
                }
            }
        }
        return visited;
    }
    private record Run(int status, String console) { }
    private static Run launch(Path cwd, String... args) throws Exception {
        var command = new ArrayList<String>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp", Path.of("target/classes").toAbsolutePath().toString(), GitHubFlowExample.class.getName()));
        command.addAll(Arrays.asList(args));
        var process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
        String console = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(process.waitFor(), console);
    }
    static int run() throws Exception {
        var model = GitHubFlowExample.model();
        var validation = new ProcessModelValidator().validate(model);
        check(validation.valid(), "GitHub Flow adaptation is structurally valid");
        check(validation.diagnostics().size() == 4 && validation.diagnostics().stream().allMatch(d -> d.code().equals("OPEN_QUESTION")), "four explicit policy questions, no unspecified branches");
        check(model.elements().size() == 18 && model.relationships().size() == 19, "adaptation graph size");
        check(model.inputs().size() == 4 && model.inputs().stream().filter(ProcessInput::isRequired).count() == 3, "required context and optional issue");
        check(model.performers().size() == 4 && model.assignments().size() == 12, "roles and system assigned to all twelve activities");
        check(model.dataItems().size() == 8 && model.dataAssociations().size() == 31, "richer data model");
        var types = model.elements().stream().collect(Collectors.toMap(ProcessElement::elementId, ProcessElement::elementType));
        check(types.get("checks") == ElementType.SERVICE_TASK && types.get("review") == ElementType.USER_TASK, "automated checks distinct from human review");
        check(types.get("fork") == ElementType.PARALLEL_GATEWAY && types.get("join") == ElementType.PARALLEL_GATEWAY, "explicit parallel coordination");
        check(model.relationships().stream().filter(f -> f.sourceElementId().equals("fork")).map(ProcessRelationship::targetElementId).collect(Collectors.toSet())
            .equals(Set.of("checks", "review")), "fork starts checks and review");
        check(model.relationships().stream().filter(f -> f.targetElementId().equals("join")).map(ProcessRelationship::sourceElementId).collect(Collectors.toSet())
            .equals(Set.of("checks", "review")), "join collects completed results, including failures");
        check(model.relationships().stream().anyMatch(f -> f.sourceElementId().equals("revise") && f.targetElementId().equals("fork")), "revision repeats checks and review");
        check(model.relationships().stream().filter(f -> f.targetElementId().equals("openPr")).count() == 1, "revision updates existing PR instead of opening another");
        var decision = model.relationships().stream().filter(f -> f.sourceElementId().equals("decision")).toList();
        check(decision.stream().map(ProcessRelationship::conditionExpression).collect(Collectors.toSet()).equals(Set.of(
            "mergeDecision == 'ready'", "mergeDecision == 'revise'", "mergeDecision == 'close'")), "three descriptive outcomes");
        check(decision.stream().noneMatch(ProcessRelationship::isDefault), "no implicit default approval");
        check(model.relationships().stream().filter(f -> f.targetElementId().equals("merge")).allMatch(f -> f.sourceElementId().equals("decision")), "merge follows readiness decision");
        check(model.relationships().stream().anyMatch(f -> f.sourceElementId().equals("merge") && f.targetElementId().equals("cleanup"))
            && model.relationships().stream().anyMatch(f -> f.sourceElementId().equals("cleanup") && f.targetElementId().equals("merged")), "successful path includes post-merge cleanup");
        check(model.relationships().stream().anyMatch(f -> f.sourceElementId().equals("closePr") && f.targetElementId().equals("closed")), "separate closure ending");
        var ids = types.keySet();
        check(reached(model, Set.of("proposed"), false).equals(ids), "every example node is reachable");
        check(reached(model, Set.of("merged", "closed"), true).equals(ids), "every example node has a structural path to an ending");
        var activityIds = model.elements().stream().filter(e -> e.elementType().category() == ElementCategory.ACTIVITY).map(ProcessElement::elementId).collect(Collectors.toSet());
        check(model.assignments().stream().map(ActivityAssignment::elementId).collect(Collectors.toSet()).equals(activityIds), "every activity has an explicit performer");
        check(model.dataAssociations().containsAll(List.of(
            new ActivityDataAssociation("assess", "checkResults", DataDirection.INPUT),
            new ActivityDataAssociation("assess", "reviewFeedback", DataDirection.INPUT),
            new ActivityDataAssociation("revise", "commitSet", DataDirection.OUTPUT))), "assessment and revision data dependencies");
        var codec = new ProcessModelXml();
        Path example = Path.of("examples/github-flow.process.xml");
        check(codec.read(example).equals(model), "committed XML matches Java specification");
        check(Files.readString(example).equals(codec.toXml(model)), "committed XML is reproducible");
        Path dir = Files.createTempDirectory("github-flow-example-");
        try {
            Files.createDirectory(dir.resolve("target"));
            Run generated = launch(dir);
            check(generated.status() == 0 && generated.console().contains("Structurally valid; warnings: 4"), "no-argument example launcher works");
            Path xml = dir.resolve("target/github-flow.process.xml");
            check(codec.read(xml).equals(model), "launcher XML round trip");
            check(!Files.exists(dir.resolve("target/github-flow.html")), "launcher produces XML without HTML");
            check(!Files.exists(dir.resolve(".git")), "example creates no Git repository");
            Path custom = dir.resolve("custom é.process.xml");
            check(launch(dir, custom.toString()).status() == 0 && codec.read(custom).equals(model), "custom Unicode output path");
            check(launch(dir, "one", "two").status() == 2, "invalid argument count");
            check(launch(dir, "missing/out.process.xml").status() == 1, "file error exit status");

        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        return checks;
    }
}
