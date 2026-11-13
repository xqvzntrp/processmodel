# Embedded subprocesses at multiple depths

The model now supports an embedded subprocess containing another embedded
subprocess. The [nested-change XML](nested-change.process.xml) is derived from the
[flat GitHub Flow example](GITHUB-FLOW.md). The [basic HTML](nested-change.html)
shows three diagrams with the same plain tables and no editing controls.

```text
Main software-change process
└── Validate proposed change (validateChange)
    ├── Parallel build verification and review
    ├── Verify build (checks)
    │   ├── Compile changes
    │   ├── If compilation passed, run tests
    │   └── Record a result, including failed/skipped checks
    └── Assess merge readiness
```

## How containment works

`ProcessElement.parentSubprocessId` points to its immediate parent. A subprocess
is itself an activity in its parent's scope, with its own internal children.
This rule is the same at every depth; there is no special second-level record.

| Element | Type | Parent |
| --- | --- | --- |
| validateChange | SUB_PROCESS | null: main process |
| checks | SUB_PROCESS | validateChange |
| compile | SERVICE_TASK | checks |
| runTests | SERVICE_TASK | checks |
| review | USER_TASK | validateChange |
| decision | EXCLUSIVE_GATEWAY | null: main process |

All children and parents must belong to the same process. A parent must exist and
be a SUB_PROCESS. Direct and indirect containment cycles are errors. Record order
is independent of containment: a child may appear before its parent in saved XML.

## Flow boundaries and events

Sequence flows connect siblings in one direct scope. A main-process flow enters
`validateChange`, never `compile` or an internal start event. The validation scope
connects to the `checks` subprocess node; compilation/testing flows stay inside it.

- Main scope: at least one direct start and direct end event.
- Every subprocess scope: exactly one direct start and at least one direct end.
- Descendant events never substitute for direct events.
- Flows cannot enter any start event or leave any end event.
- Root-to-child, child-to-parent and different-scope flows are errors.

The conventions describe an embedded activity with its own entry/exit. They do not
implement execution semantics. The validator does not prove reachability, gateway
balance, deadlock freedom, termination or condition completeness. Internal cycles
and disconnected elements remain representable, as in flat scopes.

## What changes from the flat example

The flat example remains unchanged. The nested adaptation groups fork, build
verification, review, join and assessment inside `validateChange`. Main-scope
open/revise paths now lead to that subprocess node. Assessment reaches an internal
end, and the parent then reaches its existing ready/revise/close decision.

The former `checks` activity becomes the inner “Verify build” subprocess. Its
contents compile the change, branch on the descriptive compilation result, run
tests only on success, and record a completed result. A failed compilation records
tests as skipped. The internal end means results are available, not that checks
passed. The existing readiness assessment still decides whether to merge or revise.

The model has 27 elements, 27 flows, 4 input definitions, 4 performers, 16 activity
assignments, 10 data items, 40 data associations and 5 open questions. The fifth
question asks how cancelled/unavailable checks should become a completed result.
This is not an executable cancellation rule.

Performers, inputs and data items remain process-level definitions. A subprocess
assignment identifies its coordinator; it does not automatically assign its
children. Data associations on subprocesses summarize dependencies. There are no
local runtime variables, parameter mappings, inherited assignments or automatic
failure propagation. The compile/test report descriptions state how skipped tests
are interpreted; the model does not store or enforce runtime values.

## Persistence and HTML

No record or XML layout changed. SUB_PROCESS and parentSubprocessId already existed;
the version-1 supported subset now accepts valid hierarchies. Flat XML/HTML fixtures
remain identical. Older program revisions reject nested models.

The saved XML still contains flat lists of records. Containment comes from IDs,
not physically nested XML. The reader validates the complete hierarchy on load.

HTML emits one diagram per scope, with a labeled subprocess box in the parent.
Subprocess sections appear parent-first and display their ID and immediate parent.
Each node and flow appears in exactly one scope diagram; the tables retain the
complete model. Validation and scope ordering use iterative traversal rather than
recursive Java calls. There is no one-level restriction; tests cover 256 levels of
validation and 64 levels of XML/HTML round trips.

## Run it

From the project root:

```sh
mvn clean install
java -cp target/classes local.dev.mavenproject3processmodel.examples.NestedChangeExample
```

Open `target/nested-change.html` in a browser. Its source is
`target/nested-change.process.xml`. A custom output path is optional, with an
existing parent directory:

```sh
java -cp target/classes local.dev.mavenproject3processmodel.examples.NestedChangeExample target/my-nested.process.xml
```

To visualize the committed XML directly:

```sh
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar validate examples/nested-change.process.xml
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar html examples/nested-change.process.xml target/nested-change.html
```

NetBeans Run File on `NestedChangeExample.java` generates the example. Run Project
continues to use onboarding. No Git/GitHub operations are executed. This adds
embedded scopes, not reusable called processes, BPMN export or an execution engine.
The diagram needs internet access for Mermaid; the rest is static read-only HTML.
Browser rendering has not been visually verified by the agent because file-URL
previews are blocked. Full Maven suite: **280 checks passed**.
