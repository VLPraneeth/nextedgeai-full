package com.syncari.core.model.pagination;

/**
 * Defines the pagination strategy to use for querying data.
 *
 * CURSOR: High-performance cursor-based pagination using _id field.
 *         Best for large datasets with default sorting.
 *
 * OFFSET: Offset-based pagination using skip/limit.
 *         Supports custom field sorting but has performance tradeoffs for large offsets.
 *         Suitable for datasets where users paginate only a few pages (typical UI usage).
 */
public enum PaginationType {
    /**
     * Cursor-based pagination - uses _id as cursor for high performance.
     * Default mode for backward compatibility.
     */
    CURSOR,

    /**
     * Offset-based pagination - uses skip/limit for flexible sorting.
     * Used when custom field sorting is required.
     */
    OFFSET
}
