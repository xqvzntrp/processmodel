package local.dev.mavenproject3processmodel.model;

import java.util.List;

public record ProcessModel(
        List<ProcessDefinition> processes,
        List<ProcessElement> elements,
        List<ProcessRelationship> relationships,
        List<ProcessInput> inputs,
        List<PerformerDefinition> performers,
        List<ActivityAssignment> assignments,
        List<ProcessDataItem> dataItems,
        List<ActivityDataAssociation> dataAssociations,
        List<ProcessOpenQuestion> openQuestions
) {
    public ProcessModel {
        processes = List.copyOf(processes);
        elements = List.copyOf(elements);
        relationships = List.copyOf(relationships);
        inputs = List.copyOf(inputs);
        performers = List.copyOf(performers);
        assignments = List.copyOf(assignments);
        dataItems = List.copyOf(dataItems);
        dataAssociations = List.copyOf(dataAssociations);
        openQuestions = List.copyOf(openQuestions);
    }
}
