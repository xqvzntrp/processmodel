package local.dev.mavenproject3processmodel.examples;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;
import local.dev.mavenproject3processmodel.visualization.ProcessModelHtml;

/** Embedded validation contains an embedded build subprocess. No repository actions execute. */
public final class NestedChangeExample {
    private NestedChangeExample() { }
    public static ProcessModel model() {
        var base = GitHubFlowExample.model();
        var elements = new ArrayList<ProcessElement>();
        var insideValidation = Set.of("fork", "checks", "review", "join", "assess");
        for (var element : base.elements()) {
            String id = element.elementId();
            elements.add(new ProcessElement(id, element.processId(), id.equals("checks") ? ElementType.SUB_PROCESS : element.elementType(),
                id.equals("checks") ? "Verify build" : element.name(),
                id.equals("checks") ? "Embedded build validation produces a completed result, including failures." : element.description(),
                insideValidation.contains(id) ? "validateChange" : null));
        }
        elements.add(node("validateChange", ElementType.SUB_PROCESS, "Validate proposed change", "Coordinate build verification and review; return a merge assessment.", null));
        elements.add(node("validationStart", ElementType.START_EVENT, "Validation requested", "Begin this revision's validation.", "validateChange"));
        elements.add(node("validationEnd", ElementType.END_EVENT, "Assessment available", "Return the assessment to the parent scope.", "validateChange"));
        elements.add(node("buildStart", ElementType.START_EVENT, "Build requested", "Begin build verification.", "checks"));
        elements.add(node("compile", ElementType.SERVICE_TASK, "Compile changes", "Describe compilation and its report; no compiler is invoked.", "checks"));
        elements.add(node("compileOutcome", ElementType.EXCLUSIVE_GATEWAY, "Compilation result", "Choose a descriptive passed/failed outcome.", "checks"));
        elements.add(node("runTests", ElementType.SERVICE_TASK, "Run tests", "Describe testing of a successful compilation.", "checks"));
        elements.add(node("recordChecks", ElementType.SERVICE_TASK, "Record check results", "Record a complete result; when compilation fails, tests are recorded as skipped.", "checks"));
        elements.add(node("buildEnd", ElementType.END_EVENT, "Build results available", "A completed result may include failure; completion does not imply success.", "checks"));
        var flows = new ArrayList<ProcessRelationship>();
        for (var flow : base.relationships()) {
            String target = switch (flow.relationshipId()) {
                case "gf06", "gf14" -> "validateChange";
                case "gf12" -> "validationEnd";
                default -> flow.targetElementId();
            };
            flows.add(new ProcessRelationship(flow.relationshipId(), flow.relationshipType(), flow.sourceElementId(), target,
                flow.name(), flow.conditionExpression(), flow.isDefault()));
        }
        flows.add(flow("nestedReturn", "validateChange", "decision", "Assessment ready", null));
        flows.add(flow("nestedEntry", "validationStart", "fork", "", null));
        flows.add(flow("build01", "buildStart", "compile", "", null));
        flows.add(flow("build02", "compile", "compileOutcome", "", null));
        flows.add(flow("buildPassed", "compileOutcome", "runTests", "Passed", "compileResult == 'passed'"));
        flows.add(flow("buildFailed", "compileOutcome", "recordChecks", "Failed; tests skipped", "compileResult == 'failed'"));
        flows.add(flow("build03", "runTests", "recordChecks", "Test results", null));
        flows.add(flow("build04", "recordChecks", "buildEnd", "", null));
        var assignments = new ArrayList<>(base.assignments());
        assignments.addAll(List.of(new ActivityAssignment("validateChange", "maintainerRole"),
            new ActivityAssignment("compile", "ciSystem"), new ActivityAssignment("runTests", "ciSystem"), new ActivityAssignment("recordChecks", "ciSystem")));
        var items = new ArrayList<>(base.dataItems());
        items.add(new ProcessDataItem("buildReport", "githubFlow", "Compilation report", "Compilation outcome and diagnostics."));
        items.add(new ProcessDataItem("testReport", "githubFlow", "Test report", "Results when tests ran; absence is interpreted as skipped after compilation failure."));
        var data = new ArrayList<>(base.dataAssociations());
        data.addAll(List.of(data("validateChange", "commitSet", DataDirection.INPUT), data("validateChange", "mergeAssessment", DataDirection.OUTPUT),
            data("compile", "commitSet", DataDirection.INPUT), data("compile", "buildReport", DataDirection.OUTPUT),
            data("runTests", "buildReport", DataDirection.INPUT), data("runTests", "testReport", DataDirection.OUTPUT),
            data("recordChecks", "buildReport", DataDirection.INPUT), data("recordChecks", "testReport", DataDirection.INPUT),
            data("recordChecks", "checkResults", DataDirection.OUTPUT)));
        var questions = new ArrayList<>(base.openQuestions());
        questions.add(new ProcessOpenQuestion("qCancelledBuild", "githubFlow", "recordChecks", "How should cancelled or unavailable checks become a completed result?"));
        return new ProcessModel(List.of(new ProcessDefinition("githubFlow", "Software change with nested validation",
            "An embedded validation subprocess contains an embedded build subprocess. Adapted from the flat GitHub Flow example.")),
            elements, flows, base.inputs(), base.performers(), assignments, items, data, questions);
    }
    public static void main(String[] args) {
        if (args.length > 1) { System.err.println("Usage: NestedChangeExample [OUTPUT.process.xml]"); System.exit(2); return; }
        try {
            Path output = Path.of(args.length == 0 ? "target/nested-change.process.xml" : args[0]);
            Path html = ProcessModelHtml.companionPath(output);
            ProcessModelHtml.requireDifferent(output, html);
            var codec = new ProcessModelXml();
            var model = model();
            codec.write(model, output);
            var saved = codec.read(output);
            if (!saved.equals(model)) throw new IllegalStateException("Nested model round trip differs");
            new ProcessModelHtml().write(output, html);
            System.out.println("Saved and reloaded " + output);
            System.out.println("Saved HTML " + html);
            System.out.println("Elements: " + saved.elements().size() + "; relationships: " + saved.relationships().size());
            var validation = new ProcessModelValidator().validate(saved);
            System.out.println("Structurally valid; warnings: " + validation.diagnostics().size());
            for (var diagnostic : validation.diagnostics()) System.out.println(diagnostic.severity() + " " + diagnostic.code()
                + " [" + diagnostic.entityId() + "]: " + diagnostic.message());
        } catch (IOException | IllegalArgumentException ex) {
            String message = ex.getMessage();
            System.err.println("Nested example failed: " + (message == null ? ex.getClass().getSimpleName() : message.replace('\n', ' ').replace('\r', ' ')));
            System.exit(1);
        }
    }
    private static ProcessElement node(String id, ElementType type, String name, String description, String parent) {
        return new ProcessElement(id, "githubFlow", type, name, description, parent);
    }
    private static ProcessRelationship flow(String id, String source, String target, String name, String condition) {
        return new ProcessRelationship(id, RelationshipType.SEQUENCE_FLOW, source, target, name, condition, false);
    }
    private static ActivityDataAssociation data(String activity, String item, DataDirection direction) {
        return new ActivityDataAssociation(activity, item, direction);
    }
}
