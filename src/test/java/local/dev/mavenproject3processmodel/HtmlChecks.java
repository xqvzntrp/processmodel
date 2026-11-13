package local.dev.mavenproject3processmodel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;
import local.dev.mavenproject3processmodel.visualization.ProcessModelHtml;

final class HtmlChecks {
    private static int checks;
    private HtmlChecks() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String diagnostic) throws Exception {
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(diagnostic), "diagnostic: " + expected.getMessage());
            return;
        }
        throw new AssertionError("Expected rejection");
    }
    static int run() throws Exception {
        var exporter = new ProcessModelHtml();
        var codec = new ProcessModelXml();
        Path example = Path.of("examples/onboarding.process.xml");
        byte[] before = Files.readAllBytes(example);
        String onboarding = exporter.export(example);
        check(onboarding.equals(exporter.export(example)), "deterministic HTML");
        check(Arrays.equals(before, Files.readAllBytes(example)), "export preserves saved XML");
        for (String section : List.of("Process", "Elements", "Relationships", "Inputs", "Performers", "Assignments", "Data items", "Data associations", "Open questions", "Validation warnings"))
            check(onboarding.contains("<h2>" + section + "</h2>"), "section: " + section);
        check(onboarding.contains("qStartDate") && onboarding.contains("qIdentity") && onboarding.contains("OPEN_QUESTION"), "questions and warnings visible");
        check(onboarding.contains("identitySystem") && onboarding.contains("accountData") && onboarding.contains("startDate"), "rich model information retained in tables");
        check(!onboarding.contains("<input") && !onboarding.contains("<form") && !onboarding.contains("<button")
            && !onboarding.contains("contenteditable"), "read-only basic view");
        check(onboarding.contains("mermaid@12.3.0") && onboarding.contains("securityLevel: 'strict'") && onboarding.contains("htmlLabels: false"), "renderer configuration");
        check(ProcessModelHtml.companionPath(Path.of("demo.process.xml")).equals(Path.of("demo.html")), "process XML companion path");
        check(ProcessModelHtml.companionPath(Path.of("demo.xml")).equals(Path.of("demo.html")), "XML companion path");
        check(ProcessModelHtml.companionPath(Path.of("demo.txt")).equals(Path.of("demo.txt.html")), "other extension companion path");
        rejects(() -> ProcessModelHtml.companionPath(Path.of("/")), "must name a file");
        Path dir = Files.createTempDirectory("process-html-");
        try {
            var fixture = new ModelChecks.Fixture();
            String hostile = "</pre><script>alert(1)</script>\"&[]|\n é 😀";
            fixture.processes.set(0, new ProcessDefinition("p", hostile, ""));
            fixture.elements.add(new ProcessElement("end", "p", ElementType.TASK, null, hostile, null));
            fixture.elements.add(new ProcessElement("n0", "p", ElementType.SERVICE_TASK, hostile, null, null));
            fixture.elements.add(new ProcessElement("a-b", "p", ElementType.EXCLUSIVE_GATEWAY, "Choice", null, null));
            fixture.elements.add(new ProcessElement("a.b", "p", ElementType.PARALLEL_GATEWAY, "Fork", null, null));
            fixture.flows.add(new ProcessRelationship("loop", RelationshipType.SEQUENCE_FLOW, "n0", "n0", hostile, null, false));
            fixture.flows.add(new ProcessRelationship("yes", RelationshipType.SEQUENCE_FLOW, "a-b", "t", "yes", "${ready}", false));
            fixture.flows.add(new ProcessRelationship("otherwise", RelationshipType.SEQUENCE_FLOW, "a-b", "end", "", null, true));
            Path input = dir.resolve("source.process.xml");
            Path output = dir.resolve("view.html");
            codec.write(fixture.build(), input);
            byte[] source = Files.readAllBytes(input);
            String html = exporter.export(input);
            String graph = html.substring(html.indexOf("flowchart TD"), html.indexOf("</pre>"));
            check(graph.lines().count() == 1 + fixture.elements.size() + fixture.flows.size(), "all nodes and flows emitted including disconnected nodes and cycles");
            check(graph.contains("n3[&quot;end&quot;]") && graph.contains("n0((&quot;Start&quot;))"), "safe generated IDs and event/task shapes");
            check(graph.contains("User#58; Review") && graph.contains("Service#58; "), "user/service task distinction");
            check(graph.contains("n5{&quot;X#58; Choice&quot;}") && graph.contains("n6{&quot;#43;#58; Fork&quot;}"), "gateway markers and diamonds");
            check(graph.contains("n4 --&gt;|&quot;") && graph.contains("| n4"), "cycle flow and escaped label");
            check(graph.contains("n5 --&gt;|&quot;default&quot;| n3"), "default flow marked");
            check(graph.contains("#60;#47;pre#62;") && graph.contains("#34;#38;#91;#93;#124;#10; #233; #128512;"), "Mermaid-sensitive text encoded");
            check(html.contains("&lt;/pre&gt;&lt;script&gt;alert(1)&lt;/script&gt;&quot;&amp;[]|"), "HTML text escaped");
            check(html.split("<script", -1).length == 2, "user text cannot inject scripts");
            check(html.contains("${ready}") && html.contains("MISSING_NAME"), "conditions and warnings visible as text");
            exporter.write(input, output);
            check(Files.readString(output).equals(html), "file export matches text export");
            check(Arrays.equals(source, Files.readAllBytes(input)), "file export preserves source bytes");
            Files.writeString(input, Files.readString(input).replace("<name>Choice</name>", "<name>Changed on disk</name>"));
            check(exporter.export(input).contains("Changed on disk"), "HTML uses saved file contents");
            rejects(() -> exporter.write(input, input), "different files");
            Path symlink = dir.resolve("alias.html"); Files.createSymbolicLink(symlink, input);
            rejects(() -> exporter.write(input, symlink), "different files");
            Path hardlink = dir.resolve("hard.html"); Files.createLink(hardlink, input);
            rejects(() -> exporter.write(input, hardlink), "different files");
            for (String invalid : List.of("<broken>", "<!DOCTYPE processModel SYSTEM 'http://127.0.0.1:9/evil.dtd'><processModel/>",
                    codec.toXml(fixture.build()).replace("<targetElementId>t</targetElementId>", "<targetElementId>missing</targetElementId>"))) {
                Files.writeString(input, invalid);
                Files.writeString(output, "keep HTML");
                rejects(() -> exporter.write(input, output), invalid.startsWith("<!DOCTYPE") ? "DOCTYPE" : invalid.startsWith("<broken") ? "Invalid XML" : "UNKNOWN_TARGET");
                check(Files.readString(output).equals("keep HTML"), "invalid source preserves existing output");
                check(Files.readString(input).equals(invalid), "invalid source unchanged");
            }
            var minimal = new ModelChecks.Fixture();
            minimal.questions.clear(); minimal.inputs.clear(); minimal.performers.clear();
            minimal.assignments.clear(); minimal.data.clear(); minimal.associations.clear();
            codec.write(minimal.build(), input);
            check(exporter.export(input).contains("<h2>Validation warnings</h2>\n<p>None.</p>"), "empty collections handled plainly");
        } finally {
            try (var files = Files.list(dir)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(dir);
        }
        return checks;
    }
}
