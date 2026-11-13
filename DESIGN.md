# Rich process model: first-version design

The purpose is to describe, validate and persist process knowledge. The source is
`model.zip`, adapted from package `local.dev.mavenproject2process.model` into this
project's package. This application is independent of the BPMN reader project.

The saved rich model is the durable source of truth. Use a versioned project XML
format with namespace `urn:local.dev:processmodel:1` and version `1`. This is not
BPMN XML. It must retain all nine model collections, optional values and Unicode.
A future explicit adapter may produce BPMN; that saved BPMN would then drive the
existing BPMN → Mermaid → HTML pipeline.

## Scope and invariants

- Exactly one process in version 1; keep the list-based API to match the ZIP.
- Plain start/end events, generic/user/service tasks, exclusive/parallel gateways,
  and embedded subprocesses with arbitrary nesting.
- Parent membership must identify a subprocess in the same process and form an
  acyclic hierarchy. Flows stay within their direct scope. Main-scope events cannot
  be supplied by descendants. Each subprocess needs exactly one direct start and
  at least one direct end event. Root scope retains at least one of each.
- Keep MESSAGE_FLOW and ASSOCIATION identifiable but unsupported. Multiple processes
  and called/reusable processes are outside scope. Never silently flatten content.
- Keep performers, activity assignments, typed inputs, process data items,
  input/output data associations and process/element clarification questions.
- Copy every aggregate list defensively. IDs are globally unique ASCII NCNames
  across named entities; references must identify the correct entity kind.
- Check process ownership, activity-only assignments/data associations, duplicate
  bindings, input types, start/end flow direction and exclusive-flow defaults.
- Conditions/defaults are allowed only on exclusive-gateway outgoing flows;
  conditions are stored as text, never evaluated. Only one default per gateway;
  a default cannot also have a condition.
- Missing labels and unresolved questions are warnings. Dangling references,
  malformed IDs, invalid XML text and unsupported structures are errors. A model
  can be structurally valid while its business meaning is incomplete.
- Do not infer reachability, gateway balance, executable correctness, performer
  availability or data type compatibility. Cycles and disconnected nodes remain
  representable. Data associations do not imply control-flow edges.

## Components

Model records carry data. A separate validator returns ordered diagnostics with
severity, code, entity ID and message. Validation must not mutate the model.
Persistence validates before writing and after parsing; errors block save/load,
warnings do not. XML parsing rejects unknown elements, fields, versions and types,
DOCTYPE, external entities, schemas and XInclude. No arbitrary class names from XML
are instantiated. Only explicitly registered record types are read.

The first worked example is onboarding: review, correction loop, declined ending,
three parallel preparation activities, join, confirmation, completed ending. Add
performer assignments, a required input, read/write data and an open question to
demonstrate the richer information.

## Planned commits

1. `build: establish Java 21 process-model foundation`
2. `feat: add rich process model and validation`
3. `feat: persist process models as secure versioned XML`
4. `feat: add onboarding model and NetBeans demo`
5. `docs: explain process-model workflow and boundaries`

Each commit must pass its relevant checks before committing. No external application
or test libraries, graphical editor, execution engine or automatic BPMN adaptation.

## Implementation record

The input ZIP contained 16 Java records/enums, with no build, parser or tests.
Original archive SHA-256:
`a5393ce019e65cdba86e0e26a9eecb0ded049ec0db43962a0a8fa286452d7ca6`.
The source archive remains unchanged. The record fields are retained; the package
was renamed for this project and PARALLEL_GATEWAY was added. The aggregate's list
copies and ProcessDefinition's nonblank-ID check were retained. Other structural
rules live in the separate validator rather than being scattered through records.

Verified progression before the planned commits: 1 build check, 45 checks with the
rich model/validator, 84 with secure XML, and 110 with the onboarding example and
launcher. The final documentation commit reruns all 110 checks and the application.
NetBeans Run uses a project action mapping; no-argument Run File uses the same demo.
The executable JAR has also been exercised. No source history was rewritten or
pushed, and the separate BPMN reader project was not modified.


## Basic read-only HTML continuation

A direct rich-XML visual projection is separate from future BPMN adaptation. The
HTML exporter reads the saved project XML, then emits a Mermaid control-flow view
and static tables for all nine collections and warnings. It adds no editor or
execution behavior. Java remains dependency-free; the browser loads a pinned
Mermaid renderer from a CDN. Export never writes the source XML.

Planned continuation commits, each verified before committing:

1. `feat: export saved process models as basic read-only HTML` — 155 checks.
2. `feat: generate basic HTML from the saved demo model` — 166 checks.
3. `docs: explain basic read-only HTML export` — full suite and application run.

The default NetBeans/JAR demo generates XML and HTML. An explicit `html INPUT OUTPUT`
command renders saved models. Validation and XML copy retain their existing roles.
The browser preview tool cannot open local file URLs, so rendering inspection is
left to a normal browser; automated export and command checks pass.


## GitHub Flow example continuation

The second example is an adaptation of GitHub's documented GitHub Flow. Its
parallel coordination, readiness assessment and no-merge closure branch are explicit
local modeling decisions. The accompanying walkthrough distinguishes them from
the source and documents the absence of commit-versioned data or approval freshness
checks. No workflow is executed and no repository policy is asserted.

Planned and tested commits:

1. `feat: model a documented GitHub Flow adaptation` — 187 checks.
2. `feat: save and visualize the GitHub Flow example` — 201 checks.
3. `docs: explain GitHub Flow assumptions and review paths` — full suite and demos.

The existing schema, validator, HTML styling and default onboarding launcher remain
unchanged. The example is a separate Java main class with an optional XML output
path. It saves/reloads XML before generating the basic HTML from the saved file.


## Nested subprocess continuation

The original first-version rejection of subprocesses is superseded by embedded
scope support. The records and XML field layout are unchanged. Version 1 already
represented SUB_PROCESS and parentSubprocessId; accepting them is an additive
supported-subset extension, not a new serialization shape. Existing flat files
retain identical XML and HTML output. Older application revisions reject nested
models rather than silently flatten them.

Containment validation and scope ordering are iterative, with no one-level limit.
Metadata remains process-owned: performers, data items and inputs are not cloned
per subprocess. Assignments/data dependencies can refer to a subprocess activity;
children have their own explicit bindings. There is no runtime variable scope,
parameter passing, call mechanism, interruption, token-flow or completion engine.

Four commits, tested before committing:

1. `feat: validate nested embedded subprocess scopes` — 229 checks.
2. `feat: visualize nested subprocesses by scope` — 263 checks.
3. `feat: add a nested software-validation example` — 280 checks.
4. `docs: explain nested subprocess scopes and examples` — full suite and demos.

Validation tests cover 256 nesting levels. XML/HTML tests cover 64 nested scopes,
node/flow preservation, per-diagram endpoint declarations, cycle rejection, scope
entry/exit rules and escaping. The worked example contains two embedded levels.
Browser rendering remains unverified by the agent due to local-file preview policy.
