package local.dev.mavenproject3processmodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;

final class NestedChecks {
    private static int checks;
    private NestedChecks() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    static ModelChecks.Fixture fixture(int depth) {
        var f = new ModelChecks.Fixture();
        for (int i = 0; i < depth; i++) {
            String scope = "sub" + i;
            f.elements.add(node(scope, ElementType.SUB_PROCESS, i == 0 ? null : "sub" + (i - 1)));
            f.elements.add(node("start" + i, ElementType.START_EVENT, scope));
            f.elements.add(node("end" + i, ElementType.END_EVENT, scope));
            String body = i + 1 == depth ? "leaf" : "sub" + (i + 1);
            f.flows.add(flow("in" + i, "start" + i, body));
            f.flows.add(flow("out" + i, body, "end" + i));
        }
        f.elements.add(node("leaf", ElementType.TASK, "sub" + (depth - 1)));
        f.flows.set(0, flow("f1", "s", "sub0"));
        f.flows.add(flow("afterSub", "sub0", "t"));
        return f;
    }
    private static ProcessElement node(String id, ElementType type, String parent) {
        return new ProcessElement(id, "p", type, id, null, parent);
    }
    private static ProcessRelationship flow(String id, String source, String target) {
        return new ProcessRelationship(id, RelationshipType.SEQUENCE_FLOW, source, target, "", null, false);
    }
    private static void replaceParent(ModelChecks.Fixture f, String id, String parent) {
        for (int i = 0; i < f.elements.size(); i++) {
            var e = f.elements.get(i);
            if (e.elementId().equals(id)) f.elements.set(i, new ProcessElement(e.elementId(), e.processId(), e.elementType(), e.name(), e.description(), parent));
        }
    }
    private static void rejects(String code, Consumer<ModelChecks.Fixture> mutation) {
        var f = fixture(2); mutation.accept(f);
        var result = new ProcessModelValidator().validate(f.build());
        check(!result.valid() && result.diagnostics().stream().anyMatch(d -> d.code().equals(code)), "Expected " + code + ": " + result);
    }
    static int run() throws Exception {
        var validator = new ProcessModelValidator();
        for (int depth : new int[]{1, 2, 8, 256})
            check(validator.validate(fixture(depth).build()).valid(), "supported nesting depth " + depth);
        var reversed = fixture(3); Collections.reverse(reversed.elements);
        check(validator.validate(reversed.build()).valid(), "parents need not precede children in record order");
        var valid = fixture(2);
        valid.assignments.add(new ActivityAssignment("sub0", "worker"));
        valid.assignments.add(new ActivityAssignment("leaf", "worker"));
        valid.associations.add(new ActivityDataAssociation("sub1", "d", DataDirection.INPUT));
        valid.associations.add(new ActivityDataAssociation("leaf", "d", DataDirection.OUTPUT));
        valid.questions.add(new ProcessOpenQuestion("nestedQ", "p", "sub1", "Which checks belong here?"));
        check(validator.validate(valid.build()).valid(), "subprocess activities retain metadata and process-owned shared data");
        check(validator.validate(valid.build()).equals(validator.validate(valid.build())), "deterministic nested diagnostics");
        var multiEnd = fixture(2); multiEnd.elements.add(node("otherEnd", ElementType.END_EVENT, "sub1"));
        check(validator.validate(multiEnd.build()).valid(), "multiple direct subprocess endings supported");
        rejects("UNKNOWN_PARENT", f -> replaceParent(f, "leaf", "missing"));
        rejects("UNKNOWN_PARENT", f -> replaceParent(f, "leaf", ""));
        rejects("INVALID_PARENT_TYPE", f -> replaceParent(f, "leaf", "t"));
        rejects("PARENT_PROCESS_MISMATCH", f -> f.elements.add(new ProcessElement("foreignChild", "other", ElementType.TASK, "Foreign", null, "sub0")));
        rejects("SUBPROCESS_CYCLE", f -> replaceParent(f, "sub0", "sub0"));
        rejects("SUBPROCESS_CYCLE", f -> replaceParent(f, "sub0", "sub1"));
        rejects("SUBPROCESS_CYCLE", f -> {
            f.elements.add(node("sub2", ElementType.SUB_PROCESS, "sub1"));
            replaceParent(f, "sub0", "sub2");
        });
        rejects("CROSS_SCOPE_FLOW", f -> f.flows.add(flow("enterBody", "t", "leaf")));
        rejects("CROSS_SCOPE_FLOW", f -> f.flows.add(flow("exitBody", "leaf", "t")));
        rejects("CROSS_SCOPE_FLOW", f -> f.flows.add(flow("siblingScope", "sub1", "leaf")));
        rejects("CROSS_SCOPE_FLOW", f -> f.flows.add(flow("toAncestor", "leaf", "sub0")));
        rejects("SUBPROCESS_EVENTS_REQUIRED", f -> f.elements.removeIf(e -> e.elementId().equals("start0")));
        rejects("SUBPROCESS_EVENTS_REQUIRED", f -> f.elements.removeIf(e -> e.elementId().equals("end1")));
        rejects("SUBPROCESS_EVENTS_REQUIRED", f -> f.elements.add(node("secondStart", ElementType.START_EVENT, "sub1")));
        rejects("SUBPROCESS_EVENTS_REQUIRED", f -> {
            f.elements.removeIf(e -> e.elementId().equals("start0") || e.elementId().equals("end0"));
        });
        rejects("EVENTS_REQUIRED", f -> replaceParent(f, "s", "sub0"));
        rejects("EVENTS_REQUIRED", f -> replaceParent(f, "e", "sub0"));
        rejects("EVENT_DIRECTION", f -> f.flows.add(flow("backToStart", "leaf", "start1")));
        rejects("EVENT_DIRECTION", f -> f.flows.add(flow("fromEnd", "end1", "leaf")));
        rejects("DUPLICATE_ID", f -> f.elements.add(node("sub0", ElementType.SUB_PROCESS, null)));
        return checks;
    }
}
