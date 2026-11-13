package local.dev.mavenproject3processmodel.validation;

public record Diagnostic(Severity severity, String code, String entityId, String message) {
    public enum Severity { ERROR, WARNING }
}
