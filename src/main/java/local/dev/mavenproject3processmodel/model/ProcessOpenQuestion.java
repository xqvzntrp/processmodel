package local.dev.mavenproject3processmodel.model;

/** An unresolved clarification question; a null elementId means process-level. */
public record ProcessOpenQuestion(
        String questionId,
        String processId,
        String elementId,
        String description
) {}
