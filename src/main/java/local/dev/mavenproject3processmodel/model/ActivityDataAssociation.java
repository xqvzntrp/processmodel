package local.dev.mavenproject3processmodel.model;

/** A simplified data dependency; it does not specify execution order. */
public record ActivityDataAssociation(
        String elementId,
        String dataItemId,
        DataDirection direction
) {}
