import { ColDef } from 'ag-grid-community';
import { useMemo } from 'react';

import AgTable from 'components/AgTable';
import { CursorPageInfo, PageBasedPageInfo } from 'components/AgTable/Pagination';
import { agFrameworkComponentsFromRendererMap, defaultRendererMap } from 'components/renderers';
import SyncariSystemFieldHeader from 'components/renderers/SyncariSystemFieldHeader';
import { FieldMetadata } from 'components/renderers/types';

const frameworkComponents = agFrameworkComponentsFromRendererMap(defaultRendererMap);

const getAgColumnsConfig = (
  metadata: Record<string, FieldMetadata>,
  columns: string[],
  frameworkComponents: typeof defaultRendererMap,
  defaultColDef: Partial<ColDef> = {}
): ColDef[] => {
  return columns
    .filter((column) => column in metadata)
    .map((column) => {
      const meta = metadata[column];

      return {
        ...defaultColDef,
        headerName: meta?.label || column,
        colId: column,
        field: column,
        headerComponentFramework: meta?.isSystem ? SyncariSystemFieldHeader : undefined,
        cellRenderer:
          meta?.label in frameworkComponents
            ? (meta?.label as string)
            : meta?.dataType in frameworkComponents
            ? (meta?.dataType as string)
            : undefined,
      };
    });
};

const Table = ({
  metadata,
  pageInfo,
  data,
}: {
  data: Record<string, any>[];
  metadata: { columns: string[]; fields: Record<string, any> };
  pageInfo: CursorPageInfo | PageBasedPageInfo | {};
}) => {
  const columnDefs = useMemo(() => {
    // If we only have a few columns, make sure they fill the entire width of the table
    return getAgColumnsConfig(
      metadata.fields,
      metadata.columns,
      frameworkComponents,
      metadata.columns.length <= 6 ? { flex: 1 } : {}
    );
  }, [metadata]);

  return (
    <div className="data-quality-studio-table">
      <AgTable columnDefs={columnDefs} frameworkComponents={frameworkComponents} rowData={data} />
    </div>
  );
};

export default Table;
