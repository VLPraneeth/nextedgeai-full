package com.syncari.core.insights.provider;

public enum InsightsProvider {
    THOUGHTSPOT{
        @Override
        public String endpoint() {
            return "https://syncari.thoughtspot.cloud/";
        }
    },SYNCARI;

    public String endpoint() {
        return null;
    }
}
