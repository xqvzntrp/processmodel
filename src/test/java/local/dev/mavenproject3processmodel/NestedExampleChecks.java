package local.dev.mavenproject3processmodel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import local.dev.mavenproject3processmodel.examples.NestedChangeExample;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;
import local.dev.mavenproject3processmodel.visualization.ProcessModelHtml;

final class NestedExampleChecks {
    private static int checks;
    private NestedExampleChecks() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    static int run() throws Exception {
        var model = NestedChangeExample.model();
        var result = new ProcessModelValidator().validate(model);
        check(result.valid() && result.diagnostics().size() == 5, "nested worked example is valid with five questions");
        check(model.elements().size() == 27 && model.relationships().size() == 27, "nested example graph size");
        var byId = model.elements().stream().collect(Collectors.toMap(ProcessElement::elementId, e -> e));
        check(byId.get("validateChange").elementType() == ElementType.SUB_PROCESS && byId.get("validateChange").parentSubprocessId() == null, "outer subprocess belongs to main scope");
        check(byId.get("checks").elementType() == ElementType.SUB_PROCESS && byId.get("checks").parentSubprocessId().equals("validateChange"), "build subprocess belongs to outer validation scope");
        check(byId.get("compile").parentSubprocessId().equals("checks") && byId.get("runTests").parentSubprocessId().equals("checks"), "build activities belong to inner scope");
        check(model.relationships().stream().anyMatch(f -> f.sourceElementId().equals("revise") && f.targetElementId().equals("validateChange")), "revision reenters the outer subprocess node");
        check(model.relationships().stream().anyMatch(f -> f.sourceElementId().equals("compileOutcome") && f.targetElementId().equals("recordChecks") && f.conditionExpression().equals("compileResult == 'failed'")), "compilation failure skips testing and still records a result");
        check(model.assignments().stream().map(ActivityAssignment::elementId).collect(Collectors.toSet()).equals(model.elements().stream()
            .filter(e -> e.elementType().category() == ElementCategory.ACTIVITY).map(ProcessElement::elementId).collect(Collectors.toSet())), "all subprocess and ordinary activities have explicit performers");
        check(model.dataItems().size() == 10 && model.dataAssociations().size() == 40, "nested example retains shared data and adds build reports");
        var codec = new ProcessModelXml();
        Path source = Path.of("examples/nested-change.process.xml");
        check(codec.read(source).equals(model) && codec.toXml(model).equals(Files.readString(source)), "nested example XML is reproducible");
        byte[] before = Files.readAllBytes(source);
        String html = new ProcessModelHtml().export(source);
        check(html.equals(Files.readString(Path.of("examples/nested-change.html"))), "nested HTML fixture comes from saved XML");
        check(html.split("<pre class=\\\"mermaid\\\">", -1).length == 4, "main, validation and build diagrams");
        String main = html.substring(html.indexOf("<pre class=\"mermaid\">"), html.indexOf("</pre>"));
        check(main.contains("Subprocess#58; Validate proposed change") && !main.contains("Compile changes"), "main diagram collapses the internal steps");
        check(Arrays.equals(before, Files.readAllBytes(source)), "nested visualization preserves source XML");
        Path dir = Files.createTempDirectory("nested-example-");
        try {
            Files.createDirectory(dir.resolve("target"));
            var cli = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                Path.of("target/classes").toAbsolutePath().toString(), NestedChangeExample.class.getName())
                .directory(dir.toFile()).redirectErrorStream(true).start();
            String console = new String(cli.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check(cli.waitFor() == 0 && console.contains("Structurally valid; warnings: 5"), "no-argument nested example launcher");
            Path saved = dir.resolve("target/nested-change.process.xml");
            check(codec.read(saved).equals(model), "launcher XML round trip");
            check(Files.readString(dir.resolve("target/nested-change.html")).equals(new ProcessModelHtml().export(saved)), "launcher generates matching scoped HTML");
        } finally {
            try (var files = Files.walk(dir)) { for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        return checks;
    }
}
