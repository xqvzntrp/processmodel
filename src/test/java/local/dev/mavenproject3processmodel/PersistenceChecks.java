package local.dev.mavenproject3processmodel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;

final class PersistenceChecks {
    private static int checks;
    private PersistenceChecks() { }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void rejects(String xml, String message) {
        try { new ProcessModelXml().parse(xml); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(message), "Expected diagnostic " + message + ", got " + expected.getMessage());
            return;
        }
        throw new AssertionError("Accepted invalid XML");
    }
    static int run() throws Exception {
        var codec = new ProcessModelXml();
        var fixture = new ModelChecks.Fixture();
        String hostile = "\"<>& [test] é 😀\nnew line\rcarriage\tTAB";
        fixture.processes.set(0, new ProcessDefinition("p", hostile, ""));
        for (var type : InputDataType.values()) fixture.inputs.add(new ProcessInput("p", type.name(), type, false));
        fixture.performers.add(new PerformerDefinition("sys", hostile, PerformerType.SYSTEM, null));
        fixture.elements.add(new ProcessElement("generic", "p", ElementType.TASK, hostile, "", null));
        fixture.elements.add(new ProcessElement("service", "p", ElementType.SERVICE_TASK, "Service", null, null));
        fixture.elements.add(new ProcessElement("parallel", "p", ElementType.PARALLEL_GATEWAY, "Fork", null, null));
        fixture.elements.add(new ProcessElement("decision", "p", ElementType.EXCLUSIVE_GATEWAY, "Decision", null, null));
        fixture.flows.add(new ProcessRelationship("condition", RelationshipType.SEQUENCE_FLOW, "decision", "generic", "yes", "${x < 1 && ready}", false));
        fixture.flows.add(new ProcessRelationship("default", RelationshipType.SEQUENCE_FLOW, "decision", "service", "otherwise", null, true));
        fixture.associations.add(new ActivityDataAssociation("generic", "d", DataDirection.OUTPUT));
        fixture.questions.add(new ProcessOpenQuestion("elementQ", "p", "generic", hostile));
        var model = fixture.build();
        String xml = codec.toXml(model);
        check(model.equals(codec.parse(xml)), "complete nine-collection round trip including null/empty, enums, flags and whitespace");
        check(xml.equals(codec.toXml(model)), "deterministic XML");
        check(xml.contains("&amp;") && xml.contains("&lt;") && xml.contains("&#13;"), "XML escaping including CR");
        check(xml.contains("😀"), "UTF-8 Unicode retained");
        String prefixed = xml.replace("xmlns=", "xmlns:p=").replaceAll("<(/?)([A-Za-z][A-Za-z0-9]*)", "<$1p:$2");
        check(codec.parse(prefixed).equals(model), "namespace-aware alternate prefixes");
        check(codec.parse(xml.replace("<processes>", "<!-- comment --><?note ignored?><processes>")).equals(model), "comments and processing instructions ignored");
        rejects("<broken>", "Invalid XML");
        rejects(xml.replace("version=\"1\"", "version=\"2\""), "version");
        rejects(xml.replace(" version=\"1\"", ""), "version");
        rejects(xml.replace(ProcessModelXml.NAMESPACE, "urn:foreign"), "namespace");
        rejects(xml.replace("<processes>", "<unknown/>\n<processes>"), "Unknown collection");
        rejects(xml.replace("<processes>", "<processes extra='true'>"), "Unknown attribute");
        rejects(xml.replace("processDefinition", "arbitraryClass"), "Expected processDefinition");
        rejects(xml.replace("<processId>p</processId>", "<processId>p</processId><surprise>data</surprise>"), "Unknown field");
        rejects(xml.replace("<processId>p</processId>", "<processId>p</processId><processId>p</processId>"), "Duplicate field");
        rejects(xml.replace("<processId>p</processId>", "<processId><nested/></processId>"), "Nested content");
        rejects(xml.replace("<processes>", "<processes>unexpected"), "Unexpected text");
        rejects(xml.replace("<processes>", "<processes xmlns='urn:foreign'>"), "namespace");
        rejects(xml.replace("</processModel>", "<inputs/></processModel>"), "Duplicate collection");
        rejects(xml.replaceAll("(?s)\\s*<openQuestions>.*?</openQuestions>", ""), "nine model collections");
        rejects(xml.replace("<elementType>USER_TASK</elementType>", "<elementType>MAGIC</elementType>"), "Unknown elementType");
        rejects(xml.replace("<isRequired>true</isRequired>", "<isRequired>yes</isRequired>"), "boolean");
        rejects(xml.replace("<isRequired>true</isRequired>", ""), "boolean");
        rejects(xml.replace("<targetElementId>t</targetElementId>", "<targetElementId>absent</targetElementId>"), "UNKNOWN_TARGET");
        rejects(xml.replace("<dataType>STRING</dataType>", ""), "INPUT_TYPE_REQUIRED");
        rejects(xml.replace("<processId>p</processId>", "<processId></processId>"), "Invalid processDefinition");
        for (String doctype : new String[]{"<!DOCTYPE processModel>",
            "<!DOCTYPE processModel SYSTEM 'http://127.0.0.1:9/evil.dtd'>",
            "<!DOCTYPE processModel [<!ENTITY ext SYSTEM 'file:///etc/passwd'>]>",
            "<!DOCTYPE processModel [<!ENTITY % ext SYSTEM 'http://127.0.0.1:9/evil.dtd'>%ext;]>",
            "<!DOCTYPE processModel [<!ENTITY a 'boom'><!ENTITY b '&a;&a;'>]>"})
            rejects(doctype + xml.substring(xml.indexOf("?>") + 2), "DOCTYPE");
        rejects(xml.replace("<processes>", "<xi:include xmlns:xi='http://www.w3.org/2001/XInclude' href='file:///etc/passwd'/><processes>"), "namespace");
        rejects(xml.replace("version=\"1\"", "version='1' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='urn:test http://127.0.0.1:9/schema'"), "Unknown attribute");
        Path temp = Files.createTempDirectory("process-model-xml-");
        try {
            Path file = temp.resolve("model.xml");
            codec.write(model, file);
            check(codec.read(file).equals(model), "file-based model round trip");
            check(Arrays.equals(Files.readAllBytes(file), xml.getBytes(StandardCharsets.UTF_8)), "UTF-8 file bytes");
            Files.write(file, xml.replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"").getBytes(StandardCharsets.UTF_16));
            check(codec.read(file).equals(model), "XML byte-encoding declaration honored");
            Files.writeString(file, "existing");
            var invalid = new ModelChecks.Fixture(); invalid.flows.clear(); invalid.elements.clear();
            try { codec.write(invalid.build(), file); throw new AssertionError("Saved invalid model"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(Files.readString(file).equals("existing"), "validation failure preserves existing output");
            try { codec.read(temp.resolve("missing.xml")); throw new AssertionError("Read missing file"); }
            catch (java.io.IOException expected) { checks++; }
        } finally {
            try (var files = Files.list(temp)) { for (Path path : files.toList()) Files.delete(path); }
            Files.delete(temp);
        }
        return checks;
    }
}
