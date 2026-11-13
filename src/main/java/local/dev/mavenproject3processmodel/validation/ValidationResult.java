package local.dev.mavenproject3processmodel.validation;

import java.util.List;
import java.util.stream.Collectors;

public record ValidationResult(List<Diagnostic> diagnostics) {
    public ValidationResult { diagnostics = List.copyOf(diagnostics); }
    public boolean valid() {
        return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
    }
    public void requireValid() {
        if (!valid()) throw new IllegalArgumentException(diagnostics.stream()
            .filter(d -> d.severity() == Diagnostic.Severity.ERROR)
            .map(d -> d.code() + " [" + d.entityId() + "]: " + d.message())
            .collect(Collectors.joining("; ")));
    }
}
