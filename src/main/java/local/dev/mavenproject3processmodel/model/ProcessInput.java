package local.dev.mavenproject3processmodel.model;

public record ProcessInput(
        String processId,
        String inputName,
        InputDataType dataType,
        boolean isRequired
) {}
