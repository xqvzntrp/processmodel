package local.dev.mavenproject3processmodel.validation;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import local.dev.mavenproject3processmodel.model.*;

/** Stateless structural validator. Conditions and process activities are never executed. */
public final class ProcessModelValidator {
    public ValidationResult validate(ProcessModel model) {
        var session = new Session();
        if (model == null) session.error("MODEL_REQUIRED", "model", "Model is required");
        else session.validate(model);
        return new ValidationResult(session.diagnostics);
    }

    private static final class Session {
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final Set<String> globalIds = new HashSet<>();
        private Map<String, ProcessDefinition> processes;
        private Map<String, ProcessElement> elements;
        private Map<String, PerformerDefinition> performers;
        private Map<String, ProcessDataItem> dataItems;

        private void error(String code, String id, String message) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.ERROR, code, id, message));
        }
        private void warning(String code, String id, String message) {
            diagnostics.add(new Diagnostic(Diagnostic.Severity.WARNING, code, id, message));
        }
        private <T> Map<String, T> index(List<T> records, Function<T, String> identifier) {
            var result = new LinkedHashMap<String, T>();
            for (T record : records) {
                String id = identifier.apply(record);
                if (id == null || !id.matches("[A-Za-z_][A-Za-z0-9_.-]*"))
                    error("INVALID_ID", id, "ID must use the supported ASCII NCName syntax");
                if (id != null) {
                    if (!globalIds.add(id)) error("DUPLICATE_ID", id, "IDs must be globally unique");
                    result.putIfAbsent(id, record);
                }
            }
            return result;
        }
        private void owner(String processId, String entityId) {
            if (!processes.containsKey(processId)) error("UNKNOWN_PROCESS", entityId, "Unknown process: " + processId);
        }
        private void name(String name, String id) {
            if (name == null || name.isBlank()) warning("MISSING_NAME", id, "A display name has not been supplied");
        }
        private ProcessElement activity(String id, String context) {
            ProcessElement element = elements.get(id);
            if (element == null) error("UNKNOWN_ELEMENT", context, "Unknown element: " + id);
            else if (element.elementType() == null || element.elementType().category() != ElementCategory.ACTIVITY)
                error("ACTIVITY_REQUIRED", context, "Reference must identify an activity: " + id);
            return element;
        }
        private void validate(ProcessModel model) {
            if (model.processes().size() != 1) error("PROCESS_COUNT", "model", "Version 1 requires exactly one process");
            processes = index(model.processes(), ProcessDefinition::processId);
            elements = index(model.elements(), ProcessElement::elementId);
            index(model.relationships(), ProcessRelationship::relationshipId);
            performers = index(model.performers(), PerformerDefinition::performerId);
            dataItems = index(model.dataItems(), ProcessDataItem::dataItemId);
            index(model.openQuestions(), ProcessOpenQuestion::questionId);
            var records = new ArrayList<Record>();
            records.addAll(model.processes()); records.addAll(model.elements()); records.addAll(model.relationships());
            records.addAll(model.inputs()); records.addAll(model.performers()); records.addAll(model.assignments());
            records.addAll(model.dataItems()); records.addAll(model.dataAssociations()); records.addAll(model.openQuestions());
            for (Record record : records) checkText(record);
            for (var process : model.processes()) {
                name(process.name(), process.processId());
                boolean start = model.elements().stream().anyMatch(e -> Objects.equals(e.processId(), process.processId()) && e.parentSubprocessId() == null && e.elementType() == ElementType.START_EVENT);
                boolean end = model.elements().stream().anyMatch(e -> Objects.equals(e.processId(), process.processId()) && e.parentSubprocessId() == null && e.elementType() == ElementType.END_EVENT);
                if (!start || !end) error("EVENTS_REQUIRED", process.processId(), "Process scope needs at least one direct start and one direct end event");
            }
            for (var element : model.elements()) {
                owner(element.processId(), element.elementId()); name(element.name(), element.elementId());
                if (element.elementType() == null) error("ELEMENT_TYPE_REQUIRED", element.elementId(), "Element type is required");
                if (element.parentSubprocessId() != null) {
                    var parent = elements.get(element.parentSubprocessId());
                    if (parent == null) error("UNKNOWN_PARENT", element.elementId(), "Unknown parent subprocess: " + element.parentSubprocessId());
                    else {
                        if (parent.elementType() != ElementType.SUB_PROCESS) error("INVALID_PARENT_TYPE", element.elementId(), "Parent must be a subprocess");
                        if (!Objects.equals(parent.processId(), element.processId())) error("PARENT_PROCESS_MISMATCH", element.elementId(), "Parent and child must belong to the same process");
                    }
                }
            }
            hierarchy(model);
            var defaults = new HashSet<String>();
            var outgoing = new HashMap<String, Integer>();
            for (var flow : model.relationships()) outgoing.merge(flow.sourceElementId(), 1, Integer::sum);
            for (var flow : model.relationships()) {
                String id = flow.relationshipId();
                if (flow.relationshipType() != RelationshipType.SEQUENCE_FLOW)
                    error("RELATIONSHIP_UNSUPPORTED", id, "Only sequence flows are supported in version 1");
                var source = elements.get(flow.sourceElementId());
                var target = elements.get(flow.targetElementId());
                if (source == null) error("UNKNOWN_SOURCE", id, "Unknown source: " + flow.sourceElementId());
                if (target == null) error("UNKNOWN_TARGET", id, "Unknown target: " + flow.targetElementId());
                if (source != null && target != null && !Objects.equals(source.processId(), target.processId()))
                    error("CROSS_PROCESS_FLOW", id, "Sequence flows cannot cross processes");
                if (source != null && target != null && !Objects.equals(source.parentSubprocessId(), target.parentSubprocessId()))
                    error("CROSS_SCOPE_FLOW", id, "Sequence flows must connect elements in the same parent scope");
                if (source != null && source.elementType() == ElementType.END_EVENT || target != null && target.elementType() == ElementType.START_EVENT)
                    error("EVENT_DIRECTION", id, "Flows cannot leave end events or enter start events");
                boolean conditional = flow.conditionExpression() != null && !flow.conditionExpression().isBlank();
                boolean exclusive = source != null && source.elementType() == ElementType.EXCLUSIVE_GATEWAY;
                if ((conditional || flow.isDefault()) && !exclusive)
                    error("EXCLUSIVE_FLOW_REQUIRED", id, "Conditions and defaults require an exclusive source gateway");
                if (flow.isDefault() && conditional) error("DEFAULT_HAS_CONDITION", id, "A default flow cannot have a condition");
                if (flow.isDefault() && !defaults.add(flow.sourceElementId())) error("MULTIPLE_DEFAULTS", id, "Only one default per source gateway");
                if (exclusive && outgoing.getOrDefault(source.elementId(), 0) > 1 && !flow.isDefault() && !conditional)
                    warning("UNSPECIFIED_BRANCH", id, "Branch has neither a condition nor default designation");
            }
            Set<List<String>> inputKeys = new HashSet<>();
            for (var input : model.inputs()) {
                owner(input.processId(), input.inputName());
                if (input.inputName() == null || input.inputName().isBlank()) error("INPUT_NAME_REQUIRED", input.processId(), "Input name is required");
                if (input.dataType() == null) error("INPUT_TYPE_REQUIRED", input.inputName(), "Input type is required");
                if (!inputKeys.add(java.util.Arrays.asList(input.processId(), input.inputName()))) error("DUPLICATE_INPUT", input.inputName(), "Input names must be unique within a process");
            }
            for (var performer : model.performers()) {
                name(performer.name(), performer.performerId());
                if (performer.performerType() == null) error("PERFORMER_TYPE_REQUIRED", performer.performerId(), "Performer type is required");
            }
            Set<List<String>> assignments = new HashSet<>();
            for (var assignment : model.assignments()) {
                activity(assignment.elementId(), assignment.elementId());
                if (!performers.containsKey(assignment.performerId())) error("UNKNOWN_PERFORMER", assignment.elementId(), "Unknown performer: " + assignment.performerId());
                if (!assignments.add(java.util.Arrays.asList(assignment.elementId(), assignment.performerId())))
                    error("DUPLICATE_ASSIGNMENT", assignment.elementId(), "Duplicate activity/performer assignment");
            }
            for (var item : model.dataItems()) { owner(item.processId(), item.dataItemId()); name(item.name(), item.dataItemId()); }
            Set<List<Object>> associations = new HashSet<>();
            for (var association : model.dataAssociations()) {
                var element = activity(association.elementId(), association.elementId());
                var item = dataItems.get(association.dataItemId());
                if (item == null) error("UNKNOWN_DATA_ITEM", association.elementId(), "Unknown data item: " + association.dataItemId());
                if (element != null && item != null && !Objects.equals(element.processId(), item.processId()))
                    error("CROSS_PROCESS_DATA", association.elementId(), "Activity and data item must share a process");
                if (association.direction() == null) error("DATA_DIRECTION_REQUIRED", association.elementId(), "Data direction is required");
                if (!associations.add(java.util.Arrays.asList(association.elementId(), association.dataItemId(), association.direction())))
                    error("DUPLICATE_DATA_ASSOCIATION", association.elementId(), "Duplicate activity/data/direction binding");
            }
            for (var question : model.openQuestions()) {
                owner(question.processId(), question.questionId());
                if (question.elementId() != null) {
                    var element = elements.get(question.elementId());
                    if (element == null) error("UNKNOWN_ELEMENT", question.questionId(), "Unknown question element: " + question.elementId());
                    else if (!Objects.equals(element.processId(), question.processId())) error("QUESTION_PROCESS_MISMATCH", question.questionId(), "Question and element must share a process");
                }
                if (question.description() == null || question.description().isBlank()) error("QUESTION_TEXT_REQUIRED", question.questionId(), "Clarification question text is required");
                warning("OPEN_QUESTION", question.questionId(), question.description());
            }
        }
        private record Scope(String processId, String parentId) { }
        private void hierarchy(ProcessModel model) {
            // Iterative traversal avoids imposing a recursion-depth limit on containment.
            var completed = new HashSet<String>();
            for (var element : model.elements()) {
                var path = new HashSet<String>();
                String current = element.elementId();
                while (current != null && !completed.contains(current)) {
                    if (!path.add(current)) {
                        error("SUBPROCESS_CYCLE", current, "Containment cannot contain a direct or indirect cycle");
                        break;
                    }
                    var node = elements.get(current);
                    current = node == null ? null : node.parentSubprocessId();
                }
                completed.addAll(path);
            }
            var events = new HashMap<Scope, int[]>();
            for (var element : model.elements()) {
                var count = events.computeIfAbsent(new Scope(element.processId(), element.parentSubprocessId()), key -> new int[2]);
                if (element.elementType() == ElementType.START_EVENT) count[0]++;
                if (element.elementType() == ElementType.END_EVENT) count[1]++;
            }
            for (var element : model.elements()) {
                if (element.elementType() != ElementType.SUB_PROCESS || element.elementId() == null) continue;
                var count = events.getOrDefault(new Scope(element.processId(), element.elementId()), new int[2]);
                if (count[0] != 1 || count[1] < 1) error("SUBPROCESS_EVENTS_REQUIRED", element.elementId(),
                    "Each subprocess needs exactly one direct start and at least one direct end event");
            }
        }
        private void checkText(Record record) {
            try {
                for (var component : record.getClass().getRecordComponents()) {
                    Object value = component.getAccessor().invoke(record);
                    if (value instanceof String text && text.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13
                            || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF)))
                        error("INVALID_XML_TEXT", record.getClass().getSimpleName() + "." + component.getName(), "Illegal XML 1.0 character");
                }
            } catch (IllegalAccessException | InvocationTargetException ex) {
                throw new IllegalStateException("Cannot inspect model record", ex);
            }
        }
    }
}
