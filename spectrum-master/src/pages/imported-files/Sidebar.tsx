import { Link, useMatch } from '@reach/router';
import cx from 'classnames';
import { orderBy, sortBy } from 'lodash';
import { useMemo } from 'react';

import { ReactComponent as FileIcon } from 'assets/icons/file.svg';
import { ReactComponent as UploadIcon } from 'assets/icons/upload.svg';
import Button from 'components/Button';
import Can from 'components/Can';
import KebabMenu, { MenuItem } from 'components/KebabMenu';
import Tooltip from 'components/tooltip/Tooltip';
import TreeSkeleton from 'components/tree-skeleton';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { openUploadDrawer, openEditFolderDrawer, openEditFileDrawer } from 'store/imported-files/slice';
import { ImportedFile, UploadFolder } from 'store/imported-files/types';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadCsvDataAsFile } from 'utils/DownloadUtil';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { useDeleteFile, useDeleteFolder } from './hooks';

import './Sidebar.less';
interface SidebarProps {
  folders?: UploadFolder[];
  selectedFolderId?: string;
}

const Sidebar = ({ folders }: SidebarProps) => {
  const tn = tNamespaced('ImportedFiles');
  const match = useMatch('folder/:folderId/file/:fileId');

  const dispatch = useEnhancedDispatch();

  const folderIdMatch = useMatch('folder/:folderId');

  const selectedFolderId = folderIdMatch?.folderId;

  const deleteFile = useDeleteFile();
  const deleteFolder = useDeleteFolder();

  const FolderHeader = useMemo(
    () => ({ folder }: { folder: UploadFolder }) => (
      <Link
        to={makeUrl(RouteConstants.IMPORTED_FILES_FOLDER, { folderId: folder.id })}
        className={cx('sidebar__folder-header', selectedFolderId === folder.id && 'sidebar__folder-header--selected')}>
        <Tooltip title={folder.name}>
          <Text className="sidebar__folder-header-text" color="gray-800" weight="semibold">
            {folder.name}
          </Text>
        </Tooltip>
        <KebabMenu
          className="sidebar__folder-header--kebab"
          menuItems={[
            <Can key="upload_file" permission={AllPermissions.WRITE_FILE_DATA}>
              <MenuItem
                onClick={() => {
                  dispatch(openUploadDrawer({ folderId: folder.id }));
                }}>
                <TranslatedText text="upload_file" />
              </MenuItem>
            </Can>,
            <Can key="edit_folder" permission={AllPermissions.WRITE_FILE_DATA}>
              <MenuItem onClick={() => dispatch(openEditFolderDrawer({ folderId: folder.id }))}>
                <TranslatedText text="edit_folder" />
              </MenuItem>
            </Can>,
            <Can key="delete_folder" permission={AllPermissions.DELETE_FILE_DATA}>
              <MenuItem onClick={() => deleteFolder({ folderId: folder.id, folderName: folder.name })}>
                <TranslatedText text="delete_folder" />
              </MenuItem>
            </Can>,
          ]}
        />
      </Link>
    ),
    [deleteFolder, dispatch, selectedFolderId]
  );

  const FileHeader = useMemo(
    () => ({ file, folder }: { file: ImportedFile; folder: UploadFolder }) => (
      <div key={file.id}>
        <Link
          to={makeUrl(RouteConstants.IMPORTED_FILES_FILE, { folderId: folder.id, fileId: file.id })}
          className={cx('sidebar__file-header', match?.fileId === file.id && 'sidebar__file-header--selected')}>
          <Tooltip className="sidebar__file-header-text" title={file.name}>
            <FileIcon className="sidebar__file-icon" role="img" aria-label="File icon" />
            <Text className="sidebar__file-header-text" color="gray-800" weight="semibold">
              {file.name}
            </Text>
          </Tooltip>
          <div className="sidebar__folder-header--kebab" />
          <KebabMenu
            className="sidebar__folder-header--kebab"
            menuItems={[
              <Can key="edit_file" permission={AllPermissions.WRITE_FILE_DATA}>
                <MenuItem onClick={() => dispatch(openEditFileDrawer({ folderId: folder.id }))}>
                  <Text>{tn('edit_file')}</Text>
                </MenuItem>
              </Can>,
              <MenuItem
                key="export_file"
                onClick={() =>
                  downloadCsvDataAsFile(file.name, makeUrl(DataUrlConstants.GET_EXPORT_FILE, { fileId: file.id }))
                }>
                <Text>{tn('export_file')}</Text>
              </MenuItem>,
              <Can key="delete_file" permission={AllPermissions.DELETE_FILE_DATA}>
                <MenuItem onClick={() => deleteFile({ fileId: file.id, fileName: file.name, folderId: folder.id })}>
                  <Text>{tn('delete_file')}</Text>
                </MenuItem>
              </Can>,
            ]}
          />
        </Link>
      </div>
    ),
    [deleteFile, dispatch, match?.fileId, tn]
  );

  const items = sortBy(folders, 'name')?.map((folder) => {
    return {
      key: folder.id,
      label: <FolderHeader folder={folder} />,
      children:
        folder.files.length > 0 ? (
          orderBy(folder.files, 'uploadedAt', 'desc').map((file) => {
            return <FileHeader folder={folder} file={file} />;
          })
        ) : (
          <div className="sidebar__file-header-text--no-data">
            <TranslatedText text="no_files_found" color="gray-700" />
          </div>
        ),
    };
  });

  return (
    <div className="sidebar">
      <Can permission={AllPermissions.WRITE_FILE_DATA}>
        <Button
          onClick={() => dispatch(openUploadDrawer({ folderId: selectedFolderId! }))}
          className="sidebar__upload-button"
          size="large">
          <UploadIcon className="file-icon" role="img" aria-label="Upload icon" />
          {tn('upload_file')}
        </Button>
      </Can>

      <div className="sidebar__folder-list">
        <TreeSkeleton
          expandIconsOffset={15}
          borderOptions={{
            labelBottom: false,
            contentBottom: false,
          }}
          items={items || EMPTY_ARRAY}
        />
      </div>
    </div>
  );
};

export default Sidebar;
