package local.dev.mavenproject3processmodel.persistence;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import local.dev.mavenproject3processmodel.model.*;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Versioned project XML, not BPMN. Only the registered record types can be loaded. */
public final class ProcessModelXml {
    public static final String NAMESPACE = "urn:local.dev:processmodel:1";
    public static final String VERSION = "1";
    private static final Map<String, Class<? extends Record>> TYPES;
    static {
        var types = new LinkedHashMap<String, Class<? extends Record>>();
        types.put("processes", ProcessDefinition.class);
        types.put("elements", ProcessElement.class);
        types.put("relationships", ProcessRelationship.class);
        types.put("inputs", ProcessInput.class);
        types.put("performers", PerformerDefinition.class);
        types.put("assignments", ActivityAssignment.class);
        types.put("dataItems", ProcessDataItem.class);
        types.put("dataAssociations", ActivityDataAssociation.class);
        types.put("openQuestions", ProcessOpenQuestion.class);
        TYPES = java.util.Collections.unmodifiableMap(types);
    }

    public String toXml(ProcessModel model) {
        new ProcessModelValidator().validate(model).requireValid();
        try {
            var buffer = new StringWriter();
            var writer = XMLOutputFactory.newFactory().createXMLStreamWriter(buffer);
            try {
                writer.writeStartDocument("UTF-8", "1.0");
                writer.setDefaultNamespace(NAMESPACE);
                writer.writeStartElement("", "processModel", NAMESPACE);
                writer.writeDefaultNamespace(NAMESPACE);
                writer.writeAttribute("version", VERSION);
                // ProcessModel's components and TYPES deliberately share the format's fixed order.
                for (RecordComponent collection : ProcessModel.class.getRecordComponents()) {
                    start(writer, collection.getName(), 1);
                    var values = (List<?>) collection.getAccessor().invoke(model);
                    for (Object value : values) writeRecord(writer, (Record) value);
                    if (!values.isEmpty()) indent(writer, 1);
                    writer.writeEndElement();
                }
                indent(writer, 0);
                writer.writeEndElement();
                writer.writeCharacters("\n");
                writer.writeEndDocument();
                writer.flush();
                return buffer.toString();
            } finally { writer.close(); }
        } catch (XMLStreamException | ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot serialize process model", ex);
        }
    }

    public void write(ProcessModel model, Path path) throws IOException {
        String xml = toXml(model); // Validate and serialize before opening the destination.
        Files.writeString(path, xml, StandardCharsets.UTF_8);
    }

    public ProcessModel read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) { return parseSource(new InputSource(input)); }
    }
    public ProcessModel parse(String xml) {
        try { return parseSource(new InputSource(new StringReader(xml))); }
        catch (IOException ex) { throw new IllegalArgumentException("Cannot read XML", ex); }
    }

    private static void writeRecord(XMLStreamWriter writer, Record record) throws XMLStreamException, ReflectiveOperationException {
        start(writer, recordName(record.getClass()), 2);
        for (var component : record.getClass().getRecordComponents()) {
            Object value = component.getAccessor().invoke(record);
            if (value == null) continue;
            start(writer, component.getName(), 3);
            String text = value.toString();
            int offset = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\r') {
                    writer.writeCharacters(text.substring(offset, i));
                    writer.writeEntityRef("#13"); // Preserve CR across XML newline normalization.
                    offset = i + 1;
                }
            }
            writer.writeCharacters(text.substring(offset));
            writer.writeEndElement();
        }
        indent(writer, 2);
        writer.writeEndElement();
    }
    private static void start(XMLStreamWriter writer, String name, int depth) throws XMLStreamException {
        indent(writer, depth); writer.writeStartElement("", name, NAMESPACE);
    }
    private static void indent(XMLStreamWriter writer, int depth) throws XMLStreamException {
        writer.writeCharacters("\n" + "  ".repeat(depth));
    }
    private static String recordName(Class<?> type) {
        String name = type.getSimpleName();
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private ProcessModel parseSource(InputSource source) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> { throw invalid("External XML resources are forbidden"); });
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
                @Override public void fatalError(org.xml.sax.SAXParseException ex) throws SAXException { throw ex; }
            });
            Element root = builder.parse(source).getDocumentElement();
            require(root, "processModel");
            attributes(root, "version");
            if (!VERSION.equals(root.getAttribute("version"))) throw invalid("Unsupported or missing process-model version");
            var containers = new LinkedHashMap<String, Element>();
            for (Element child : children(root, false)) {
                requireNamespace(child);
                if (!TYPES.containsKey(child.getLocalName())) throw invalid("Unknown collection: " + child.getLocalName());
                attributes(child);
                if (containers.putIfAbsent(child.getLocalName(), child) != null) throw invalid("Duplicate collection: " + child.getLocalName());
            }
            if (containers.size() != TYPES.size()) throw invalid("All nine model collections are required");
            var model = new ProcessModel(
                readRecords(containers.get("processes"), ProcessDefinition.class),
                readRecords(containers.get("elements"), ProcessElement.class),
                readRecords(containers.get("relationships"), ProcessRelationship.class),
                readRecords(containers.get("inputs"), ProcessInput.class),
                readRecords(containers.get("performers"), PerformerDefinition.class),
                readRecords(containers.get("assignments"), ActivityAssignment.class),
                readRecords(containers.get("dataItems"), ProcessDataItem.class),
                readRecords(containers.get("dataAssociations"), ActivityDataAssociation.class),
                readRecords(containers.get("openQuestions"), ProcessOpenQuestion.class));
            new ProcessModelValidator().validate(model).requireValid();
            return model;
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("Secure XML parser unavailable", ex);
        } catch (SAXException ex) { throw invalid("Invalid XML: " + ex.getMessage()); }
    }

    private static <T extends Record> List<T> readRecords(Element container, Class<T> type) {
        var values = new ArrayList<T>();
        for (Element element : children(container, false)) {
            require(element, recordName(type)); attributes(element);
            var fields = new HashMap<String, String>();
            RecordComponent[] components = type.getRecordComponents();
            for (Element field : children(element, false)) {
                requireNamespace(field); attributes(field);
                String name = field.getLocalName();
                if (Arrays.stream(components).noneMatch(c -> c.getName().equals(name))) throw invalid("Unknown field: " + name);
                if (!children(field, true).isEmpty()) throw invalid("Nested content in field: " + name);
                if (fields.putIfAbsent(name, field.getTextContent()) != null) throw invalid("Duplicate field: " + name);
            }
            var arguments = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                var component = components[i];
                String text = fields.get(component.getName());
                if (component.getType() == String.class) arguments[i] = text;
                else if (component.getType() == boolean.class) {
                    if (!"true".equals(text) && !"false".equals(text)) throw invalid("Invalid or missing boolean: " + component.getName());
                    arguments[i] = Boolean.valueOf(text);
                } else if (component.getType().isEnum()) {
                    if (text == null) arguments[i] = null; // Validator reports required enum fields.
                    else arguments[i] = Arrays.stream(component.getType().getEnumConstants())
                        .filter(e -> ((Enum<?>) e).name().equals(text)).findFirst()
                        .orElseThrow(() -> invalid("Unknown " + component.getName() + ": " + text));
                } else throw new IllegalStateException("Unregistered field type: " + component.getType());
            }
            try {
                values.add(type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(arguments));
            } catch (InvocationTargetException ex) { throw invalid("Invalid " + recordName(type) + ": " + ex.getCause().getMessage()); }
            catch (ReflectiveOperationException ex) { throw new IllegalStateException("Cannot construct registered model record", ex); }
        }
        return values;
    }
    private static List<Element> children(Element parent, boolean allowText) {
        var result = new ArrayList<Element>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) result.add(element);
            else if (!allowText && (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !node.getTextContent().isBlank()) throw invalid("Unexpected text in " + parent.getLocalName());
        }
        return result;
    }
    private static void attributes(Element element, String... permitted) {
        var names = List.of(permitted);
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attribute = attributes.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) continue;
            if (attribute.getNamespaceURI() != null || !names.contains(attribute.getNodeName())) throw invalid("Unknown attribute: " + attribute.getNodeName());
        }
    }
    private static void requireNamespace(Element element) {
        if (!NAMESPACE.equals(element.getNamespaceURI())) throw invalid("Unsupported namespace on " + element.getNodeName());
    }
    private static void require(Element element, String name) {
        requireNamespace(element);
        if (!name.equals(element.getLocalName())) throw invalid("Expected " + name + ", got " + element.getLocalName());
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
