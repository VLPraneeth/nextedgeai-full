package com.syncari.api.rest.controllers.data.studio;

public enum ScoreStatus {
    na("Not Available"),
    unpublished("Publish the entity to see score"),
    available("Available");
    
    public final String label;

    private ScoreStatus(String label) {
        this.label = label;
    }
}
