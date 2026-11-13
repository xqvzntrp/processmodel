package local.dev.mavenproject3processmodel.model;

public record PerformerDefinition(
        String performerId,
        String name,
        PerformerType performerType,
        String description
) {}
