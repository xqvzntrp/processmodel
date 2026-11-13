package local.dev.mavenproject3processmodel;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import local.dev.mavenproject3processmodel.examples.OnboardingExample;
import local.dev.mavenproject3processmodel.model.ProcessModel;
import local.dev.mavenproject3processmodel.persistence.ProcessModelXml;
import local.dev.mavenproject3processmodel.validation.ProcessModelValidator;

/** NetBeans Run / executable JAR entry point. No arguments runs the onboarding demo. */
public final class Mavenproject3processmodel {
    private Mavenproject3processmodel() { }
    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }
    public static int run(String[] args, PrintStream out, PrintStream err) {
        String[] command = args.length == 0 ? new String[]{"demo", "target/onboarding.process.xml"} : args;
        boolean validArgs = command.length == 2 && (command[0].equals("demo") || command[0].equals("validate"))
            || command.length == 3 && command[0].equals("copy");
        if (!validArgs) {
            err.println("Usage: [demo OUTPUT.process.xml | validate INPUT.process.xml | copy INPUT.process.xml OUTPUT.process.xml]");
            return 2;
        }
        try {
            var codec = new ProcessModelXml();
            ProcessModel saved;
            if (command[0].equals("demo")) {
                Path output = Path.of(command[1]);
                var model = OnboardingExample.model();
                codec.write(model, output);
                saved = codec.read(output);
                if (!saved.equals(model)) throw new IllegalStateException("Saved model round trip differs");
                out.println("Saved and reloaded " + output);
            } else if (command[0].equals("validate")) {
                saved = codec.read(Path.of(command[1]));
                out.println("Validated " + command[1]);
            } else {
                Path input = Path.of(command[1]).toAbsolutePath().normalize();
                Path output = Path.of(command[2]).toAbsolutePath().normalize();
                if (input.equals(output) || Files.exists(output) && Files.isSameFile(input, output))
                    throw new IllegalArgumentException("Input and output must identify different files");
                saved = codec.read(input);
                codec.write(saved, output);
                saved = codec.read(output);
                out.println("Copied and reloaded " + output);
            }
            summary(saved, out);
            return 0;
        } catch (IOException | IllegalArgumentException ex) {
            err.println("Process model failed: " + ex.getMessage().replace('\n', ' ').replace('\r', ' '));
            return 1;
        }
    }
    private static void summary(ProcessModel model, PrintStream out) {
        out.println("Process: " + model.processes().getFirst().name());
        out.println("Elements: " + model.elements().size() + "; relationships: " + model.relationships().size());
        out.println("Inputs: " + model.inputs().size() + "; performers: " + model.performers().size()
            + "; assignments: " + model.assignments().size());
        out.println("Data items: " + model.dataItems().size() + "; data associations: " + model.dataAssociations().size());
        var result = new ProcessModelValidator().validate(model);
        out.println("Structurally valid; warnings: " + result.diagnostics().size());
        for (var diagnostic : result.diagnostics()) out.println(diagnostic.severity() + " " + diagnostic.code()
            + " [" + diagnostic.entityId() + "]: " + diagnostic.message());
    }
}
