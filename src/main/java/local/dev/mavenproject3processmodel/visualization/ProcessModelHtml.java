package local.dev.mavenproject3processmodel.visualization;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;

/** Basic read-only HTML projection. Always parses a saved process-model XML file. */
public final class ProcessModelHtml {
    public static Path companionPath(Path xml) {
        if (xml.getFileName() == null) throw new IllegalArgumentException("XML output must name a file");
        String name = xml.getFileName().toString();
        if (name.endsWith(".process.xml")) name = name.substring(0, name.length() - 12);
        else if (name.endsWith(".xml")) name = name.substring(0, name.length() - 4);
        return xml.resolveSibling(name + ".html");
    }
    public String export(Path input) throws IOException {
        ProcessModel model = new ProcessModelXml().read(input);
        var diagnostics = new ProcessModelValidator().validate(model).diagnostics();
        var process = model.processes().getFirst();
        var html = new StringBuilder("""
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
            """);
        html.append("  <title>").append(escape(name(process.name(), process.processId()))).append("</title>\n");
        html.append("""
              <style>
                table { border-collapse: collapse; }
                th, td { border: 1px solid #aaa; padding: 4px; text-align: left; vertical-align: top; white-space: pre-wrap; }
              </style>
            </head>
            <body>
            """);
        html.append("<h1>").append(escape(name(process.name(), process.processId()))).append("</h1>\n");
        html.append("<p>Read-only view of the saved process model. Conditions are descriptive text; no activities are executed.</p>\n");
        html.append("<h2>Flowchart</h2>\n");
        html.append("<p>User and service tasks are marked in their labels. Arrows show control flow. The diagram loads Mermaid from a CDN and requires internet access; its source remains visible if the library cannot load.</p>\n");
        html.append("<pre class=\"mermaid\">\n").append(escape(mermaid(model, null))).append("</pre>\n");
        subprocessDiagrams(html, model);
        table(html, "Validation warnings", List.of("Severity", "Code", "Entity", "Message"), diagnostics);
        table(html, "Process", List.of("ID", "Name", "Description"), model.processes());
        table(html, "Elements", List.of("ID", "Process", "Type", "Name", "Description", "Parent subprocess"), model.elements());
        table(html, "Relationships", List.of("ID", "Type", "From", "To", "Label", "Condition", "Default"), model.relationships());
        table(html, "Inputs", List.of("Process", "Name", "Data type", "Required"), model.inputs());
        table(html, "Performers", List.of("ID", "Name", "Type", "Description"), model.performers());
        table(html, "Assignments", List.of("Activity", "Performer"), model.assignments());
        table(html, "Data items", List.of("ID", "Process", "Name", "Description"), model.dataItems());
        table(html, "Data associations", List.of("Activity", "Data item", "Direction"), model.dataAssociations());
        table(html, "Open questions", List.of("ID", "Process", "Element (blank means process-level)", "Question"), model.openQuestions());
        html.append("""
            <script type="module">
              import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@12.3.0/dist/mermaid.esm.min.mjs';
              mermaid.initialize({ startOnLoad: true, securityLevel: 'strict', flowchart: { htmlLabels: false } });
            </script>
            </body>
            </html>
            """);
        return html.toString();
    }
    public void write(Path input, Path output) throws IOException {
        requireDifferent(input, output);
        String html = export(input); // Complete parsing and conversion before opening output.
        Files.writeString(output, html, StandardCharsets.UTF_8);
    }
    public static void requireDifferent(Path input, Path output) throws IOException {
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || Files.exists(input) && Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("XML input and HTML output must identify different files");
    }
    private static void subprocessDiagrams(StringBuilder html, ProcessModel model) {
        var byParent = new LinkedHashMap<String, List<ProcessElement>>();
        var byId = new LinkedHashMap<String, ProcessElement>();
        for (var element : model.elements()) {
            byId.put(element.elementId(), element);
            if (element.elementType() == ElementType.SUB_PROCESS)
                byParent.computeIfAbsent(element.parentSubprocessId(), key -> new ArrayList<>()).add(element);
        }
        var pending = new ArrayDeque<ProcessElement>();
        var roots = byParent.getOrDefault(null, List.of());
        for (int i = roots.size() - 1; i >= 0; i--) pending.push(roots.get(i));
        // Parent-before-child traversal without Java recursion, independent of record order.
        while (!pending.isEmpty()) {
            var scope = pending.pop();
            var parent = byId.get(scope.parentSubprocessId());
            String context = parent == null ? "Main process" : name(parent.name(), parent.elementId()) + " (" + parent.elementId() + ")";
            html.append("<h2>Subprocess: ").append(escape(name(scope.name(), scope.elementId()))).append("</h2>\n");
            html.append("<p>ID: ").append(escape(scope.elementId())).append("; Parent: ").append(escape(context)).append("</p>\n");
            html.append("<pre class=\"mermaid\">\n").append(escape(mermaid(model, scope.elementId()))).append("</pre>\n");
            var children = byParent.getOrDefault(scope.elementId(), List.of());
            for (int i = children.size() - 1; i >= 0; i--) pending.push(children.get(i));
        }
    }
    private static String mermaid(ProcessModel model, String parentId) {
        var ids = new LinkedHashMap<String, String>();
        var text = new StringBuilder("flowchart TD\n");
        for (var node : model.elements()) {
            if (!Objects.equals(node.parentSubprocessId(), parentId)) continue;
            String id = "n" + ids.size(); ids.put(node.elementId(), id);
            String label = name(node.name(), node.elementId());
            String open;
            String close;
            switch (node.elementType()) {
                case START_EVENT, END_EVENT -> { open = "((\""; close = "\"))"; }
                case SUB_PROCESS -> { open = "[[\""; close = "\"]]"; label = "Subprocess: " + label; }
                case TASK, USER_TASK, SERVICE_TASK -> {
                    open = "[\""; close = "\"]";
                    if (node.elementType() == ElementType.USER_TASK) label = "User: " + label;
                    if (node.elementType() == ElementType.SERVICE_TASK) label = "Service: " + label;
                }
                case EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY -> {
                    open = "{\""; close = "\"}";
                    label = (node.elementType() == ElementType.EXCLUSIVE_GATEWAY ? "X: " : "+: ") + label;
                }
                default -> throw new IllegalArgumentException("Unsupported element type: " + node.elementType());
            }
            text.append("  ").append(id).append(open).append(label(label)).append(close).append('\n');
        }
        for (var flow : model.relationships()) {
            if (!ids.containsKey(flow.sourceElementId())) continue;
            text.append("  ").append(ids.get(flow.sourceElementId())).append(" -->");
            String caption = flow.name();
            if (flow.isDefault()) caption = caption == null || caption.isEmpty() ? "default" : caption + " (default)";
            if (caption != null && !caption.isEmpty()) text.append("|\"").append(label(caption)).append("\"|");
            text.append(' ').append(ids.get(flow.targetElementId())).append('\n');
        }
        return text.toString();
    }
    private static void table(StringBuilder html, String title, List<String> headings, List<? extends Record> rows) {
        html.append("<h2>").append(escape(title)).append("</h2>\n");
        if (rows.isEmpty()) { html.append("<p>None.</p>\n"); return; }
        html.append("<table>\n<thead><tr>");
        for (String heading : headings) html.append("<th>").append(escape(heading)).append("</th>");
        html.append("</tr></thead>\n<tbody>\n");
        for (Record row : rows) {
            var components = row.getClass().getRecordComponents();
            if (components.length != headings.size()) throw new IllegalStateException("Table headings differ from model record");
            html.append("<tr>");
            for (var component : components) {
                try {
                    Object value = component.getAccessor().invoke(row);
                    String text = value == null ? "" : value instanceof Boolean flag ? (flag ? "yes" : "no") : value.toString();
                    html.append("<td>").append(escape(text)).append("</td>");
                } catch (IllegalAccessException | InvocationTargetException ex) {
                    throw new IllegalStateException("Cannot read model field for HTML", ex);
                }
            }
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");
    }
    private static String name(String text, String fallback) { return text == null || text.isBlank() ? fallback : text; }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static String label(String text) {
        var output = new StringBuilder();
        text.codePoints().forEach(c -> {
            if (c == ' ' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') output.appendCodePoint(c);
            else output.append('#').append(c).append(';');
        });
        return output.toString();
    }
}
