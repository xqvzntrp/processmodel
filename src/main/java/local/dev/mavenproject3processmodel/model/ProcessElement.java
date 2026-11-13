package local.dev.mavenproject3processmodel.model;

public record ProcessElement(
        String elementId,
        String processId,
        ElementType elementType,
        String name,
        String description,
        String parentSubprocessId
) {}
