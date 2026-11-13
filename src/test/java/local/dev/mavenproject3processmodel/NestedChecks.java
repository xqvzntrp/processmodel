package local.dev.mavenproject3processmodel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.function.Consumer;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;
import local.dev.mavenproject3processmodel.visualization.ProcessModelHtml;

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
        persistenceAndHtml();
        return checks;
    }
    private static List<String> diagrams(String html) {
        var result = new ArrayList<String>();
        String marker = "<pre class=\"mermaid\">\n";
        int offset = 0;
        while ((offset = html.indexOf(marker, offset)) >= 0) {
            offset += marker.length();
            int end = html.indexOf("</pre>", offset);
            result.add(html.substring(offset, end));
            offset = end;
        }
        return result;
    }
    private static void persistenceAndHtml() throws Exception {
        var codec = new ProcessModelXml();
        var view = new ProcessModelHtml();
        Path dir = Files.createTempDirectory("nested-scopes-");
        try {
            Path file = dir.resolve("nested.process.xml");
            Path output = dir.resolve("nested.html");
            for (int depth : new int[]{2, 64}) {
                var model = fixture(depth).build();
                codec.write(model, file);
                check(codec.read(file).equals(model), "nested file round trip depth " + depth);
                check(codec.parse(codec.toXml(model)).equals(model), "nested in-memory XML round trip depth " + depth);
                check(diagrams(view.export(file)).size() == depth + 1, "one diagram per scope depth " + depth);
            }
            var f = fixture(3);
            var model = f.build();
            codec.write(model, file);
            byte[] original = Files.readAllBytes(file);
            String html = view.export(file);
            var graphs = diagrams(html);
            check(graphs.size() == 4, "main plus three nested subprocess diagrams");
            check(graphs.get(0).contains("[[&quot;Subprocess#58; sub0&quot;]]"), "subprocess uses a labeled subroutine box");
            check(!graphs.get(0).contains("leaf") && !graphs.get(1).contains("leaf") && graphs.get(3).contains("leaf"), "children appear only in their direct scope");
            check(html.indexOf("<h2>Subprocess: sub0") < html.indexOf("<h2>Subprocess: sub1")
                && html.indexOf("<h2>Subprocess: sub1") < html.indexOf("<h2>Subprocess: sub2"), "parent-first diagram order");
            check(html.contains("ID: sub0; Parent: Main process") && html.contains("ID: sub2; Parent: sub1 (sub1)"), "scope context visible");
            long nodes = 0;
            long flows = 0;
            for (String graph : graphs) {
                var declared = new java.util.HashSet<String>();
                var endpoints = new java.util.HashSet<String>();
                for (String line : graph.lines().toList()) {
                    if (line.contains(" --&gt;")) {
                        var match = java.util.regex.Pattern.compile("  (n[0-9]+) --&gt;(?:\\|.*?\\|)? (n[0-9]+)").matcher(line);
                        check(match.matches(), "scope-local edge syntax");
                        endpoints.add(match.group(1)); endpoints.add(match.group(2)); flows++;
                    } else if (line.startsWith("  n")) {
                        var match = java.util.regex.Pattern.compile("  (n[0-9]+).*").matcher(line);
                        if (!match.matches()) throw new AssertionError("Missing node ID");
                        declared.add(match.group(1)); nodes++;
                    }
                }
                check(declared.containsAll(endpoints), "every edge endpoint is declared in its own diagram");
            }
            check(nodes == model.elements().size() && flows == model.relationships().size(), "every supported node and flow appears in exactly one scope diagram");
            check(html.equals(view.export(file)), "deterministic nested HTML");
            check(Arrays.equals(original, Files.readAllBytes(file)), "nested export preserves source bytes");
            Collections.reverse(f.elements); codec.write(f.build(), file);
            String reversed = view.export(file);
            check(reversed.indexOf("<h2>Subprocess: sub0") < reversed.indexOf("<h2>Subprocess: sub1"), "display hierarchy independent of record order");
            var first = f.elements.stream().filter(e -> e.elementId().equals("sub0")).findFirst().orElseThrow();
            f.elements.set(f.elements.indexOf(first), new ProcessElement(first.elementId(), first.processId(), first.elementType(), "</h2><script>é & \"</script>", "", first.parentSubprocessId()));
            codec.write(f.build(), file);
            String escaped = view.export(file);
            check(escaped.contains("&lt;/h2&gt;&lt;script&gt;é &amp; &quot;&lt;/script&gt;"), "scope headings and parent descriptions escaped");
            check(escaped.split("<script", -1).length == 2, "subprocess names cannot inject scripts");
            for (String bad : List.of(
                codec.toXml(model).replace("<parentSubprocessId>sub1</parentSubprocessId>", "<parentSubprocessId>missing</parentSubprocessId>"),
                codec.toXml(model).replace("<elementId>sub0</elementId>", "<elementId>sub0</elementId><parentSubprocessId>sub0</parentSubprocessId>"))) {
                Files.writeString(file, bad); Files.writeString(output, "keep HTML");
                try { view.write(file, output); throw new AssertionError("Accepted invalid hierarchy"); }
                catch (IllegalArgumentException expected) {
                    check(expected.getMessage().contains("UNKNOWN_PARENT") || expected.getMessage().contains("SUBPROCESS_CYCLE"), "XML load enforces hierarchy");
                }
                check(Files.readString(output).equals("keep HTML"), "invalid hierarchy preserves existing HTML");
            }
        } finally {
            try (var files = Files.list(dir)) { for (Path path : files.toList()) Files.delete(path); }
            Files.delete(dir);
        }
    }
}
