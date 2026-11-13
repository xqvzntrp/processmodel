# Software change: proposal to merge

A process-model adaptation inspired by
[GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow),
consulted on 2026-11-13. This is an independently authored specification of a
workflow, not a copied BPMN file or an executable GitHub integration.

Our example adds explicit coordination, data dependencies and policy questions.
It is not a claim that every repository uses these rules. Running the example
only saves local XML and basic HTML; it does not push, open/close PRs, merge,
delete branches, run CI or change repository settings.

## Source and adaptation

The source describes a branch-based workflow with changes, pull requests, review,
revision, merging and branch cleanup. Our model makes these local choices:

| Choice | Meaning in this example |
| --- | --- |
| Separate proposal step | Describe purpose, scope and acceptance criteria before implementation |
| Parallel checks and review | Both begin after a PR is opened and may overlap |
| Join completed results | Wait for both to complete, including failed checks or requested changes |
| Maintainer assessment | Assess results, conflicts and repository requirements before choosing an outcome |
| Three explicit outcomes | Ready to merge, revise, or close without merging; there is no default approval |
| Revision cycle | Update the existing PR, then repeat both checks and review for the revised commits |
| Closure branch | A modeled decision to stop, separate from successful integration |
| Post-merge cleanup | Delete the working branch only on the successful path |

This intentionally simplifies real asynchronous work. A PR could be closed before
checks finish, reviews can arrive in several rounds, and check runs can be cancelled
or rerun independently. Our model waits for both branches and treats a cancelled or
blocked run as needing a completed result before assessment. There are no timers,
interruptions, escalation rules, merge queues or deployment steps.

The readiness expressions are descriptive text. They are neither GitHub rules nor
automatically evaluated conditions. Approval counts, branch protection, conflicts
and checks must be interpreted by the maintainer in this specification. The model
does not verify that conditions are mutually exclusive or exhaustive.

## Flow

1. A change is proposed, described, implemented on a branch, committed and published.
2. Open a pull request, then start checks and review in parallel.
3. Collect their completed results and assess readiness.
4. Choose one of:
   - **Revise:** update and publish the change, then repeat both branches.
   - **Ready:** merge, clean up the branch, and reach the merged ending.
   - **Close:** close the PR without integrating the change and reach a separate ending.

The example has 18 elements, 19 sequence flows, 4 input definitions, 4 performers,
12 assignments, 8 data items, 31 data associations and 4 unresolved questions.
All activities have an explicit performer. Every element is structurally reachable
from the start and has a structural path to an ending; those are example-specific
checks, not new general validator guarantees.

## Roles and data

The change author describes, implements, publishes and revises the change, opens
the PR and handles successful branch cleanup. The reviewer supplies feedback.
The build system produces check results. The maintainer assesses readiness and
merges or closes the proposal. Roles do not imply GitHub permissions or user accounts.

Required inputs are repositoryName, targetBranch and changeDescription; relatedIssue
is optional. These are definitions, not values read from your actual repository.

Data items describe the change specification, branch, commits, PR, check results,
review feedback, merge assessment and final repository outcome. Read/write data
associations capture which activities consume or produce them; they do not add
control-flow edges.

The model has no commit-versioned data values. Repeated checks and reviews are
intended to apply to the latest commits, but that relationship is a documented
assumption. It does not enforce stale-approval invalidation or keep a revision audit
trail. Failed checks are results, rather than exceptions in an execution engine.

## Questions left open

- Who may review a change in a solo-maintained project?
- Which checks must pass for this repository?
- Which approvals, conflict checks and branch-protection rules define merge readiness?
- Should the branch be retained or deleted when a PR closes without merging?

These are WARNING/OPEN_QUESTION diagnostics. They do not make the structure invalid.
There is no implied approval policy for your repositories.

## Run it

From the Maven project root:

```sh
mvn clean install
java -cp target/classes local.dev.mavenproject3processmodel.examples.GitHubFlowExample
```

This saves and reloads `target/github-flow.process.xml`, then parses that saved XML
to generate `target/github-flow.html`. Open the HTML in a browser. It uses the same
basic read-only diagram and tables as onboarding. The diagram needs internet access
to load Mermaid; tables are embedded in the page. Browser rendering has not been
visually verified by the agent because local-file previews are blocked.

A custom output location is optional (its parent directory must exist):

```sh
java -cp target/classes local.dev.mavenproject3processmodel.examples.GitHubFlowExample target/another-change.process.xml
```

To view or validate the committed saved model without regenerating it from Java:

```sh
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar validate examples/github-flow.process.xml
java -jar target/mavenproject3processmodel-1.0-SNAPSHOT.jar html examples/github-flow.process.xml target/github-flow.html
```

In NetBeans, right-click `GitHubFlowExample.java` and choose **Run File**. **Run
Project (F6)** retains onboarding as its default. No action mapping was changed.

Committed files: [XML](github-flow.process.xml) and [basic HTML](github-flow.html).
The regression suite checks both against the specification and saved-XML export.
With the new example, the full suite passes **201 checks**.
