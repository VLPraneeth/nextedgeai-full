package com.syncari.core.model.insights;

import java.util.HashMap;
import java.util.Map;

public class InsightsDBStaticIndexes {

    public static final Map<String, String> indexes = new HashMap<>();
    private static final String OPPTY_INDEX = "create index if not exists oppt_insights_idx_isclosed on %s.\"opportunity\" (isclosed, iswon, stagename)";
    private static final String LEAD_INDEX = "create index if not exists lead_insights_idx_status on %s.\"lead\" (status)";

    static {
        indexes.put("OPPTY_INDEX1", OPPTY_INDEX);
        indexes.put("LEAD_INDEX1", LEAD_INDEX);
    }
}
