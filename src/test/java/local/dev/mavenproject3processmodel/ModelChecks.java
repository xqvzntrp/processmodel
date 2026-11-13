package local.dev.mavenproject3processmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.validation.*;

final class ModelChecks {
    private static int checks;
    private ModelChecks() { }
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    static final class Fixture {
        List<ProcessDefinition> processes = new ArrayList<>(List.of(new ProcessDefinition("p", "Example", null)));
        List<ProcessElement> elements = new ArrayList<>(List.of(
            new ProcessElement("s", "p", ElementType.START_EVENT, "Start", null, null),
            new ProcessElement("t", "p", ElementType.USER_TASK, "Review", null, null),
            new ProcessElement("e", "p", ElementType.END_EVENT, "Done", null, null)));
        List<ProcessRelationship> flows = new ArrayList<>(List.of(
            new ProcessRelationship("f1", RelationshipType.SEQUENCE_FLOW, "s", "t", "", null, false),
            new ProcessRelationship("f2", RelationshipType.SEQUENCE_FLOW, "t", "e", null, "", false)));
        List<ProcessInput> inputs = new ArrayList<>(List.of(new ProcessInput("p", "request", InputDataType.STRING, true)));
        List<PerformerDefinition> performers = new ArrayList<>(List.of(new PerformerDefinition("worker", "Reviewer", PerformerType.HUMAN_ROLE, "Role")));
        List<ActivityAssignment> assignments = new ArrayList<>(List.of(new ActivityAssignment("t", "worker")));
        List<ProcessDataItem> data = new ArrayList<>(List.of(new ProcessDataItem("d", "p", "Request", null)));
        List<ActivityDataAssociation> associations = new ArrayList<>(List.of(new ActivityDataAssociation("t", "d", DataDirection.INPUT)));
        List<ProcessOpenQuestion> questions = new ArrayList<>(List.of(new ProcessOpenQuestion("q", "p", null, "Who is the backup reviewer?")));
        ProcessModel build() { return new ProcessModel(processes, elements, flows, inputs, performers, assignments, data, associations, questions); }
    }
    private static void rejects(String code, Consumer<Fixture> change) {
        var fixture = new Fixture(); change.accept(fixture);
        var result = new ProcessModelValidator().validate(fixture.build());
        check(!result.valid() && result.diagnostics().stream().anyMatch(d -> d.code().equals(code)
            && d.severity() == Diagnostic.Severity.ERROR), "Expected structural error " + code + ": " + result);
    }
    static int run() {
        var validator = new ProcessModelValidator();
        var fixture = new Fixture();
        var model = fixture.build();
        var result = validator.validate(model);
        check(result.valid(), "open question is valid incomplete knowledge");
        check(result.diagnostics().size() == 1 && result.diagnostics().getFirst().code().equals("OPEN_QUESTION"), "question warning");
        check(result.equals(validator.validate(model)), "deterministic diagnostics");
        fixture.elements.clear();
        check(model.elements().size() == 3, "aggregate copies lists");
        try { model.elements().clear(); throw new AssertionError("Mutable aggregate"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { result.diagnostics().clear(); throw new AssertionError("Mutable diagnostics"); }
        catch (UnsupportedOperationException expected) { checks++; }
        check(!validator.validate(null).valid(), "null model diagnostic");
        rejects("PROCESS_COUNT", f -> f.processes.clear());
        rejects("PROCESS_COUNT", f -> f.processes.add(new ProcessDefinition("other", "Other", null)));
        rejects("DUPLICATE_ID", f -> f.data.add(new ProcessDataItem("t", "p", "Conflict", null)));
        rejects("INVALID_ID", f -> f.elements.add(new ProcessElement("bad:id", "p", ElementType.TASK, "Bad", null, null)));
        rejects("INVALID_ID", f -> f.elements.add(new ProcessElement(null, "p", ElementType.TASK, "Missing ID", null, null)));
        rejects("UNKNOWN_PROCESS", f -> f.elements.add(new ProcessElement("orphan", "absent", ElementType.TASK, "Orphan", null, null)));
        rejects("ELEMENT_TYPE_REQUIRED", f -> f.elements.add(new ProcessElement("unknown", "p", null, "Unknown", null, null)));
        rejects("SUBPROCESS_UNSUPPORTED", f -> f.elements.add(new ProcessElement("sub", "p", ElementType.SUB_PROCESS, "Subprocess", null, null)));
        rejects("SUBPROCESS_UNSUPPORTED", f -> f.elements.add(new ProcessElement("child", "p", ElementType.TASK, "Child", null, "t")));
        for (var type : List.of(RelationshipType.MESSAGE_FLOW, RelationshipType.ASSOCIATION))
            rejects("RELATIONSHIP_UNSUPPORTED", f -> f.flows.add(new ProcessRelationship("other", type, "s", "t", "", null, false)));
        rejects("UNKNOWN_SOURCE", f -> f.flows.add(new ProcessRelationship("bad", RelationshipType.SEQUENCE_FLOW, "absent", "t", "", null, false)));
        rejects("UNKNOWN_TARGET", f -> f.flows.add(new ProcessRelationship("bad", RelationshipType.SEQUENCE_FLOW, "t", "absent", "", null, false)));
        rejects("EVENT_DIRECTION", f -> f.flows.add(new ProcessRelationship("back", RelationshipType.SEQUENCE_FLOW, "e", "s", "", null, false)));
        rejects("EXCLUSIVE_FLOW_REQUIRED", f -> f.flows.add(new ProcessRelationship("condition", RelationshipType.SEQUENCE_FLOW, "t", "e", "", "${ready}", false)));
        rejects("EXCLUSIVE_FLOW_REQUIRED", f -> f.flows.add(new ProcessRelationship("default", RelationshipType.SEQUENCE_FLOW, "t", "e", "", null, true)));
        rejects("DEFAULT_HAS_CONDITION", f -> {
            f.elements.add(new ProcessElement("g", "p", ElementType.EXCLUSIVE_GATEWAY, "Decision", null, null));
            f.flows.add(new ProcessRelationship("bad", RelationshipType.SEQUENCE_FLOW, "g", "e", "", "x", true));
        });
        rejects("MULTIPLE_DEFAULTS", f -> {
            f.elements.add(new ProcessElement("g", "p", ElementType.EXCLUSIVE_GATEWAY, "Decision", null, null));
            f.flows.add(new ProcessRelationship("a", RelationshipType.SEQUENCE_FLOW, "g", "t", "", null, true));
            f.flows.add(new ProcessRelationship("b", RelationshipType.SEQUENCE_FLOW, "g", "e", "", null, true));
        });
        rejects("INPUT_NAME_REQUIRED", f -> f.inputs.add(new ProcessInput("p", "", InputDataType.DATE, false)));
        rejects("INPUT_TYPE_REQUIRED", f -> f.inputs.add(new ProcessInput("p", "date", null, false)));
        rejects("DUPLICATE_INPUT", f -> f.inputs.add(f.inputs.getFirst()));
        rejects("PERFORMER_TYPE_REQUIRED", f -> f.performers.add(new PerformerDefinition("unknown", "Unknown", null, null)));
        rejects("UNKNOWN_PERFORMER", f -> f.assignments.add(new ActivityAssignment("t", "unknown")));
        rejects("ACTIVITY_REQUIRED", f -> f.assignments.add(new ActivityAssignment("s", "worker")));
        rejects("UNKNOWN_ELEMENT", f -> f.assignments.add(new ActivityAssignment("unknown", "worker")));
        rejects("DUPLICATE_ASSIGNMENT", f -> f.assignments.add(f.assignments.getFirst()));
        rejects("UNKNOWN_DATA_ITEM", f -> f.associations.add(new ActivityDataAssociation("t", "unknown", DataDirection.OUTPUT)));
        rejects("DATA_DIRECTION_REQUIRED", f -> f.associations.add(new ActivityDataAssociation("t", "d", null)));
        rejects("ACTIVITY_REQUIRED", f -> f.associations.add(new ActivityDataAssociation("e", "d", DataDirection.OUTPUT)));
        rejects("DUPLICATE_DATA_ASSOCIATION", f -> f.associations.add(f.associations.getFirst()));
        rejects("UNKNOWN_ELEMENT", f -> f.questions.add(new ProcessOpenQuestion("badQ", "p", "absent", "Where?")));
        rejects("QUESTION_TEXT_REQUIRED", f -> f.questions.add(new ProcessOpenQuestion("blankQ", "p", "t", "")));
        rejects("INVALID_XML_TEXT", f -> f.data.add(new ProcessDataItem("badData", "p", "Bad\u0000", null)));
        rejects("INVALID_XML_TEXT", f -> f.data.add(new ProcessDataItem("badData", "p", "Bad\uD800", null)));
        rejects("EVENTS_REQUIRED", f -> f.elements.removeFirst());
        var named = new Fixture();
        named.performers.add(new PerformerDefinition("unnamed", null, PerformerType.SYSTEM, null));
        check(validator.validate(named.build()).valid(), "missing names are warnings");
        var cyclic = new Fixture();
        cyclic.flows.add(new ProcessRelationship("loop", RelationshipType.SEQUENCE_FLOW, "t", "t", "Again", null, false));
        cyclic.elements.add(new ProcessElement("parallel", "p", ElementType.PARALLEL_GATEWAY, "Parallel", null, null));
        check(validator.validate(cyclic.build()).valid(), "cycles and disconnected parallel gateway supported");
        return checks;
    }
}
