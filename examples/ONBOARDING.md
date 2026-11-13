# Employee onboarding: worked model

The model describes preparation before an employee's first day. It is a specification,
not an executable workflow. The Java source is
`src/main/java/local/dev/mavenproject3processmodel/examples/OnboardingExample.java`.
Its saved form is [onboarding.process.xml](onboarding.process.xml).

## Control flow

1. Receive the onboarding request and review it.
2. At the exclusive gateway:
   - Needs correction: request corrections, then loop back to review.
   - Declined: notify the requester and end.
   - Approved: begin three parallel preparation branches.
3. Prepare HR records, provision accounts, and prepare equipment.
4. Join the preparation branches, confirm readiness, and end.

The three review expressions describe intended branch selection. The model does
not evaluate them or validate the value of `reviewResult`. The join describes the
intended synchronization, but the validator does not prove gateway balance.

## Responsibilities

| Activity | Performer |
| --- | --- |
| Review request | HR coordinator |
| Request corrections | Hiring manager |
| Notify requester of decline | HR coordinator |
| Prepare HR records | HR coordinator |
| Provision accounts | Identity system |
| Prepare equipment | IT coordinator |
| Confirm readiness | Hiring manager |

Human and service activities retain their distinct model types. A SERVICE_TASK
describes a system activity; it does not invoke a service or carry engine bindings.

## Inputs and data

The process declares required employeeName (STRING) and startDate (DATE), and
optional remoteWorker (BOOLEAN). These are input definitions, not submitted values.

The onboarding request is read during review and preparation, and updated during
corrections. The preparation branches produce HR, account and equipment records.
Confirmation reads all three. Input/output direction is relative to the activity;
these associations do not add execution-order arrows. Process input definitions
are not automatically bound to data-item fields.

## Deliberate open questions

- Process-level: Who approves exceptions to the requested start date?
- Account-provisioning activity: Which identity provider will provision accounts?

Both generate WARNING/OPEN_QUESTION diagnostics. The example has no structural
errors and is valid to save despite these unresolved questions.

## Try it

From the project root after `mvn clean install`:

```sh
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar validate target/onboarding.process.xml
```

The default run reports:

```text
Saved and reloaded target/onboarding.process.xml
Process: Employee onboarding
Elements: 13; relationships: 15
Inputs: 3; performers: 4; assignments: 7
Data items: 4; data associations: 11
Structurally valid; warnings: 2
WARNING OPEN_QUESTION [qStartDate]: Who approves exceptions to the requested start date?
WARNING OPEN_QUESTION [qIdentity]: Which identity provider will provision the accounts?
```

Edit a copy of the saved XML and run `validate` to inspect its diagnostics. For
example, changing a targetElementId to `absent` produces UNKNOWN_TARGET. The original
fixture is protected by a regression check against the Java model.
