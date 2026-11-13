package local.dev.mavenproject3processmodel.model;

public record ProcessDefinition(
        String processId,
        String name,
        String description
) {
    public ProcessDefinition {
        if (processId == null || processId.isBlank()) {
            throw new IllegalArgumentException(
                    "processId must not be null or blank"
            );
        }
    }
}
