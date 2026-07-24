import { RouteComponentProps } from '@reach/router';
import { useEffect, useMemo } from 'react';

import { BreadcrumbLink } from 'components/breadcrumb/BreadcrumbLink';
import { BreadcrumbSeparator } from 'components/breadcrumb/BreadcrumbSeparator';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useBreadcrumb } from 'pages/breadcrumbs/useBreadcrumb';
import { EMPTY_ARRAY } from 'store/constants';
import { useGetImportedFoldersListQuery } from 'store/imported-files/api';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

export interface ImportedFileBreadcrumbProps extends RouteComponentProps {
  folderId?: string;
  fileId?: string;
}

export const ImportedFilesBreadcrumb = withI18n(({ folderId, fileId }: ImportedFileBreadcrumbProps) => {
  const { data: importedFolders = EMPTY_ARRAY } = useGetImportedFoldersListQuery();
  const { tn } = useI18nContext();
  const { setUrlName } = useBreadcrumb();

  useEffect(() => {
    setUrlName(RouteConstants.IMPORTED_FILES, tn('window_title'));
  }, [setUrlName, tn]);

  const [folderName, url] = useMemo(() => {
    const importedFolder = importedFolders?.find((folder) => folder.id === folderId);
    const name = importedFolder?.name ? importedFolder.name : folderId;
    const url = makeUrl(RouteConstants.IMPORTED_FILES_FOLDER, { folderId });
    if (importedFolder) {
      setUrlName(url, importedFolder.name);
    }
    return [name, url];
  }, [folderId, importedFolders, setUrlName]);

  const [fileName, fileUrl] = useMemo(() => {
    const folder = importedFolders.find((folder) => folder.files.find((file) => file.id === fileId));
    const file = folder?.files.find((file) => file.id === fileId);
    const url = makeUrl(RouteConstants.IMPORTED_FILES_FILE, { folderId, fileId });
    if (file) {
      setUrlName(url, file.name);
    }
    return [file?.name, url];
  }, [fileId, folderId, importedFolders, setUrlName]);

  return (
    <>
      <BreadcrumbLink to={RouteConstants.IMPORTED_FILES}>{tn('window_title')}</BreadcrumbLink>
      {folderName && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink to={url}>{folderName}</BreadcrumbLink>
        </>
      )}

      {fileId && (
        <>
          <BreadcrumbSeparator />
          <BreadcrumbLink to={fileUrl}>{fileName}</BreadcrumbLink>
        </>
      )}
    </>
  );
}, 'ImportedFiles');
