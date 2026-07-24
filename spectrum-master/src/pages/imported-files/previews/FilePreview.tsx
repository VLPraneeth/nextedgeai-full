import { message } from 'antd';
import { useMemo, useState } from 'react';

import { ReactComponent as ExportIcon } from 'assets/icons/export.svg';
import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Button from 'components/Button';
import Can from 'components/Can';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { HStack, Spacer, Stack } from 'components/layout';
import StatusBadge, { StatusBadgeSize, StatusBadgeType } from 'components/StatusBadge';
import Tooltip from 'components/tooltip/Tooltip';
import { Text } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { useGetImportedFilePreviewQuery } from 'store/imported-files/api';
import { openEditFileDrawer } from 'store/imported-files/slice';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadCsvDataAsFile } from 'utils/DownloadUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useDeleteFile } from '../hooks';

import './PreviewContainer.less';

type FilePreviewProps = {
  fileId: string;
  fileName: string;
  folderId: string;
};

enum KebabKeys {
  'edit_file' = 'edit_file',
  'delete_file' = 'delete_file',
}

const FilePreview = ({ fileId, fileName, folderId }: FilePreviewProps) => {
  const tn = tNamespaced('ImportedFiles');
  const { data, isFetching } = useGetImportedFilePreviewQuery({ fileId });
  const deleteFile = useDeleteFile();
  const dispatch = useEnhancedDispatch();
  const [isFileExporting, setIsFileExporting] = useState(false);

  const headers = useMemo(
    () =>
      data?.headerColumns.map((item) => {
        const cleaned = item.replaceAll('"', '');
        return {
          headerName: cleaned,
          field: cleaned,
        };
      }),
    [data?.headerColumns]
  );

  const rows = data?.rows.map((row, index) => {
    const headerFields = headers?.map((header) => header.field);

    let newRow = { id: `row-${index}` };
    row.forEach((item, index) => {
      const field = headerFields![index];
      const cleanedValue = item.replaceAll('"', '');
      Object.assign(newRow, { [field]: cleanedValue });
    });

    return newRow;
  });

  const handleKebabClick = (key: KebabKeys) => {
    if (key === KebabKeys.edit_file) {
      dispatch(openEditFileDrawer({ folderId: fileId }));
    }

    if (key === KebabKeys.delete_file) {
      deleteFile({ fileId, fileName, folderId });
    }
  };

  return (
    <div className="imported-files-preview-container">
      <Stack className="imported-files__header-bar">
        <HStack spacing="xs">
          <Text color="gray-900" weight="semibold">
            {fileName}
          </Text>
          <Tooltip title={tn('preview')}>
            {/* this div wrapper is needed for tooltip triggering to work properly */}
            <div>
              <StatusBadge size={StatusBadgeSize.SMALL} type={StatusBadgeType.SUCCESS}>
                {tn('preview')}
              </StatusBadge>
            </div>
          </Tooltip>
        </HStack>
        <Spacer />
        <HStack spacing="xxxs">
          <Button
            loading={isFileExporting}
            onClick={() => {
              setIsFileExporting(true);
              downloadCsvDataAsFile(fileName, makeUrl(DataUrlConstants.GET_EXPORT_FILE, { fileId }))
                .catch(() => message.error(tn('download_failed')))
                .finally(() => setIsFileExporting(false));
            }}
            className="imported-files__export-button"
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
            size="small">
            <ExportIcon className="imported-files__export-button-icon" height="20px" width="20px" />
            <Text size="sm">{tn('export')}</Text>
          </Button>
          <KebabMenu
            onClick={({ key }: { key: KebabKeys }) => handleKebabClick(key)}
            className="sidebar__folder-header--kebab"
            menuItems={[
              <Can key={KebabKeys.edit_file} permission={AllPermissions.WRITE_FILE_DATA}>
                <MenuItem>
                  <Text>{tn('edit_file')}</Text>
                </MenuItem>
              </Can>,
              <Can key={KebabKeys.delete_file} permission={AllPermissions.DELETE_FILE_DATA}>
                <MenuItem>
                  <Text>{tn('delete_file')}</Text>
                </MenuItem>
              </Can>,
            ]}
          />
        </HStack>
      </Stack>
      <AgTable
        sizeColumnsToFit={ResizeColumnsCondition.WHEN_NARROWER}
        loading={isFetching}
        enableCellTextSelection={Boolean(rows)}
        columnDefs={headers}
        rowData={rows}
      />
    </div>
  );
};

export default FilePreview;
