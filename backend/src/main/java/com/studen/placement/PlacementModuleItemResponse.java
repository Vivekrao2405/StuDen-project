package com.studen.placement;

import java.util.UUID;

// One module item, flattened to (targetId, title) so a caller does not need to know which of the
// three content tables it came from to render it.
public record PlacementModuleItemResponse(
        UUID id,
        ModuleItemType itemType,
        UUID targetId,
        String title,
        int displayOrder,
        boolean required) {

    private static final int PREVIEW_LENGTH = 140;

    public static PlacementModuleItemResponse from(PlacementModuleItem item) {
        UUID targetId;
        String title;
        switch (item.getItemType()) {
            case QUESTION -> {
                targetId = item.getQuestion().getId();
                String text = item.getQuestion().getQuestionText();
                title = text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "…" : text;
            }
            case PRACTICAL_ASSESSMENT -> {
                targetId = item.getPracticalAssessment().getId();
                title = item.getPracticalAssessment().getTitle();
            }
            case RESOURCE -> {
                targetId = item.getResource().getId();
                title = item.getResource().getTitle();
            }
            default -> throw new IllegalStateException("Unsupported module item type: " + item.getItemType());
        }
        return new PlacementModuleItemResponse(item.getId(), item.getItemType(), targetId, title,
                item.getDisplayOrder(), item.isRequired());
    }
}
