import { useMatch } from '@reach/router';
import { Spin } from 'antd';
import { sortBy } from 'lodash';
import { useEffect, useMemo, useState } from 'react';

import { withI18n } from 'components/I18nProvider';
import CenterLayout from 'components/layout/CenterLayout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useForbiddenRedirect } from 'hooks/useForbiddenRedirect';
import useNavigateTo from 'hooks/useNavigateTo';
import { useGetImportedFoldersListQuery } from 'store/imported-files/api';
import { openUploadDrawer as openUploadDrawerAction } from 'store/imported-files/slice';
import { AlertDataType, AlertVariants, DrawerVariants } from 'store/imported-files/types';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import EditFileDrawer from './EditFileDrawer';
import EditFolderDrawer from './EditFolderDrawer';
import EmptyState from './EmptyState';
import PreviewContainer from './previews/PreviewContainer';
import Sidebar from './Sidebar';
import UploadSideDrawer from './UploadSideDrawer';

import './ImportedFiles.less';

const ImportedFiles = () => {
  const dispatch = useEnhancedDispatch();

  const Error403 = useForbiddenRedirect({
    studioPermissions: AllPermissions.READ_FILE_DATA,
  });

  const [alertData, setAlertData] = useState<AlertDataType>({
    alertEnabled: false,
    message: '',
    type: AlertVariants.INFO,
  });

  const folderIdMatch = useMatch('/imported-files/folder/:folderId/*');
  const folderId = folderIdMatch?.folderId;

  const fileIdMatch = useMatch('/imported-files/folder/:folderId/file/:fileId');
  const fileId = fileIdMatch?.fileId;

  const { data: folders, isLoading, isFetching } = useGetImportedFoldersListQuery();

  const currentFolder = useMemo(() => folders?.find((folder) => folder.id === folderId), [folderId, folders]);

  const navigate = useNavigateTo();

  const drawerVariant = useEnhancedSelector((state) => state.importedFiles.drawerVariant);

  useEffect(() => {
    if (!folderId && folders?.length && !isFetching) {
      // Redirect to the first folder when one is not selected
      navigate(makeUrl(RouteConstants.IMPORTED_FILES_FOLDER, { folderId: sortBy(folders, 'name')[0].id }));
    }
  }, [folderId, folders, isFetching, isLoading, navigate]);

  if (isLoading) {
    return (
      <CenterLayout>
        <Spin />
      </CenterLayout>
    );
  }

  return (
    Error403 ?? (
      <div className="imported-files">
        <Sidebar selectedFolderId={folderId} folders={folders} />
        {drawerVariant === DrawerVariants.upload ? (
          <UploadSideDrawer folders={folders} setAlertData={setAlertData} />
        ) : drawerVariant === DrawerVariants.editFolder ? (
          <EditFolderDrawer currentFolder={currentFolder} />
        ) : drawerVariant === DrawerVariants.editFile ? (
          <EditFileDrawer fileId={fileId!} currentFolder={currentFolder} />
        ) : null}

        <div className="imported-files__content-container">
          {Boolean(currentFolder?.files?.length) ? (
            <PreviewContainer folder={currentFolder!} alertData={alertData} />
          ) : (
            <EmptyState
              hasFolders={Boolean(folders?.length)}
              currentFolder={currentFolder?.name || ''}
              onClick={() => {
                dispatch(openUploadDrawerAction({ folderId: currentFolder?.id! }));
              }}
            />
          )}
        </div>
      </div>
    )
  );
};

export default withI18n(ImportedFiles, 'ImportedFiles');
