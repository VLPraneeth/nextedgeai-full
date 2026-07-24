package com.syncari.core.model.pagination;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class PageCursor {
	String cursor;
	int pageNumber;
	PageDirection direction;
	int pageSize;
	String orderByField;
	Boolean ascending;
	PaginationType paginationType;
	Integer offset;

	public PageCursor(String cursor, PageDirection direction, int pageSize) {
		this.cursor = cursor;
		this.direction = direction;
		this.pageSize = pageSize;
		// paginationType stays null for backward compatibility (defaults to CURSOR)
	}

	public PageCursor(){

	}

	public PageCursor(int pageNumber, int pageSize) {
		this.pageNumber = pageNumber;
		this.pageSize = pageSize;
		// paginationType stays null for backward compatibility (defaults to CURSOR)
	}

	public boolean isForward() {
	    return direction == PageDirection.next || direction == null;
	}

	public boolean hasCustomOrdering() {
	    return orderByField != null && !orderByField.trim().isEmpty();
	}

	public boolean isOffsetPagination() {
	    return paginationType == PaginationType.OFFSET;
	}

	public boolean isCursorPagination() {
	    return paginationType == null || paginationType == PaginationType.CURSOR;
	}

	public int getOffsetValue() {
	    if (offset == null && pageNumber > 0) {
	        offset = pageNumber * pageSize;
	    }
	    return offset != null ? offset : 0;
	}

	public static PageCursor offsetBased(int pageNumber, PageDirection direction, int pageSize, String orderByField, Boolean ascending) {
	    PageCursor pc = new PageCursor();
	    pc.setPageNumber(pageNumber);
	    pc.setDirection(direction);
	    pc.setPageSize(pageSize);
	    pc.setOrderByField(orderByField);
	    pc.setAscending(ascending);
	    pc.setPaginationType(PaginationType.OFFSET);
	    pc.setOffset((pageNumber - 1) * pageSize);
	    return pc;
	}

	public void validate() {
		if(pageSize == 0) {
			throw new RuntimeException("page_size_required");
		}
		if(pageSize > Page.MAX_PAGE_SIZE) {
			throw new RuntimeException("page_size_too_large");
		}
	}
}
