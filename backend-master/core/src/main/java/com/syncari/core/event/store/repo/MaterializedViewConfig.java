package com.syncari.core.event.store.repo;

public class MaterializedViewConfig {
    private final String tableName;
    private final String viewName;
    private final String sql;

    public MaterializedViewConfig(String tableName, String viewName, String sql) {
        this.tableName = tableName;
        this.viewName = viewName;
        this.sql = sql;
    }

    public String getTableName() {
        return tableName;
    }

    public String getViewName() {
        return viewName;
    }

    public String getSql() {
        return sql;
    }
}
