import { RouteComponentProps } from '@reach/router';
import { ColDef, ColGroupDef } from 'ag-grid-community';
import { keyBy } from 'lodash';
import { useEffect } from 'react';

import AgTable, { ResizeColumnsCondition } from 'components/AgTable';
import Can from 'components/Can';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import { Text } from 'components/typography';
import useUserLocalMoment from 'hooks/moment';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { openUploadDrawer, openEditFolderDrawer } from 'store/imported-files/slice';
import { AlertDataType, UploadFolder } from 'store/imported-files/types';
import { getUsers } from 'store/user/thunks';
import { SHORT_DATE_24_TIME_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { useDeleteFolder } from '../hooks';
import ActionButtonRenderer from './ActionButtonRenderer';

import './PreviewContainer.less';
import { Alert } from 'antd';

export type FolderPreviewProps = RouteComponentProps & {
  folder: UploadFolder;
  alertData: AlertDataType;
};

enum KebabKeys {
  'upload_file' = 'upload_file',
  'edit_folder' = 'edit_folder',
  'delete_folder' = 'delete_folder',
}

const FolderPreview = ({ folder, alertData }: FolderPreviewProps) => {
  const dispatch = useEnhancedDispatch();
  const deleteFolder = useDeleteFolder();

  const tn = tNamespaced('ImportedFiles');

  const components = {
    actions: ActionButtonRenderer,
  };

  const columns: (ColDef | ColGroupDef)[] = [
    {
      headerName: tn('file_name'),
      field: 'fileName',
    },
    {
      headerName: tn('file_type'),
      field: 'fileType',
    },
    {
      headerName: tn('uploaded_at'),
      field: 'uploadedAt',
      sort: 'desc',
    },
    {
      headerName: tn('uploaded_by'),
      field: 'uploadedBy',
    },
    {
      headerName: tn('row_count'),
      field: 'rowsCount',
      flex: 1,
      minWidth: 200,
    },
    {
      headerName: 'Actions',
      field: 'id',
      cellRenderer: 'actions',
      pinned: 'right',
      maxWidth: 140,
    },
  ];

  useEffect(() => {
    dispatch(getUsers());
  }, [dispatch]);

  const moment = useUserLocalMoment();

  const users = useEnhancedSelector((state) => state.user.users);
  const userMap = keyBy(users, 'id');

  const rowData = folder.files.map((file) => {
    const user = userMap[file.uploadedBy];
    const uploadedBy = user ? `${user.firstName} ${user.lastName}` : file.uploadedBy;

    return {
      id: file.id,
      folderId: folder.id,
      fileName: file.name,
      fileType: file.fileType,
      uploadedAt: moment(file.uploadedAt).format(SHORT_DATE_24_TIME_FORMAT),
      uploadedBy,
      rowsCount: file.rowsCount,
    };
  });

  const handleKebabClick = (key: KebabKeys) => {
    if (key === KebabKeys.upload_file) {
      dispatch(openUploadDrawer({ folderId: folder.id }));
    }

    if (key === KebabKeys.edit_folder) {
      dispatch(openEditFolderDrawer({ folderId: folder.id }));
    }
    if (key === KebabKeys.delete_folder) {
      deleteFolder({ folderId: folder.id, folderName: folder.name });
    }
  };

  const { alertEnabled, message, type } = alertData;

  return (
    <div className="imported-files-preview-container">
      {alertEnabled && <Alert banner type={type} message={message} />}
      <div className="imported-files__header-bar">
        <Text color="gray-900" weight="semibold" size="lg">
          {tn('files_count', { count: folder.files.length })}
        </Text>
        <KebabMenu
          className="sidebar__folder-header--kebab"
          onClick={({ key }: { key: KebabKeys }) => handleKebabClick(key)}
          menuItems={[
            <Can key={KebabKeys.upload_file} permission={AllPermissions.WRITE_FILE_DATA}>
              <MenuItem>
                <Text>{tn('upload_file')}</Text>
              </MenuItem>
            </Can>,
            <Can key={KebabKeys.edit_folder} permission={AllPermissions.WRITE_FILE_DATA}>
              <MenuItem>
                <Text>{tn('edit_folder')}</Text>
              </MenuItem>
            </Can>,
            <Can key={KebabKeys.delete_folder} permission={AllPermissions.DELETE_FILE_DATA}>
              <MenuItem>
                <Text>{tn('delete_folder')}</Text>
              </MenuItem>
            </Can>,
          ]}
        />
      </div>

      <AgTable
        sizeColumnsToFit={ResizeColumnsCondition.ALWAYS}
        enableCellTextSelection
        columnDefs={columns}
        rowData={rowData}
        frameworkComponents={components}
      />
    </div>
  );
};

export default FolderPreview;
