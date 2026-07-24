import { useCallback, useState } from 'react';

import { DefaultPageSizeOptions } from 'components/AgTable';
import { PaginationDirection } from 'components/AgTable/Pagination';

export interface SortColumn {
  columnName: string;
  ascending: boolean;
}

export interface PageInfo {
  start: string | null;
  end: string | null;
  hasMore: boolean;
  sorting?: SortColumn[];
}

export interface PaginationParams {
  count: number;
  cursor: string | null;
  direction: 'next' | 'previous';
}

export const DEFAULT_PAGE_SIZE = DefaultPageSizeOptions[3];

interface UseCursorPaginationConfig {
  initialCursor?: string;
  initialPageSize?: number;
}

interface UseCursorPaginationResult {
  cursor: string | undefined;
  direction: PaginationDirection;
  pageSize: number;
  setPageSize: (count: number) => void;
  resetPagination: () => void;
  onRequestNextPage: (cursor: string, count?: number) => void;
  onRequestPrevPage: (cursor: string, count?: number) => void;
  onGotoPage?: (newPage: number) => void;
  firstPageStartCursor: string | null;
  setFirstPageStartCursor: (pageInfo?: PageInfo) => void;
}

export const useCursorPagination = (options?: UseCursorPaginationConfig): UseCursorPaginationResult => {
  const { initialCursor, initialPageSize = DEFAULT_PAGE_SIZE } = options || {};

  const [cursor, setCursor] = useState<string | undefined>(initialCursor);
  const [direction, setDirection] = useState<PaginationDirection>('next');
  const [pageSize, setPageSize] = useState(initialPageSize);

  // this is used to naively track when we're on the first page - this will be
  // unreliable with client configured sorting
  const [firstPageStartCursor, setFirstPageStartCursor] = useState<string | null>(null);

  const resetPagination = useCallback(() => {
    setCursor(initialCursor);
    setDirection('next');
    setFirstPageStartCursor(null);
    setPageSize(initialPageSize);
  }, [initialCursor, initialPageSize]);

  const onRequestNextPage = useCallback((cur: string, count?: number) => {
    setCursor(cur);
    setDirection('next');

    if (count) {
      setPageSize(count);
    }
  }, []);

  const onGotoPage = useCallback(
    (newPage: number) => {
      setCursor(((newPage - 1) * pageSize).toString());
      setDirection('goTo');
    },
    [pageSize]
  );

  const onRequestPrevPage = useCallback((cur: string, count?: number) => {
    setCursor(cur);
    setDirection('previous');

    if (count) {
      setPageSize(count);
    }
  }, []);

  /**
   * When reversing through pages, we don't get an indication from the server
   * if there is a previous page. So, we'll naiively track that here. When
   * given pageInfo, we'll track the very first start cursor we see, ignoring the rest.
   *
   * We will use this to flag `hasPrevious` by comparing this cursor with the cursors in
   * the result sets.
   */
  const onSetFirstPageStartCursor = useCallback(
    (pageInfo?: PageInfo) => {
      if (firstPageStartCursor || !pageInfo?.start) {
        return;
      }
      setFirstPageStartCursor(pageInfo.start);
    },
    [firstPageStartCursor]
  );

  return {
    cursor,
    direction,
    onRequestNextPage,
    onRequestPrevPage,
    resetPagination,
    onGotoPage,
    pageSize,
    setPageSize,
    firstPageStartCursor,
    setFirstPageStartCursor: onSetFirstPageStartCursor,
  };
};

interface UseOffsetBasedPaginationConfig {
  initialPage?: number;
  initialPageSize?: number;
}

interface UseOffsetBasedPaginationResult {
  page: number;
  pageSize: number;
  setPageSize: (count: number) => void;
  resetPagination: () => void;
  onRequestNextPage: () => void;
  onRequestPrevPage: () => void;
  onGotoPage?: (newPage: number) => void;
}

/**
 * Hook for offset-based pagination (used when sorting is active)
 * Instead of cursors, this manages page numbers directly
 */
export const useOffsetBasedPagination = (options?: UseOffsetBasedPaginationConfig): UseOffsetBasedPaginationResult => {
  const { initialPage = 1, initialPageSize = DEFAULT_PAGE_SIZE } = options || {};

  const [page, setPage] = useState(initialPage);
  const [pageSize, setPageSize] = useState(initialPageSize);

  const resetPagination = useCallback(() => {
    setPage(initialPage);
    setPageSize(initialPageSize);
  }, [initialPage, initialPageSize]);

  const onRequestNextPage = useCallback(() => {
    setPage((prev) => prev + 1);
  }, []);

  const onRequestPrevPage = useCallback(() => {
    setPage((prev) => Math.max(1, prev - 1));
  }, []);

  const onGotoPage = useCallback((newPage: number) => {
    setPage(Math.max(1, newPage));
  }, []);

  return {
    page,
    pageSize,
    setPageSize,
    resetPagination,
    onRequestNextPage,
    onRequestPrevPage,
    onGotoPage,
  };
};
