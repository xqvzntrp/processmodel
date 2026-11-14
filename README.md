# Rich process model — Java 21

A process-description library and NetBeans/Maven demo based on the supplied
`model.zip`. It records process structure, responsibilities, input definitions,
data dependencies and unresolved questions. It does not execute activities.

The rich model's saved `.process.xml` file is the durable source of truth. This is
our own versioned XML format, not BPMN. A future explicit adapter can export BPMN
for the existing BPMN → Mermaid → HTML project. That integration is not part of
this initial foundation; the BPMN reader project remains separate and unchanged.

## Build and run

Requires a Java 21 JDK and Maven. The compiler uses `--release 21`, `-Xlint:all` and
`-Werror`. A pinned compiler plugin fixes the scaffold's Java 5 default failure.
No application/test library dependencies or build.sh are required.
The Java build and commands run without a browser and produce XML, not HTML.
Open saved models in the standalone [viewer](viewer/process_relational_viewer_v4_sidebar.html).
Its flowcharts use Mermaid from a CDN, with a simplified offline fallback.
Maven plugins provide compilation, the plain Java test runner and JAR packaging.

From this project directory:

```sh
mvn clean install

# No arguments: save/reload the onboarding XML and print a summary.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar

# Create the example at another output path.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar demo target/another.process.xml

# Validate a saved model and print its summary and warnings.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar validate examples/onboarding.process.xml

# Read a saved model, write a separate file, then reload it.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar copy examples/onboarding.process.xml target/copied.process.xml
```

Alternatively use the NetBeans-bundled Maven:

```sh
JAVA_HOME=/usr/lib/jvm/jdk-21.0.9-oracle-x64 \
  /usr/lib/apache-netbeans/java/maven/bin/mvn --no-transfer-progress clean install
```

Open this directory in NetBeans and use **Run Project (F6)**. The checked-in
`nbactions.xml` explicitly selects the application entry point. Run File on
`Mavenproject3processmodel.java` also works without arguments. Both save
`target/onboarding.process.xml`, reload it, verify the round trip, and print the
reloaded model's summary and warnings. Parent directories must already exist;
compiling creates `target/`. Open the standalone viewer, choose **Open XML file**,
and select `target/onboarding.process.xml`. These actions produce XML and console output.

`validate` only reads its input. `copy` rejects same-file paths, including symbolic
and hard-link aliases. Successful writes overwrite the destination. Validation or
parsing failure leaves existing output untouched; final writes are not atomic and
an I/O failure can leave partial output. Status 0 means success, 1 means a model/file
error, and 2 means invalid command arguments. Diagnostics are written to stderr.

## Model structure

All records and enums are in `local.dev.mavenproject3processmodel.model`.
`ProcessModel` owns nine defensively copied lists:

| Collection | Content |
| --- | --- |
| processes | Process identity, name and description |
| elements | Events, activities and gateways, with process ownership |
| relationships | Directed control-flow edges, labels, condition text and defaults |
| inputs | Typed required/optional input definitions, not input values |
| performers | Human roles and systems, not live user accounts |
| assignments | Activity-to-performer references |
| dataItems | Process-owned named data descriptions, not runtime values |
| dataAssociations | Data consumed/read or produced/updated by an activity |
| openQuestions | Process-level or element-level unresolved clarification questions |

Version 1 accepts exactly one process; plain start/end events; generic, user and
service tasks; exclusive/parallel gateways; embedded subprocesses with arbitrary
nesting; and sequence flows. IDs are globally
unique across processes, elements, relationships, performers, data items and
questions. IDs use `[A-Za-z_][A-Za-z0-9_.-]*`. Input names are unique per process;
assignment pairs and activity/data/direction triples cannot be duplicated.

SUB_PROCESS uses `parentSubprocessId` to form an acyclic hierarchy in the same
process. Root elements have a null parent. Each embedded subprocess may contain
ordinary elements and other subprocesses, with no one-level restriction. Sequence
flows connect only elements in the same scope; parent flows connect to the
subprocess node rather than its internal events. Each subprocess needs exactly one
direct start and at least one direct end event. The main scope needs at least one
of each. See [nested scope rules](examples/NESTED-SUBPROCESSES.md).

MESSAGE_FLOW and ASSOCIATION remain identifiable enum values but are unsupported.
Multiple processes and reusable called processes are not implemented. Input types are STRING, NUMBER,
BOOLEAN, DATE and TIMESTAMP. Only their definitions are stored; no values are
parsed or evaluated against those types.

## Validation

`ProcessModelValidator.validate(model)` returns an immutable `ValidationResult`
containing diagnostics with severity, stable code, entity ID and message.
`valid()` means no ERROR diagnostics. `requireValid()` throws if errors exist.

Errors include malformed/duplicate IDs, dangling or wrong-kind references, invalid
process ownership, missing required types, illegal XML characters, unsupported
structures, missing start/end events, and flows entering starts or leaving ends.
Only activity elements may have performer assignments or data associations.

Conditions/default flags require an exclusive source gateway. One default is
allowed per gateway and it cannot also contain a condition. Conditions are opaque
text; the library assigns no expression language and never evaluates them. It does
not prove that branch conditions are exclusive or exhaustive.

Missing display names, exclusive branches without conditions/defaults, and open
questions are warnings. A model with unresolved questions can still be structurally
valid and saved. Invalid draft models can be assembled in memory for validation,
but cannot be saved or loaded through the strict persistence API. Null collection
references/entries are rejected by the aggregate constructor; ProcessDefinition
also rejects a blank/null process ID immediately.

No reachability, gateway-balance, deadlock, performer-availability or data-type
compatibility analysis is performed. Cycles and disconnected elements are allowed.
A data association does not imply a control-flow edge. No editor, engine, scheduler,
BPMN importer/exporter or automatic simplification is included.

## API

```java
var model = local.dev.mavenproject3processmodel.examples.OnboardingExample.model();
var validator = new local.dev.mavenproject3processmodel.validation.ProcessModelValidator();
var validation = validator.validate(model);
validation.requireValid(); // Warnings do not block saving.

var xml = new local.dev.mavenproject3processmodel.persistence.ProcessModelXml();
var file = java.nio.file.Path.of("target/onboarding.process.xml");
xml.write(model, file);
var reloaded = xml.read(file);
if (!model.equals(reloaded)) throw new IllegalStateException("Round trip differs");
```

Persistence retains null versus empty text, Unicode, line breaks, booleans, enum
values and record order. XML reading uses JDK APIs with namespace awareness,
DOCTYPE rejection and external entity/DTD/schema access disabled. Unknown versions,
fields and types are rejected. See [FORMAT.md](FORMAT.md) for the exact contract.

## Worked example and verification

[Onboarding walkthrough](examples/ONBOARDING.md) explains the 13 elements, 15 flows,
3 inputs, 4 performers, 7 assignments, 4 data items, 11 data associations and 2 open
questions. [onboarding.process.xml](examples/onboarding.process.xml) is a committed,
deterministically generated fixture checked against the Java example.

The suite passes **191 checks** after removing the Java HTML exporter and its tests. The harness covers model
validation, immutable lists, diagnostic severity, all input types, supported node
types, XML round trips, optional fields, Unicode/whitespace, unknown content,
DOCTYPE/entity/network-reference rejection, file errors and the real no-argument
launcher. It also verifies same-file protection and existing-output preservation
when copying invalid input.

Maven runs the harness under `process-model-checks`; look for `Passed 191 checks`.
Surefire reports zero JUnit tests because this is a separate plain Java harness.
A failing check fails Maven before packaging/installing. `mvn -DskipTests package`
skips this harness when explicitly requested.

See [DESIGN.md](DESIGN.md) for the initial decisions and commit plan.


## View saved process XML

Open [the standalone viewer](viewer/process_relational_viewer_v4_sidebar.html) in a browser,
choose **Open XML file**, and select any `.process.xml` model. The right sidebar follows
whichever main process or subprocess is open in the main area. Java does not generate
HTML reports, and the former `html` command is no longer supported.

You can author, schema-validate and view files without building the Java application.
See [AUTHORING.md](viewer/AUTHORING.md) for a starter example, the format reference and
schema-validation commands. The standalone XSD helper checks document structure and
references; the Java application's `validate` command adds process-specific checks.
The Java reader does not automatically run the XSD.

## Second example: software change from proposal to merge

[GitHub Flow walkthrough and assumptions](examples/GITHUB-FLOW.md) describes an
adaptation of GitHub's documented branch/review workflow. It adds parallel checks
and review, a combined readiness decision, revision cycles, and separate merge and
closure endings. It also records responsibilities, data dependencies and unresolved
repository policies. It performs no GitHub operations.

```sh
java -cp target/classes local.dev.mavenproject3processmodel.examples.GitHubFlowExample
```

This generates `target/github-flow.process.xml`. Select that file in the standalone
viewer. NetBeans Run File on `GitHubFlowExample.java` does the same. Run Project still
selects onboarding. The committed [XML](examples/github-flow.process.xml) is kept
reproducible by the test harness.


## Nested subprocess example

[Nested software-validation walkthrough](examples/NESTED-SUBPROCESSES.md) derives
from the GitHub Flow example. “Validate proposed change” contains “Verify build”,
which contains compilation, conditional testing and check-result recording.

```sh
java -cp target/classes local.dev.mavenproject3processmodel.examples.NestedChangeExample
```

Open `target/nested-change.process.xml` through **Open XML file** in the standalone
viewer to explore the main process and both subprocesses. NetBeans Run File on
`NestedChangeExample.java` works without arguments. Run Project remains onboarding.
The committed [XML](examples/nested-change.process.xml) is checked for reproducibility.
