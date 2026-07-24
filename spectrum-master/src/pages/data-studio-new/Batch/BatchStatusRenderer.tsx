import { ICellRendererParams } from 'ag-grid-community';

import { withI18n, useI18nContext } from 'components/I18nProvider';
import {
  NODE_GRAPH_READY,
  NODE_GRAPH_SYNCING,
  NODE_GRAPH_PAUSED,
  NODE_GRAPH_WARNING,
  NODE_GRAPH_ERROR,
} from 'components/icons/Icons';
import InlineSvg from 'components/icons/InlineSvg';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { BatchStatus } from 'store/data-studio-batch/types';
import { UnreachableCaseError } from 'utils/TypeUtils';

import './BatchStatusRenderer.less';

const getIconForStatus = (status: BatchStatus) => {
  switch (status) {
    case BatchStatus.ACTIVE:
    case BatchStatus.PROCESSING:
      return NODE_GRAPH_SYNCING;

    case BatchStatus.COMPLETED:
      return NODE_GRAPH_READY;

    case BatchStatus.CANCELLED:
    case BatchStatus.DELETED:
      return NODE_GRAPH_WARNING;

    case BatchStatus.ERROR:
      return NODE_GRAPH_ERROR;

    case BatchStatus.NEW:
    case BatchStatus.PENDING:
      return NODE_GRAPH_PAUSED;

    default:
      throw new UnreachableCaseError(status);
  }
};

const BatchStatusRenderer = ({ value }: ICellRendererParams) => {
  const { tn } = useI18nContext();
  const icon = getIconForStatus(value);

  return (
    <HStack className="synri-batch-status-cell" spacing="xs">
      {icon && <InlineSvg className="synri-batch-status-icon" src={icon} title={tn(value)} />}
      <TranslatedText text={value} />
    </HStack>
  );
};

export default withI18n(BatchStatusRenderer, 'DataStudio.BatchStatus');
