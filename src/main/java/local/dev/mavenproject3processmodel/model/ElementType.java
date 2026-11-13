package local.dev.mavenproject3processmodel.model;

public enum ElementType {
    START_EVENT(ElementCategory.EVENT),
    END_EVENT(ElementCategory.EVENT),
    TASK(ElementCategory.ACTIVITY),
    USER_TASK(ElementCategory.ACTIVITY),
    SERVICE_TASK(ElementCategory.ACTIVITY),
    SUB_PROCESS(ElementCategory.ACTIVITY),
    EXCLUSIVE_GATEWAY(ElementCategory.GATEWAY),
    PARALLEL_GATEWAY(ElementCategory.GATEWAY);

    private final ElementCategory category;

    ElementType(ElementCategory category) {
        this.category = category;
    }

    public ElementCategory category() {
        return category;
    }
}
