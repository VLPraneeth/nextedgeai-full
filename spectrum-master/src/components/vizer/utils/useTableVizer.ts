//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { EMPTY_ARRAY } from 'store/constants';

import { VizerProps } from '../types';

const useTableVizer = ({ configuration, data }: VizerProps) => {
  return {
    columns: configuration.columns?.map((col) => ({
      headerName: col.displayName,
      headerTooltip: col.displayName,
      cellRenderer: col.displayFormat,
      field: col.name,
      sortable: true,
      resizable: true,
      // Setting flex ensures that columns will always fill the available space when
      // columns are added/removed. Default behavior of ag-grid is *not* to resize
      // when column defs change: https://www.ag-grid.com/react-data-grid/column-updating-definitions/
      flex: 1,
    })),
    rowData: data?.rows || EMPTY_ARRAY,
  };
};

export { useTableVizer };
