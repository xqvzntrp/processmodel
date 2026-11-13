package local.dev.mavenproject3processmodel.model;

public record ProcessRelationship(
        String relationshipId,
        RelationshipType relationshipType,
        String sourceElementId,
        String targetElementId,
        String name,
        String conditionExpression,
        boolean isDefault
) {}
