import { ReactComponent as EmptyFolderIcon } from 'assets/icons/empty-folder.svg';
import { ReactComponent as EmptyPageIcon } from 'assets/icons/empty-page.svg';
import IconButton from 'components/Button';
import Can from 'components/Can';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import './EmptyState.less';

const tn = tNamespaced('ImportedFiles');

type EmptyFolderStateProps = {
  onClick: () => void;
  currentFolder: string;
};

type EmptyImportStateProps = Pick<EmptyFolderStateProps, 'onClick'>;

type EmptyStateProps = {
  onClick: () => void;
  hasFolders: boolean;
  currentFolder: string;
  path?: string;
};

const EmptyFolderState = ({ onClick, currentFolder }: EmptyFolderStateProps) => {
  return (
    <div className="empty-state">
      <EmptyPageIcon className="empty-state__icon" />
      <h3 className="empty-state__title">
        {tn('empty_folder_title', {
          folderName: currentFolder,
        })}
      </h3>
      <p className="empty-state__description" />
      <Can permission={AllPermissions.WRITE_FILE_DATA}>
        <IconButton className="empty-state__button" size="small" onClick={onClick}>
          {`+ ${tn('upload_file')}`}
        </IconButton>
      </Can>
    </div>
  );
};

const EmptyImportState = ({ onClick }: EmptyImportStateProps) => {
  return (
    <div className="empty-state">
      <EmptyFolderIcon className="empty-state__icon" />
      <h3 className="empty-state__title">{tn('empty_import_title')}</h3>
      <p className="empty-state__description">{tn('empty_import_description')}</p>
      <Can permission={AllPermissions.WRITE_FILE_DATA}>
        <IconButton className="empty-state__button" size="small" onClick={onClick}>
          {`+ ${tn('upload_file')}`}
        </IconButton>
      </Can>
    </div>
  );
};

const EmptyState = ({ hasFolders, onClick, currentFolder }: EmptyStateProps) =>
  hasFolders ? (
    <EmptyFolderState currentFolder={currentFolder} onClick={onClick} />
  ) : (
    <EmptyImportState onClick={onClick} />
  );

export default EmptyState;
