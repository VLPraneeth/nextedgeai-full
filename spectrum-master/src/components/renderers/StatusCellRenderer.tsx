import StatusBadge, { StatusBadgeType } from 'components/StatusBadge';
import { tNamespaced } from 'utils/i18nUtil';

import { Status } from './types';

const tn = tNamespaced('TableCellRenderers.StatusCell');

const statusTypeMap = {
  [Status.ACTIVE]: StatusBadgeType.SUCCESS,
  [Status.INACTIVE]: StatusBadgeType.DEFAULT,
  [Status.PENDING]: StatusBadgeType.WARNING,
  [Status.DELETED]: StatusBadgeType.DANGER,
  [Status.APPROVED]: StatusBadgeType.DEFAULT,
};

interface StatusCellProps {
  status: Status;
}

const StatusCell = ({ status }: StatusCellProps) => {
  return <StatusBadge type={statusTypeMap[status]}>{tn(status || 'INACTIVE')}</StatusBadge>;
};

const StatusCellRenderer = (status: Status) => <StatusCell status={status} />;

export default StatusCellRenderer;
