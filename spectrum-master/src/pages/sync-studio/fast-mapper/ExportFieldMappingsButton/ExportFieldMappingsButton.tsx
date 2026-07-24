import { GridApi } from 'ag-grid-community';
import { Button } from 'antd';
import { useEffect, useMemo, useState } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { useDownloadCSVHandler } from 'hooks/useDownloadCSVHandler';
import { tc } from 'utils/i18nUtil';

import { useFastMapper } from '../FastMapperModal';
import { generateMappingCSVData, useExportedFields } from './ExportFieldMappingsButton.utils';

export interface ExportFieldMappingsButtonProps {
  className?: string;
  disabled: boolean;
  gridApi: GridApi | undefined;
  gridUpdatedTrigger: boolean;
  mappings: Record<string, any>[];
  searchValue: string;
}

export const ExportFieldMappingsButton = ({
  className,
  disabled,
  gridApi,
  mappings,
  searchValue,
  gridUpdatedTrigger,
}: ExportFieldMappingsButtonProps) => {
  const { entities } = useEnhancedSelector((state) => state.entity);
  const { connectors } = useEnhancedSelector((state) => state.connector);

  const { entityId } = useFastMapper();
  const { handleDownloadCSV } = useDownloadCSVHandler();
  const { exportedFields, humanizeSyncDirectionId } = useExportedFields();

  const [displayedRowIds, setDisplayedRowIds] = useState<string[]>();
  const [selectedRowIds, setSelectedRowIds] = useState<string[]>();

  const entityName = useMemo(() => {
    if (!entities) {
      return '';
    }

    return entities.find((entity) => entity.id === entityId)?.displayName ?? '';
  }, [entities, entityId]);

  // Add the Synapse Type to the list of mappings
  const enhancedMappings: Record<string, string>[] = useMemo(() => {
    return mappings.map((mapping) => {
      const synapseType = connectors.find((connector) => connector.id === mapping.synapseId)?.displayName ?? '';
      let syncDirectionId = humanizeSyncDirectionId(mapping.syncDirectionId);

      return {
        ...mapping,
        synapseType,
        syncDirectionId,
      };
    });
  }, [connectors, humanizeSyncDirectionId, mappings]);

  // Apply sortings and filters for export
  const displayedMappings = useMemo(() => {
    let displayedMappings = enhancedMappings;

    if (displayedRowIds) {
      displayedMappings.sort((a, b) => displayedRowIds.indexOf(a.id) - displayedRowIds.indexOf(b.id));
    }

    // Apply a search filter if one is present
    if (displayedRowIds && displayedRowIds.length !== mappings.length) {
      displayedMappings = displayedMappings.filter((mapping) => displayedRowIds.includes(mapping.id));
    }

    // If one or more rows are selected, then only use the selected rows for export
    if (selectedRowIds && selectedRowIds.length > 0) {
      displayedMappings = displayedMappings.filter((mapping) => selectedRowIds.includes(mapping.id));
    }

    return displayedMappings;
  }, [displayedRowIds, enhancedMappings, mappings.length, selectedRowIds]);

  // When the filter or search order for the table changes, recalculate the
  // what rows are displayed and in what order
  useEffect(() => {
    if (gridApi) {
      const rowCount = gridApi.getDisplayedRowCount();
      const filteredRows: string[] = [];

      for (let i = 0; i < rowCount; i++) {
        const row = gridApi.getDisplayedRowAtIndex(i);
        if (row) {
          filteredRows.push(row.data.id);
        }
      }

      setDisplayedRowIds(filteredRows);
    }
  }, [gridApi, searchValue, gridUpdatedTrigger]);

  // When a new row is selected, update the selectedRowIds array
  useEffect(() => {
    if (gridApi) {
      const selectedRowIds = gridApi.getSelectedRows().map((row) => row.id);
      setSelectedRowIds(selectedRowIds);
    }
  }, [gridApi, gridUpdatedTrigger]);

  return (
    <Button
      className={className}
      disabled={displayedMappings.length === 0 || disabled}
      onClick={() => {
        const csvName = `${entityName}_mappings.csv`;
        const csvData = generateMappingCSVData(displayedMappings, exportedFields);
        handleDownloadCSV(csvData, csvName);
      }}>
      {tc('export_as_csv')}
    </Button>
  );
};
