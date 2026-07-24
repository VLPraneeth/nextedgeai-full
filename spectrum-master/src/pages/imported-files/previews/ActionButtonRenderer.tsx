import { ICellRendererParams } from 'ag-grid-community';

import { ReactComponent as OpenArrowIcon } from 'assets/icons/open-arrow.svg';
import Button from 'components/Button';
import ActionsCell from 'components/renderers/ActionsCellRenderer';
import useNavigateTo from 'hooks/useNavigateTo';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { replaceToken } from 'utils/StringUtil';

import './ActionButtonRenderer.less';

const ActionButtonRenderer = ({ data: { folderId, id: fileId } }: ICellRendererParams) => {
  const navigate = useNavigateTo();

  const tn = tNamespaced('ImportedFiles');

  return (
    <div className="imported-files__action-button-container">
      <ActionsCell size="small">
        <Button
          type="default"
          size="small"
          className="imported-files__action-button"
          onClick={() =>
            navigate(
              replaceToken(RouteConstants.IMPORTED_FILES_FILE, {
                folderId,
                fileId,
              })
            )
          }>
          <OpenArrowIcon className="imported-files__action-button-arrow-icon" />
          {tn('preview')}
        </Button>
      </ActionsCell>
    </div>
  );
};

export default ActionButtonRenderer;
