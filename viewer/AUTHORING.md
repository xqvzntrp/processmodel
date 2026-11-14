# Author a process model

A `.process.xml` file describes activities, their order, who performs them and the information they use. The HTML viewer reads that file and derives its pages and diagrams. It does not execute tasks or evaluate conditions.

This is a custom format inspired by BPMN, not standard BPMN XML. The structure is defined in `process-model.xsd`. This guide explains how to author models using that schema and the project conventions.

## Start here

1. Open `process_relational_viewer_v4_sidebar.html` in a browser.
2. Choose **Open XML file** and select `author-process.process.xml` for an explorable authoring guide.
3. Inspect `purchase-approval.process.xml` for decisions, roles, data items and two subprocesses. `nested-change.process.xml` demonstrates deeper nesting.
4. Copy the small example below into a plain text editor and save it as `request.process.xml`, using UTF-8 encoding.
5. Open your new file in the viewer. The main diagram should show **Request received → Review request → Review complete**.

Files are selected manually; the viewer does not automatically load neighboring XML. After editing a file, select it again to see the changes. Refreshing the HTML clears the loaded model. XML remains local. Online Mermaid rendering loads a public JavaScript library; an offline fallback provides a simplified diagram.

## Describe the process before writing XML

Write down the trigger, intended outcomes and boundaries. List the normal activities, then add rejection, correction and other exceptions that belong in scope. Identify responsible roles and important inputs and outputs. Record unknown rules as questions.

Use action names such as **Review request**, and explain what a satisfactory result means in the description. An end event should name an outcome. Completing a subprocess does not necessarily mean its business outcome was successful.

## A complete small example

This example records that a review occurred. It does not yet decide whether to approve a request.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<processModel xmlns="urn:local.dev:processmodel:1" version="1">
  <processes>
    <processDefinition>
      <processId>requestReview</processId>
      <name>Review a request</name>
      <description>Receive a request and record a review.</description>
    </processDefinition>
  </processes>
  <elements>
    <processElement>
      <elementId>start</elementId>
      <processId>requestReview</processId>
      <elementType>START_EVENT</elementType>
      <name>Request received</name>
      <description>A request is ready for review.</description>
    </processElement>
    <processElement>
      <elementId>review</elementId>
      <processId>requestReview</processId>
      <elementType>USER_TASK</elementType>
      <name>Review request</name>
      <description>Examine the request and record the findings.</description>
    </processElement>
    <processElement>
      <elementId>finish</elementId>
      <processId>requestReview</processId>
      <elementType>END_EVENT</elementType>
      <name>Review complete</name>
      <description>The review findings have been recorded.</description>
    </processElement>
  </elements>
  <relationships>
    <processRelationship>
      <relationshipId>flow01</relationshipId>
      <relationshipType>SEQUENCE_FLOW</relationshipType>
      <sourceElementId>start</sourceElementId>
      <targetElementId>review</targetElementId>
      <isDefault>false</isDefault>
    </processRelationship>
    <processRelationship>
      <relationshipId>flow02</relationshipId>
      <relationshipType>SEQUENCE_FLOW</relationshipType>
      <sourceElementId>review</sourceElementId>
      <targetElementId>finish</targetElementId>
      <isDefault>false</isDefault>
    </processRelationship>
  </relationships>
  <inputs />
  <performers>
    <performerDefinition>
      <performerId>reviewer</performerId>
      <name>Reviewer</name>
      <performerType>HUMAN_ROLE</performerType>
      <description>Examines the request and records findings.</description>
    </performerDefinition>
  </performers>
  <assignments>
    <activityAssignment>
      <elementId>review</elementId>
      <performerId>reviewer</performerId>
    </activityAssignment>
  </assignments>
  <dataItems />
  <dataAssociations />
  <openQuestions />
</processModel>
```

## Record reference

Keep one process definition per file for this viewer. It selects the first definition and reads records throughout the document; it does not provide selection between multiple processes.

All nine sections are required; empty sections may remain as self-closing tags. Names and descriptions are optional in the schema, but authors should supply them for readable models. `parentSubprocessId` is optional; an open question may omit `elementId` to apply to the process as a whole. Other fields in the table are required except relationship `name` and `conditionExpression`. The Java reader and schema accept fields and sections in any order; the examples use a consistent order for readability.

| Section / record | Fields to supply |
| --- | --- |
| `processes / processDefinition` | `processId`, `name`, `description` |
| `elements / processElement` | `elementId`, `processId`, `elementType`, `name`, `description`; `parentSubprocessId` only for contained elements |
| `relationships / processRelationship` | `relationshipId`, `relationshipType` (`SEQUENCE_FLOW`), `sourceElementId`, `targetElementId`, `isDefault`; add `name` and `conditionExpression` for conditional alternatives |
| `inputs / processInput` | `processId`, `inputName`, `dataType`, `isRequired` (`true` or `false`); examples use `STRING` |
| `performers / performerDefinition` | `performerId`, `name`, `performerType` (`HUMAN_ROLE` or `SYSTEM`), `description` |
| `assignments / activityAssignment` | `elementId`, `performerId` |
| `dataItems / processDataItem` | `dataItemId`, `processId`, `name`, `description` |
| `dataAssociations / activityDataAssociation` | `elementId`, `dataItemId`, `direction` (`INPUT` or `OUTPUT`) |
| `openQuestions / processOpenQuestion` | `questionId`, `processId`, optional `elementId`, `description` |

An assignment connects an activity to its performer. A data association connects an activity to information it consumes (`INPUT`) or produces (`OUTPUT`). Neither creates a sequence flow. A `processInput` describes starting information; it is a separate record from a data item.

### Element types used by these models

| `elementType` | Meaning |
| --- | --- |
| `START_EVENT` | Entry into a process scope |
| `END_EVENT` | An outcome that finishes a path in that scope |
| `TASK` | A general activity when a more specific task type is unnecessary |
| `USER_TASK` | Work performed by a person |
| `SERVICE_TASK` | Work performed by a system |
| `EXCLUSIVE_GATEWAY` | A decision selecting exactly one alternative |
| `PARALLEL_GATEWAY` | A split into concurrent paths or a join waiting for the intended paths |
| `SUB_PROCESS` | An activity with its own contained process elements |

The diagrams simplify these concepts. For example, parallel and exclusive gateways currently share a diamond shape. Explain gateway intent in names and descriptions. For parallel work, reason through the split and join carefully: do not join alternatives that cannot all occur.

### IDs and XML syntax

- Give every record with its own ID a stable ID, globally unique across all record types in the file. For example, a performer and an activity cannot both have the ID `review`. References reuse an existing ID rather than declare another one.
- Start an ID with an ASCII letter or underscore; subsequent characters may also contain digits, dots and hyphens. This matches the existing Java validator. Avoid using `root` as an element ID because it conflicts with the viewer routing convention.
- References use IDs, not display names. Renaming a display name does not require changing links. Changing an ID requires updating every reference to it.
- Keep `processId` consistent wherever that field occurs.
- Tag names and enum values are case-sensitive. Use the namespace and version shown above.
- Escape special characters in XML text: `&` becomes `&amp;`, and `<` becomes `&lt;`. An XML library can handle escaping when generating records.
- For booleans use the literal text `true` or `false`; the schema and Java reader do not accept `1` or `0`. Input data types are `STRING`, `NUMBER`, `BOOLEAN`, `DATE` and `TIMESTAMP`. Version 1 supports only `SEQUENCE_FLOW` relationships.
- Conditions are descriptive strings. The viewer displays them and does not check their syntax, evaluate variables or execute default branches. Use consistent variables and mutually exclusive alternatives; document where each decision is recorded.

## Exercise: add approval and rejection

Starting from the small example:

1. Change the review task description to require exactly one recorded decision: approved or rejected.
2. Add an `EXCLUSIVE_GATEWAY` with element ID `decision` and name **Review decision**.
3. Keep `finish` as an end event, but rename it **Request approved** and update its description.
4. Add an `END_EVENT` with element ID `rejected` and name **Request rejected**.
5. Change `flow02` so it connects `review` to `decision` instead of `finish`.
6. Add the following two records inside `<relationships>`.

```xml
<processRelationship>
  <relationshipId>flow03</relationshipId>
  <relationshipType>SEQUENCE_FLOW</relationshipType>
  <sourceElementId>decision</sourceElementId>
  <targetElementId>finish</targetElementId>
  <name>Approved</name>
  <conditionExpression>reviewDecision == 'approved'</conditionExpression>
  <isDefault>false</isDefault>
</processRelationship>
<processRelationship>
  <relationshipId>flow04</relationshipId>
  <relationshipType>SEQUENCE_FLOW</relationshipType>
  <sourceElementId>decision</sourceElementId>
  <targetElementId>rejected</targetElementId>
  <name>Rejected</name>
  <conditionExpression>reviewDecision == 'rejected'</conditionExpression>
  <isDefault>false</isDefault>
</processRelationship>
```

Use the same `processElement` structure as the example for the two new elements, with `processId` set to `requestReview` and descriptions of their purpose. You should now see five elements and four flows. Walk both outcomes and confirm that neither is a dead end before its end event.

## Add a subprocess

Create a `SUB_PROCESS` element in the parent scope. For every element directly inside it, add a `parentSubprocessId` containing that subprocess element ID. Omit this field for top-level elements.

Give the subprocess its own start, activities and end. Internal flows connect only its direct children. Parent flows connect to and from the subprocess element itself; do not draw a parent flow directly to an internal start or task. For another nesting level, repeat the same pattern and refer to the immediate parent.

Opening the subprocess in the main area automatically displays its internal diagram in the right sidebar. Opening an individual task shows the diagram for its containing scope.

## Validate before sharing

The viewer checks XML parsing and the presence of a process definition. It is not a complete model validator. A file opening successfully does not prove its references or business behavior are correct.

First check XML syntax. If Python 3 is available, run this from the directory containing your file:

```sh
python3 -c "import xml.etree.ElementTree as E; E.parse('request.process.xml'); print('XML syntax OK')"
```

This command checks XML syntax only.

### Validate against the schema

With Java 11 or newer, run this from the project root. The standalone helper requires no extra libraries or build step:

```sh
java viewer/ValidateProcessXml.java viewer/process-model.xsd viewer/request.process.xml
```

Replace the last path with your file. To check the included viewer models together:

```sh
java viewer/ValidateProcessXml.java viewer/process-model.xsd viewer/author-process.process.xml viewer/purchase-approval.process.xml viewer/nested-change.process.xml
```

The helper prints `PASS` or `FAIL` per file and exits with a nonzero status if validation fails. Schema errors normally include a line and column. It checks the namespace, version, sections, field types, allowed values, globally unique IDs and references. It also rejects duplicate input names, assignments and data associations. A parent reference must name an existing element; the separate model validator must establish that it is a subprocess.

The helper receives the schema explicitly, so the XML files need no schema-location attributes. In an XML editor, associate `process-model.xsd` through the editor settings if supported. The HTML viewer does not perform XSD validation automatically. The XSD is not yet wired into the project's Java reader either; run it explicitly as shown above. The existing Java `ProcessModelValidator` supplies additional structural checks.

### Check process behavior

Schema validation does not check parent types, ancestry cycles, scope boundaries, task-only assignments, event direction, reachability, or whether business decisions make sense. Use the existing Java model validator for its supported checks, then manually review the remaining behavior:


- IDs are unique, and every flow endpoint, parent, performer, data item and question reference exists.
- Every parent is a subprocess; no element is its own ancestor.
- Every sequence flow stays within one scope.
- Each scope has a start and end. Every element is reachable from a start and can reach an end. A revision loop must have a possible exit.
- Start events have no incoming flows and end events have no outgoing flows in these examples.
- Decision branches have understandable labels and conditions covering the intended alternatives. Parallel paths have coherent split and join behavior.
- Tasks have appropriate performers, and important inputs and outputs are recorded.
- Unknown rules are recorded as questions. Blocking unknowns are resolved or the model is explicitly kept as a draft.

Finally, open the file in the viewer and inspect every scope, flow endpoint, performer and data relationship. Walk normal, rejected and revised cases with someone who understands the process. Save the accepted model with the project when both the structure and business meaning are satisfactory.

### Common problems

| Symptom | Check |
| --- | --- |
| XML will not open | Unclosed tags, mismatched tag names, unescaped `&` or `<`, or no `processDefinition` |
| A link says record not found | An ID was misspelled or changed without updating references |
| A record seems to be replaced by another | Duplicate IDs; lookup maps retain the last record with that ID |
| An activity is absent from its expected diagram | Its `parentSubprocessId` is wrong, or the wrong scope is selected |
| A flow appears in the flow list but not the scope diagram | Its endpoints are missing or belong to different scopes |
| Diagram shows simplified boxes | Mermaid could not load or render; use Mermaid source and inspect the XML as well |
| Changes do not appear | Reopen the edited XML file with **Open XML file** |
