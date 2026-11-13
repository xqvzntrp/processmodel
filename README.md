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
No application/test library dependencies, schemas or build.sh are required.
The Java build runs without a browser. Viewing the generated HTML uses a browser;
rendering its flowchart needs internet access to load Mermaid from a CDN.
Maven plugins provide compilation, the plain Java test runner and JAR packaging.

From this project directory:

```sh
mvn clean install

# No arguments: save/reload the onboarding XML and generate basic read-only HTML.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar

# Create the example at another output path.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar demo target/another.process.xml

# Validate a saved model and print its summary and warnings.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar validate examples/onboarding.process.xml

# Export an existing saved XML file to basic read-only HTML.
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar html examples/onboarding.process.xml target/onboarding.html

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
reloaded model's summary and warnings. They also generate `target/onboarding.html`
by parsing the saved XML. Open that file in your browser. Parent directories must already exist;
compiling creates `target/`. These actions produce XML, basic HTML and console output.

`validate` only reads its input. `copy` and `html` reject same-file paths, including symbolic
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
service tasks; exclusive/parallel gateways; and sequence flows. IDs are globally
unique across processes, elements, relationships, performers, data items and
questions. IDs use `[A-Za-z_][A-Za-z0-9_.-]*`. Input names are unique per process;
assignment pairs and activity/data/direction triples cannot be duplicated.

The ZIP's SUB_PROCESS, MESSAGE_FLOW and ASSOCIATION enum values remain available
for identifying unsupported content, but version 1 validation rejects them.
Parent-subprocess membership is also rejected. Multiple processes and subprocess
hierarchy are deferred rather than flattened. Input types are STRING, NUMBER,
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

Verified on Java 21.0.9 with Maven 3.8.7: **201 checks**. The harness covers model
validation, immutable lists, diagnostic severity, all input types, supported node
types, XML round trips, optional fields, Unicode/whitespace, unknown content,
DOCTYPE/entity/network-reference rejection, file errors and the real no-argument
launcher. It also verifies same-file protection and existing-output preservation
when copying invalid input.

Maven runs the harness under `process-model-checks`; look for `Passed 201 checks`.
Surefire reports zero JUnit tests because this is a separate plain Java harness.
A failing check fails Maven before packaging/installing. `mvn -DskipTests package`
skips this harness when explicitly requested.

See [DESIGN.md](DESIGN.md) for the initial decisions and commit plan.


## Basic read-only HTML

`ProcessModelHtml` reads the saved `.process.xml` through the secure parser. It
produces one Mermaid flowchart and plain bordered tables for every model collection
and validation warnings. No editing controls, navigation widgets or application
framework are included. HTML export does not change the XML or execute conditions.

Events use circles, tasks rectangles, and gateways diamonds with X/+ markers.
User/service tasks are marked in their labels. Flow names and default-flow markers
are displayed. Disconnected nodes and cycles are retained. Full condition text is
shown in the relationships table; performers and data dependencies stay in tables
rather than becoming control-flow arrows. The view is a simplified projection,
not a BPMN diagram or execution format.

The browser loads pinned Mermaid 12.3.0 from jsDelivr. Tables and warning text are
static HTML and remain readable offline; the embedded Mermaid source remains
visible when the library cannot load. No external Java dependencies were added.
HTML text is escaped and diagram labels use Mermaid decimal entities. Only the
fixed renderer script is executable; model text is not inserted into JavaScript.

The default demo saves XML, then rereads it to generate a matching HTML companion:
`onboarding.process.xml` → `onboarding.html`. Other `.xml` suffixes are also replaced
with `.html`; other names get `.html` appended. The `copy` command remains an XML
copy; use `html INPUT OUTPUT` to export any existing saved model.

Export completes parsing, validation and text generation before opening the HTML
output. It preserves existing HTML on invalid input and rejects same-file aliases.
The demo checks XML/HTML aliases before writing XML. XML and HTML writes are not
transactional; an I/O error may leave only XML or an older/partial HTML companion.
Regenerate HTML after editing XML; an existing HTML file does not update itself.

The HTML extension was delivered as three tested commits: exporter, demo/CLI
integration, then documentation. The suite verifies all sections, shapes, task
markers, labels/defaults, cycles, disconnected nodes, escaping, source preservation,
output preservation and command behavior. Browser rendering verification was
blocked by the preview tool's restriction on local file URLs; open the generated
file in your browser to inspect it visually.


## Second example: software change from proposal to merge

[GitHub Flow walkthrough and assumptions](examples/GITHUB-FLOW.md) describes an
adaptation of GitHub's documented branch/review workflow. It adds parallel checks
and review, a combined readiness decision, revision cycles, and separate merge and
closure endings. It also records responsibilities, data dependencies and unresolved
repository policies. It performs no GitHub operations.

```sh
java -cp target/classes local.dev.mavenproject3processmodel.examples.GitHubFlowExample
```

This generates `target/github-flow.process.xml` and `target/github-flow.html`.
NetBeans Run File on `GitHubFlowExample.java` does the same. Run Project still
selects onboarding. The committed [XML](examples/github-flow.process.xml) and
[basic HTML](examples/github-flow.html) are kept reproducible by the test harness.
