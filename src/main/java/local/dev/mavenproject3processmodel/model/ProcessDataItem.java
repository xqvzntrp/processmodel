package local.dev.mavenproject3processmodel.model;

public record ProcessDataItem(
        String dataItemId,
        String processId,
        String name,
        String description
) {}
