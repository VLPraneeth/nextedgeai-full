import DataUrlConstants from 'utils/DataUrlConstants';
import { t } from 'utils/i18nUtil';
import { makeUrl } from 'utils/UrlUtil';

export type FileLinkProps = {
  entityId: string;
  recordId: string;
  title?: string;
};

export const FileLinkRenderer = ({ entityId, recordId, title }: FileLinkProps) => {
  return (
    <a
      download
      href={makeUrl(DataUrlConstants.GET_RECORD_DOCUMENT, { entityId, recordId })}
      rel="noreferrer"
      target="_blank">
      {title || t('DataStudio.download_file_label')}
    </a>
  );
};

export default FileLinkRenderer;
