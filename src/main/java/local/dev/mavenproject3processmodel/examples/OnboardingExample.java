package local.dev.mavenproject3processmodel.examples;

import java.util.List;
import local.dev.mavenproject3processmodel.model.*;

/** Worked process specification; conditions are descriptive text, never evaluated. */
public final class OnboardingExample {
    private OnboardingExample() { }
    public static ProcessModel model() {
        return new ProcessModel(
            List.of(new ProcessDefinition("onboarding", "Employee onboarding",
                "Review a request, then prepare HR records, accounts and equipment in parallel.")),
            List.of(
                node("submitted", ElementType.START_EVENT, "Request received", "Onboarding request is available."),
                node("review", ElementType.USER_TASK, "Review request", "Check employee details and start date."),
                node("decision", ElementType.EXCLUSIVE_GATEWAY, "Review outcome", "Approved, declined or needs correction."),
                node("correct", ElementType.USER_TASK, "Request corrections", "Ask the requester to correct the information."),
                node("decline", ElementType.USER_TASK, "Notify requester of decline", "Communicate the decision."),
                node("declined", ElementType.END_EVENT, "Request declined", "No preparation work begins."),
                node("fork", ElementType.PARALLEL_GATEWAY, "Begin preparation", "Start three preparation branches."),
                node("hr", ElementType.USER_TASK, "Prepare HR records", "Create the employee record."),
                node("accounts", ElementType.SERVICE_TASK, "Provision accounts", "Describe the account-provisioning activity; no service is invoked."),
                node("equipment", ElementType.USER_TASK, "Prepare equipment", "Arrange equipment for the employee."),
                node("join", ElementType.PARALLEL_GATEWAY, "Preparation complete", "Intended to join all three preparation branches."),
                node("confirm", ElementType.USER_TASK, "Confirm readiness", "Confirm all preparation is complete."),
                node("ready", ElementType.END_EVENT, "Ready for first day", "Onboarding preparation is complete.")),
            List.of(
                flow("f01", "submitted", "review", "", null),
                flow("f02", "review", "decision", "", null),
                flow("f03", "decision", "correct", "Needs correction", "reviewResult == 'incomplete'"),
                flow("f04", "correct", "review", "Resubmitted", null),
                flow("f05", "decision", "decline", "Declined", "reviewResult == 'declined'"),
                flow("f06", "decline", "declined", "", null),
                flow("f07", "decision", "fork", "Approved", "reviewResult == 'approved'"),
                flow("f08", "fork", "hr", "", null),
                flow("f09", "fork", "accounts", "", null),
                flow("f10", "fork", "equipment", "", null),
                flow("f11", "hr", "join", "HR ready", null),
                flow("f12", "accounts", "join", "Accounts ready", null),
                flow("f13", "equipment", "join", "Equipment ready", null),
                flow("f14", "join", "confirm", "", null),
                flow("f15", "confirm", "ready", "", null)),
            List.of(
                new ProcessInput("onboarding", "employeeName", InputDataType.STRING, true),
                new ProcessInput("onboarding", "startDate", InputDataType.DATE, true),
                new ProcessInput("onboarding", "remoteWorker", InputDataType.BOOLEAN, false)),
            List.of(
                new PerformerDefinition("hrRole", "HR coordinator", PerformerType.HUMAN_ROLE, "Reviews requests and prepares HR records."),
                new PerformerDefinition("managerRole", "Hiring manager", PerformerType.HUMAN_ROLE, "Supplies corrections and confirms readiness."),
                new PerformerDefinition("itRole", "IT coordinator", PerformerType.HUMAN_ROLE, "Prepares equipment."),
                new PerformerDefinition("identitySystem", "Identity system", PerformerType.SYSTEM, "Provisioning system to be confirmed.")),
            List.of(
                new ActivityAssignment("review", "hrRole"),
                new ActivityAssignment("correct", "managerRole"),
                new ActivityAssignment("decline", "hrRole"),
                new ActivityAssignment("hr", "hrRole"),
                new ActivityAssignment("accounts", "identitySystem"),
                new ActivityAssignment("equipment", "itRole"),
                new ActivityAssignment("confirm", "managerRole")),
            List.of(
                new ProcessDataItem("requestData", "onboarding", "Onboarding request", "Employee details and proposed start date."),
                new ProcessDataItem("hrData", "onboarding", "HR record", "Prepared employee record."),
                new ProcessDataItem("accountData", "onboarding", "Account record", "Provisioned account details."),
                new ProcessDataItem("equipmentData", "onboarding", "Equipment record", "Equipment allocation details.")),
            List.of(
                new ActivityDataAssociation("review", "requestData", DataDirection.INPUT),
                new ActivityDataAssociation("correct", "requestData", DataDirection.OUTPUT),
                new ActivityDataAssociation("hr", "requestData", DataDirection.INPUT),
                new ActivityDataAssociation("accounts", "requestData", DataDirection.INPUT),
                new ActivityDataAssociation("equipment", "requestData", DataDirection.INPUT),
                new ActivityDataAssociation("hr", "hrData", DataDirection.OUTPUT),
                new ActivityDataAssociation("accounts", "accountData", DataDirection.OUTPUT),
                new ActivityDataAssociation("equipment", "equipmentData", DataDirection.OUTPUT),
                new ActivityDataAssociation("confirm", "hrData", DataDirection.INPUT),
                new ActivityDataAssociation("confirm", "accountData", DataDirection.INPUT),
                new ActivityDataAssociation("confirm", "equipmentData", DataDirection.INPUT)),
            List.of(
                new ProcessOpenQuestion("qStartDate", "onboarding", null, "Who approves exceptions to the requested start date?"),
                new ProcessOpenQuestion("qIdentity", "onboarding", "accounts", "Which identity provider will provision the accounts?")));
    }
    private static ProcessElement node(String id, ElementType type, String name, String description) {
        return new ProcessElement(id, "onboarding", type, name, description, null);
    }
    private static ProcessRelationship flow(String id, String source, String target, String name, String condition) {
        return new ProcessRelationship(id, RelationshipType.SEQUENCE_FLOW, source, target, name, condition, false);
    }
}
