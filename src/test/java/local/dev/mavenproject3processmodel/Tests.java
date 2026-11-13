package local.dev.mavenproject3processmodel;

/** Plain Java regression harness, executed by Maven's test phase. */
public final class Tests {
    private Tests() { }
    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() < 21) throw new AssertionError("Java 21 or newer required");
        int checks = 1;
        checks += ModelChecks.run();
        checks += PersistenceChecks.run();
        checks += DemoChecks.run();
        System.out.println("Passed " + checks + " checks");
    }
}
