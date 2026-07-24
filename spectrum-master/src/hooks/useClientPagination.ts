import { useMemo, useState } from 'react';

import { DefaultPageSizeOptions } from 'components/AgTable';
import { PageBasedPageInfo } from 'components/AgTable/Pagination';

type ClientPaginationResult<T> = [
  {
    pageInfo: PageBasedPageInfo;
    records: T[];
  },
  {
    getNextPage: (pageNumber: number) => void;
    getPreviousPage: (pageNumber: number) => void;
    goToPage: (pageNumber: number) => void;
  }
];

function useClientPagination<T extends {}>(
  records: T[],
  pageSize: number = DefaultPageSizeOptions[0]
): ClientPaginationResult<T> {
  const [pageNumber, setPageNumber] = useState(0);

  const getNextPage = useMemo(() => (pageNumber: number) => setPageNumber(pageNumber), [setPageNumber]);
  const getPreviousPage = useMemo(() => (pageNumber: number) => setPageNumber(pageNumber), [setPageNumber]);
  const goToPage = useMemo(() => (pageNumber: number) => setPageNumber(pageNumber), [setPageNumber]);

  const maxPageNumber = Math.ceil(records.length / pageSize) - 1 || 0;

  const currentPage = useMemo(() => {
    const startIdx = pageSize * pageNumber;
    return records.slice(startIdx, startIdx + pageSize);
  }, [records, pageNumber, pageSize]);

  return [
    {
      pageInfo: {
        pageNumber,
        maxPageNumber,
      },
      records: currentPage,
    },
    {
      getNextPage,
      getPreviousPage,
      goToPage,
    },
  ];
}

export default useClientPagination;
