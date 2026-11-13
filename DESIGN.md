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
- Plain start/end events, generic/user/service tasks, exclusive/parallel gateways.
- Keep the ZIP's SUB_PROCESS, MESSAGE_FLOW and ASSOCIATION enum values, but report
  unsupported content as structural errors. Parent-subprocess membership is also
  unsupported. Never silently flatten or omit these constructs.
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
