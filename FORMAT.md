# Process-model XML, version 1

This is project XML, not `.bpmn`. The root is `processModel` in namespace
`urn:local.dev:processmodel:1`, with unqualified attribute `version="1"`.

Exactly one of each collection is required, even when empty:

| Collection | Record element |
| --- | --- |
| processes | processDefinition |
| elements | processElement |
| relationships | processRelationship |
| inputs | processInput |
| performers | performerDefinition |
| assignments | activityAssignment |
| dataItems | processDataItem |
| dataAssociations | activityDataAssociation |
| openQuestions | processOpenQuestion |

Each record component is a child field element in the same namespace. Strings
are text content. Omission represents null; an empty field represents an empty
string. Enum values use their Java enum names. Primitive boolean fields must
contain exactly `true` or `false`; they cannot be omitted. Conditions are opaque
text. Whitespace and Unicode are retained; CR uses a character reference.

Writing is deterministic in collection, record and component order. Reading allows
collection and field reordering and alternate namespace prefixes, but preserves
record order. Unknown namespaces, collections, record types, attributes, fields,
duplicate containers/fields, unknown enums, nested field markup and unexpected text
are rejected. Namespace declarations, comments and processing instructions are
ignored. Only the fixed registered record types are instantiated; Java class names
cannot be supplied by a document. No XSD or network schema lookup is used.

The record shape is part of the versioned format. Changing record components or
their meaning requires an explicit format/version decision and regression fixtures.

`ProcessModelXml.write(model, path)` validates and serializes before opening the
output, so validation failure leaves existing files unchanged. It writes UTF-8,
overwrites a destination on success and does not create parent directories. A write
I/O failure may leave partial output; saving is not transactional. `read(Path)`
honors the XML byte-encoding declaration. `parse(String)` reads character content.
Both read paths enforce structural validity; warnings remain permitted.

DOCTYPE is forbidden. External general/parameter entities, DTD loading, external
schema access and XInclude processing are disabled. A rejecting entity resolver
provides another barrier to external resources. Invalid/unsupported XML and invalid
models produce IllegalArgumentException; file failures produce IOException.


## Embedded subprocess scopes

Version 1 retains its flat record lists: XML elements are not physically nested
according to process containment. A ProcessElement with type SUB_PROCESS may own
children via their parentSubprocessId field. A null parent means main scope; an
empty parent ID is invalid. Parent references must resolve to subprocesses in the
same process and cannot form cycles. The same representation works at every depth.

A sequence flow's endpoints must share a direct parent scope. Subprocess boundary
crossing is represented by the subprocess activity in its parent scope, not by
arrows to internal children. Subprocess scopes require exactly one direct start
and at least one direct end; the root requires at least one direct start/end.
Descendant events do not satisfy their ancestors' requirements.

This extends the reader/validator's supported subset without changing existing
fields, enums, namespace or version. Flat fixtures remain unchanged. Earlier program
revisions do not support these records semantically and will reject nested files.
