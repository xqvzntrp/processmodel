package local.dev.mavenproject3processmodel.examples;

import java.util.List;
import local.dev.mavenproject3processmodel.model.*;

/**
 * Our specification adapted from https://docs.github.com/en/get-started/using-github/github-flow.
 * Parallel review/checks, a combined assessment and a closure path are explicit local choices.
 * This class describes a process; it performs no Git, GitHub, CI or review operations.
 */
public final class GitHubFlowExample {
    private GitHubFlowExample() { }

    public static ProcessModel model() {
        return new ProcessModel(
            List.of(new ProcessDefinition("githubFlow", "Software change: proposal to merge",
                "Adapted from GitHub Flow. Review and checks are coordinated for the latest commits; merge, revise or close after assessment.")),
            List.of(
                node("proposed", ElementType.START_EVENT, "Change proposed", "A proposed software change is available."),
                node("describe", ElementType.USER_TASK, "Describe the change", "Record its purpose, scope and acceptance criteria."),
                node("branch", ElementType.USER_TASK, "Create a branch", "Choose a branch for this change."),
                node("implement", ElementType.USER_TASK, "Implement the change", "Prepare the change and its tests."),
                node("publish", ElementType.USER_TASK, "Commit & push changes", "Describe publication of the proposed commits; no push is performed by this model."),
                node("openPr", ElementType.USER_TASK, "Open a pull request", "Explain the change and request feedback."),
                node("fork", ElementType.PARALLEL_GATEWAY, "Begin checks and review", "Our adaptation allows both branches to proceed in parallel."),
                node("checks", ElementType.SERVICE_TASK, "Run automated checks", "Record a completed result, including failure; checks must finish for this revision."),
                node("review", ElementType.USER_TASK, "Review the change", "Record approval, requested changes or other feedback for this revision."),
                node("join", ElementType.PARALLEL_GATEWAY, "Collect both results", "Our adaptation waits for both review and checks, even if one reports a problem."),
                node("assess", ElementType.USER_TASK, "Assess merge readiness", "Consider both results, conflicts, repository rules and whether to continue."),
                node("decision", ElementType.EXCLUSIVE_GATEWAY, "Next action", "Select exactly one descriptive outcome: ready, revise or close."),
                node("revise", ElementType.USER_TASK, "Revise, commit & push", "Address feedback, failed checks or conflicts; repeat review and checks for the updated commits."),
                node("merge", ElementType.USER_TASK, "Merge the pull request", "Describe the authorized merge; no repository changes are made by this model."),
                node("cleanup", ElementType.USER_TASK, "Delete the completed branch", "Describe post-merge cleanup; this application deletes no branches."),
                node("merged", ElementType.END_EVENT, "Change merged", "The change is integrated and branch cleanup is complete."),
                node("closePr", ElementType.USER_TASK, "Close without merging", "Our adaptation records the decision to stop pursuing the change."),
                node("closed", ElementType.END_EVENT, "Change closed without merge", "The proposed change is not integrated.")),
            List.of(
                flow("gf01", "proposed", "describe", "", null),
                flow("gf02", "describe", "branch", "", null),
                flow("gf03", "branch", "implement", "", null),
                flow("gf04", "implement", "publish", "", null),
                flow("gf05", "publish", "openPr", "", null),
                flow("gf06", "openPr", "fork", "", null),
                flow("gf07", "fork", "checks", "", null),
                flow("gf08", "fork", "review", "", null),
                flow("gf09", "checks", "join", "Checks completed", null),
                flow("gf10", "review", "join", "Review completed", null),
                flow("gf11", "join", "assess", "", null),
                flow("gf12", "assess", "decision", "", null),
                flow("gf13", "decision", "revise", "Revise", "mergeDecision == 'revise'"),
                flow("gf14", "revise", "fork", "Updated commits", null),
                flow("gf15", "decision", "merge", "Ready to merge", "mergeDecision == 'ready'"),
                flow("gf16", "merge", "cleanup", "", null),
                flow("gf17", "cleanup", "merged", "", null),
                flow("gf18", "decision", "closePr", "Close without merging", "mergeDecision == 'close'"),
                flow("gf19", "closePr", "closed", "", null)),
            List.of(
                new ProcessInput("githubFlow", "repositoryName", InputDataType.STRING, true),
                new ProcessInput("githubFlow", "targetBranch", InputDataType.STRING, true),
                new ProcessInput("githubFlow", "changeDescription", InputDataType.STRING, true),
                new ProcessInput("githubFlow", "relatedIssue", InputDataType.STRING, false)),
            List.of(
                new PerformerDefinition("authorRole", "Change author", PerformerType.HUMAN_ROLE, "Authors the change and responds to feedback."),
                new PerformerDefinition("reviewerRole", "Reviewer", PerformerType.HUMAN_ROLE, "Reviews the proposed revision."),
                new PerformerDefinition("maintainerRole", "Maintainer", PerformerType.HUMAN_ROLE, "Assesses readiness and decides whether to merge or close."),
                new PerformerDefinition("ciSystem", "Build system", PerformerType.SYSTEM, "Produces check results for the proposed revision.")),
            List.of(
                new ActivityAssignment("describe", "authorRole"),
                new ActivityAssignment("branch", "authorRole"),
                new ActivityAssignment("implement", "authorRole"),
                new ActivityAssignment("publish", "authorRole"),
                new ActivityAssignment("openPr", "authorRole"),
                new ActivityAssignment("checks", "ciSystem"),
                new ActivityAssignment("review", "reviewerRole"),
                new ActivityAssignment("assess", "maintainerRole"),
                new ActivityAssignment("revise", "authorRole"),
                new ActivityAssignment("merge", "maintainerRole"),
                new ActivityAssignment("cleanup", "authorRole"),
                new ActivityAssignment("closePr", "maintainerRole")),
            List.of(
                new ProcessDataItem("changeSpec", "githubFlow", "Change specification", "Purpose and acceptance criteria."),
                new ProcessDataItem("branchRecord", "githubFlow", "Branch record", "Working branch and its target."),
                new ProcessDataItem("commitSet", "githubFlow", "Proposed commits", "The latest revision under review; revision identity is descriptive, not enforced."),
                new ProcessDataItem("pullRequest", "githubFlow", "Pull request", "Proposal, discussion and current status."),
                new ProcessDataItem("checkResults", "githubFlow", "Check results", "Completed pass/fail results for the latest revision."),
                new ProcessDataItem("reviewFeedback", "githubFlow", "Review feedback", "Reviewer response for the latest revision."),
                new ProcessDataItem("mergeAssessment", "githubFlow", "Merge assessment", "Ready, revise or close, with a reason."),
                new ProcessDataItem("repositoryOutcome", "githubFlow", "Repository outcome", "Merged and cleaned up, or closed without merging.")),
            List.of(
                data("describe", "changeSpec", DataDirection.OUTPUT),
                data("branch", "changeSpec", DataDirection.INPUT),
                data("branch", "branchRecord", DataDirection.OUTPUT),
                data("implement", "changeSpec", DataDirection.INPUT),
                data("implement", "branchRecord", DataDirection.INPUT),
                data("implement", "commitSet", DataDirection.OUTPUT),
                data("publish", "commitSet", DataDirection.INPUT),
                data("openPr", "commitSet", DataDirection.INPUT),
                data("openPr", "pullRequest", DataDirection.OUTPUT),
                data("checks", "commitSet", DataDirection.INPUT),
                data("checks", "checkResults", DataDirection.OUTPUT),
                data("review", "commitSet", DataDirection.INPUT),
                data("review", "pullRequest", DataDirection.INPUT),
                data("review", "reviewFeedback", DataDirection.OUTPUT),
                data("assess", "checkResults", DataDirection.INPUT),
                data("assess", "reviewFeedback", DataDirection.INPUT),
                data("assess", "pullRequest", DataDirection.INPUT),
                data("assess", "mergeAssessment", DataDirection.OUTPUT),
                data("revise", "mergeAssessment", DataDirection.INPUT),
                data("revise", "checkResults", DataDirection.INPUT),
                data("revise", "reviewFeedback", DataDirection.INPUT),
                data("revise", "commitSet", DataDirection.OUTPUT),
                data("revise", "pullRequest", DataDirection.OUTPUT),
                data("merge", "mergeAssessment", DataDirection.INPUT),
                data("merge", "commitSet", DataDirection.INPUT),
                data("merge", "pullRequest", DataDirection.OUTPUT),
                data("cleanup", "branchRecord", DataDirection.INPUT),
                data("cleanup", "repositoryOutcome", DataDirection.OUTPUT),
                data("closePr", "mergeAssessment", DataDirection.INPUT),
                data("closePr", "pullRequest", DataDirection.OUTPUT),
                data("closePr", "repositoryOutcome", DataDirection.OUTPUT)),
            List.of(
                new ProcessOpenQuestion("qSoloReview", "githubFlow", "review", "Who may review a change in a solo-maintained project?"),
                new ProcessOpenQuestion("qRequiredChecks", "githubFlow", "checks", "Which checks must pass for this repository?"),
                new ProcessOpenQuestion("qMergeRules", "githubFlow", "assess", "Which approvals, conflict checks and branch-protection rules define readiness to merge?"),
                new ProcessOpenQuestion("qClosedBranch", "githubFlow", "closePr", "Should the branch be retained or deleted after closure without merging?")));
    }
    private static ProcessElement node(String id, ElementType type, String name, String description) {
        return new ProcessElement(id, "githubFlow", type, name, description, null);
    }
    private static ProcessRelationship flow(String id, String source, String target, String name, String condition) {
        return new ProcessRelationship(id, RelationshipType.SEQUENCE_FLOW, source, target, name, condition, false);
    }
    private static ActivityDataAssociation data(String activity, String item, DataDirection direction) {
        return new ActivityDataAssociation(activity, item, direction);
    }
}
