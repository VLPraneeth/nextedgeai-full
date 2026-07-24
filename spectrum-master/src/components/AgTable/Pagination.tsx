import Icon from 'antd/lib/icon';
import Input from 'antd/lib/input';
import cx from 'classnames';
import * as React from 'react';

import { ReactComponent as RightChevronIcon } from 'assets/icons/chevron-right.svg';
import Button from 'components/Button';
import { HStack } from 'components/layout';
import SelectInput from 'components/SelectInput';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { wrapIcon } from 'utils/IconUtils';

import { DefaultPageSizeOptions } from './constants';

import './Pagination.less';

const tn = tNamespaced('AgTable.Pagination');

const LeftChevronIcon: typeof RightChevronIcon = ({ style, ...props }) => (
  <RightChevronIcon
    style={{
      ...style,
      transform: 'rotateZ(180deg)',
    }}
    {...props}
  />
);

interface PagerProps {
  hasPrevious?: boolean;
  hasNext?: boolean;
  onRequestPreviousPage: (evt: React.MouseEvent<HTMLButtonElement>) => void;
  onRequestNextPage: (evt: React.MouseEvent<HTMLButtonElement>) => void;
  pageSize?: number;
  onPageSizeChange?: (newPageSize: number) => void;
  onGotoPageChange?: (newPageNumber: number) => void;
  pageSizeOptions?: number[];
  allowPageSizeChange?: boolean;
  totalRecords?: number;
  allowGotoPage?: boolean;
  pageInfo?: CursorPageInfo;
  simple?: boolean;
  isLoading?: boolean;
  splitLayout?: boolean;
  currentRecordCount?: number;
}

const Pager = ({
  hasPrevious = false,
  hasNext = false,
  onRequestNextPage,
  onRequestPreviousPage,
  pageSize = DefaultPageSizeOptions[0], // TODO: bump this to 25 (DefaultPageSizeOptions[1])
  onPageSizeChange,
  allowPageSizeChange,
  allowGotoPage,
  onGotoPageChange,
  simple,
  pageInfo = defaultCursorPageInfo,
  pageSizeOptions = DefaultPageSizeOptions,
  totalRecords,
  isLoading,
  splitLayout = false,
  currentRecordCount = 0,
}: PagerProps) => {
  if (allowPageSizeChange && !onPageSizeChange) {
    throw new Error('onPageSizeChange is required if allowPageSizeChange is set.');
  }
  const [currentPage, setCurrentPage] = React.useState(1);
  const totalPage = Math.ceil(pageInfo?.totalCount ? pageInfo.totalCount / pageSize : 0);

  React.useEffect(() => {
    if (pageInfo?.start) {
      // Cursor-based pagination: calculate page from start cursor
      setCurrentPage(Math.ceil((parseInt(pageInfo.start) + 1) / pageSize));
    } else if (pageInfo?.pageNumber !== undefined) {
      // Offset-based pagination: sync with pageNumber from API (add 1 since pageNumber is 0-indexed)
      setCurrentPage(pageInfo.pageNumber + 1);
    }
  }, [pageInfo?.start, pageInfo?.pageNumber, pageSize]);

  const onGotoPageChangeHandler = React.useCallback(
    (evt: React.ChangeEvent<HTMLInputElement>) => {
      const page = parseInt(evt.target.value);
      if (page <= 0 || isNaN(page)) {
        setCurrentPage(1);
        return;
      } else if (page > totalPage) {
        setCurrentPage(totalPage);
        return;
      }
      setCurrentPage(page);
    },
    [totalPage]
  );

  const buttonType = simple ? 'link' : 'default';

  // Render page size selector (for default layout)
  const pageSizeSelector =
    allowPageSizeChange && onPageSizeChange ? (
      <SelectInput
        disabled={isLoading}
        value={pageSize.toString()}
        options={pageSizeOptions.map((pageSizeOption) => ({
          title: tn('records_per_page', { count: pageSizeOption }),
          label: tn('records_per_page', { count: pageSizeOption }),
          value: pageSizeOption.toString(),
        }))}
        onChange={(value) => {
          onPageSizeChange(+value);
        }}
      />
    ) : null;

  // Render page size selector for split layout
  const splitLayoutPageSizeSelector =
    allowPageSizeChange && onPageSizeChange ? (
      <HStack spacing="xs" align="center" className="split-layout-page-size-selector">
        <span>Items per page:</span>
        <SelectInput
          disabled={isLoading}
          value={pageSize.toString()}
          options={pageSizeOptions.map((pageSizeOption) => ({
            title: pageSizeOption.toString(),
            label: pageSizeOption.toString(),
            value: pageSizeOption.toString(),
          }))}
          onChange={(value) => {
            onPageSizeChange(+value);
          }}
        />
      </HStack>
    ) : null;

  // Render goto page input (for default layout)
  const gotoPageInput = allowGotoPage ? (
    <span>
      <Input
        size="small"
        disabled={isLoading}
        className="syncari-ag-table-pagination-container__goto-page"
        value={currentPage}
        onChange={onGotoPageChangeHandler}
        style={{ width: 40, marginRight: 6 }}
        onKeyDown={(evt: React.KeyboardEvent<HTMLInputElement>) =>
          evt.key === 'Enter' && onGotoPageChange?.(currentPage)
        }
        onBlur={() => onGotoPageChange?.(currentPage)}
      />
      / {totalPage}
    </span>
  ) : null;

  // Calculate page info for display
  const totalCount = (pageInfo?.filteredCount ? pageInfo?.filteredCount : pageInfo?.totalCount) || 0;
  const currentPageNum = pageInfo?.pageNumber ?? 0;

  // Calculate start and end record numbers (1-based) using actual record count
  // When we have actual records, use those for accurate display
  // For zero records, show "0-0 of 0"
  const actualRecordCount = currentRecordCount !== undefined ? currentRecordCount : pageSize;
  const hasActualRecords = currentRecordCount !== undefined ? currentRecordCount > 0 : true;
  const startRecord = hasActualRecords ? currentPageNum * pageSize + 1 : 0;
  const endRecord = hasActualRecords ? currentPageNum * pageSize + actualRecordCount : 0;

  // Display format: "1-10" not "1-10 of unknown" when we don't have totalCount
  const hasTotal = totalCount > 0;

  // Render navigation buttons
  const navigationButtons = (
    <HStack spacing="md" className="split-layout-button-wrapper">
      {splitLayout && (
        <span className="page-info-text">
          {startRecord}-{endRecord}
          {hasTotal || !hasActualRecords ? ` of ${totalCount}` : ` of ${pageSize}`}
        </span>
      )}
      <Button type={buttonType} onClick={onRequestPreviousPage} disabled={!hasPrevious || isLoading}>
        <Icon component={wrapIcon(LeftChevronIcon)} />
      </Button>
      <Button type={buttonType} onClick={onRequestNextPage} disabled={!hasNext || isLoading}>
        <Icon component={wrapIcon(RightChevronIcon)} />
      </Button>
    </HStack>
  );

  return (
    <div
      className={cx(
        'syncari-ag-table-pagination-container',
        simple ? 'syncari-ag-table-pagination-container--simple' : null,
        splitLayout ? 'syncari-ag-table-pagination-container--split' : null
      )}>
      {splitLayout ? (
        <>
          <HStack spacing="xs" align="center" className="pagination-left">
            {totalRecords && <div className="total-records">{tc('total_records', { totalRecords })}</div>}
            {splitLayoutPageSizeSelector}
          </HStack>
          <HStack spacing="xs" align="center" className="pagination-right">
            {navigationButtons}
          </HStack>
        </>
      ) : (
        <HStack spacing={simple ? 'z' : 'md'}>
          {totalRecords && <div className="total-records">{tc('total_records', { totalRecords })}</div>}
          <Button type={buttonType} onClick={onRequestPreviousPage} disabled={!hasPrevious || isLoading}>
            <Icon component={wrapIcon(LeftChevronIcon)} />
          </Button>
          {pageSizeSelector}
          {gotoPageInput}
          <Button type={buttonType} onClick={onRequestNextPage} disabled={!hasNext || isLoading}>
            <Icon component={wrapIcon(RightChevronIcon)} />
          </Button>
        </HStack>
      )}
    </div>
  );
};

export type PaginationDirection = 'next' | 'previous' | 'goTo';

export interface CursorPageInfo {
  start: string | null;
  end: string | null;
  hasMore: boolean;
  hasPrevious?: boolean;
  totalCount?: number;
  pageNumber?: number;
  filteredCount?: number;
}

export interface PaginationProps
  extends Omit<PagerProps, 'onRequestNextPage' | 'onRequestPreviousPage' | 'hasNext' | 'hasPrevious'> {
  pageInfo?: CursorPageInfo;
  allowGotoPage?: boolean;
  onRequestNextPage?: (cursor: string, count?: number) => void;
  onRequestPreviousPage?: (cursor: string, count?: number) => void;
  onGotoPageChange?: (newPage: number) => void;
  isLoading?: boolean;
  splitLayout?: boolean;
  currentRecordCount?: number;
  isOffsetBasedPagination?: boolean;
}

const defaultCursorPageInfo = {
  start: null,
  end: null,
  hasMore: false,
  hasPrevious: false,
  pageNumber: 1,
};

const CursorBasedPagination = ({
  pageInfo = defaultCursorPageInfo,
  onRequestNextPage,
  onRequestPreviousPage,
  onGotoPageChange,
  pageSize,
  allowGotoPage,
  splitLayout,
  currentRecordCount,
  isOffsetBasedPagination,
  ...props
}: PaginationProps) => {
  const { start, end, hasMore, hasPrevious } = pageInfo;

  // Client-side page number tracking
  const [currentPage, setCurrentPage] = React.useState(0);
  const prevStartRef = React.useRef<string | null>(start);
  const prevEndRef = React.useRef<string | null>(end);

  // Sync currentPage with pageInfo.pageNumber from API response
  React.useEffect(() => {
    if (pageInfo?.pageNumber !== undefined && pageInfo.pageNumber !== currentPage) {
      setCurrentPage(pageInfo.pageNumber);
    }
  }, [pageInfo?.pageNumber]);

  // Reset page number when cursors reset (e.g., new search/filter)
  React.useEffect(() => {
    // If we had a start cursor before and now we don't, reset to page 0
    if (prevStartRef.current && !start && !end) {
      setCurrentPage(0);
    }
    // If start cursor changed significantly (not just next/prev), reset
    else if (start !== prevStartRef.current && start !== prevEndRef.current) {
      // This might be a new search/filter, check if it looks like a reset
      if (!hasPrevious && start) {
        setCurrentPage(0);
      }
    }
    prevStartRef.current = start;
    prevEndRef.current = end;
  }, [start, end, hasPrevious]);

  const handleRequestPreviousPage = () => {
    if (start) {
      setCurrentPage((prev) => Math.max(0, prev - 1));
      onRequestPreviousPage?.(start, pageSize);
    } else if (isOffsetBasedPagination && currentPage > 0) {
      // For offset-based pagination without cursor, use the offset as cursor
      const newPage = currentPage - 1;
      const offset = newPage * (pageSize || 0);
      setCurrentPage(newPage);
      // Pass the offset as string to trigger API call with offset-based pagination
      onRequestPreviousPage?.(offset.toString(), pageSize);
    }
  };

  const handleRequestNextPage = () => {
    if (end) {
      setCurrentPage((prev) => prev + 1);
      onRequestNextPage?.(end, pageSize);
    } else if (isOffsetBasedPagination) {
      // For offset-based pagination without cursor, use the offset as cursor
      const newPage = currentPage + 1;
      const offset = newPage * (pageSize || 0);
      setCurrentPage(newPage);
      onRequestNextPage?.(offset.toString(), pageSize);
    }
  };

  const effectivePageInfo = {
    ...pageInfo,
    pageNumber: currentPage,
  };

  // Calculate hasPrevious based on pagination type
  const calculateHasPrevious = () => {
    if (isOffsetBasedPagination) {
      // For offset-based pagination (when sorting is applied)
      // If start is null, check if we can go to previous page based on currentPage
      if (!start && currentPage > 0) {
        return true;
      }
      // If start exists, use the traditional hasPrevious logic
      return Boolean(start && hasPrevious);
    }
    // For cursor-based pagination, use the traditional logic
    return Boolean(start && hasPrevious);
  };

  // Calculate hasNext based on pagination type
  const calculateHasNext = () => {
    if (isOffsetBasedPagination) {
      // For offset-based pagination (when sorting is applied)
      // If end is null, check if we can go to next page based on filteredCount
      if (!end && pageInfo?.filteredCount && pageSize) {
        const nextPageStartRecord = (currentPage + 1) * pageSize;
        return nextPageStartRecord < pageInfo.filteredCount;
      }
      // If end exists, use hasMore
      return Boolean(hasMore && end);
    }
    // For cursor-based pagination, use the traditional logic
    return Boolean(hasMore && end);
  };

  return (
    <Pager
      hasPrevious={calculateHasPrevious()}
      pageInfo={effectivePageInfo}
      hasNext={calculateHasNext()}
      onRequestPreviousPage={handleRequestPreviousPage}
      onRequestNextPage={handleRequestNextPage}
      onGotoPageChange={onGotoPageChange}
      pageSize={pageSize}
      allowGotoPage={allowGotoPage}
      splitLayout={splitLayout}
      currentRecordCount={currentRecordCount}
      {...props}
    />
  );
};

export interface PageBasedPageInfo {
  pageNumber: number;
  maxPageNumber: number;
}

export interface PageBasedPaginationProps
  extends Omit<PagerProps, 'onRequestNextPage' | 'onRequestPreviousPage' | 'hasNext' | 'hasPrevious' | 'pageInfo'> {
  pageInfo?: PageBasedPageInfo;
  onRequestPreviousPage: (pageNumber: number, count?: number) => void;
  onRequestNextPage: (pageNumber: number, count?: number) => void;
}

/* this gives us an empty Pager instead of a missing pager */
const defaultPageBasedPageInfo = {
  pageNumber: 0,
  maxPageNumber: 0,
};

const PageBasedPagination = ({
  pageInfo = defaultPageBasedPageInfo,
  onRequestPreviousPage,
  onRequestNextPage,
  pageSize,
  ...props
}: PageBasedPaginationProps) => {
  const hasNext = pageInfo.pageNumber < pageInfo.maxPageNumber;
  const hasPrevious = pageInfo.pageNumber > 0;

  const handleRequestNextPage = () => {
    hasNext && onRequestNextPage(pageInfo.pageNumber + 1, pageSize);
  };

  const handleRequestPreviousPage = () => {
    hasPrevious && onRequestPreviousPage(pageInfo.pageNumber - 1, pageSize);
  };

  return (
    <Pager
      hasNext={hasNext}
      hasPrevious={hasPrevious}
      onRequestPreviousPage={handleRequestPreviousPage}
      onRequestNextPage={handleRequestNextPage}
      pageSize={pageSize}
      {...props}
    />
  );
};

export default Pager;
export { CursorBasedPagination, PageBasedPagination };
