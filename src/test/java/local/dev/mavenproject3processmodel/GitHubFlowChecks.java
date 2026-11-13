package local.dev.mavenproject3processmodel;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import local.dev.mavenproject3processmodel.examples.GitHubFlowExample;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;

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
        return checks;
    }
}
