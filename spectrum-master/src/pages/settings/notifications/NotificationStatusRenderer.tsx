import { Tag } from 'antd';

import { EmailStatus, NotificationStatus } from 'store/error-notifications-v2/types';

const statusColorMap: Record<NotificationStatus | EmailStatus, string> = {
  Active: 'green',
  Inactive: 'gray',
  OptOut: 'gray',
  Pending: 'gold',
  Disabled: 'red',
};

interface NotificationStatusRendererProps {
  status: NotificationStatus | EmailStatus | undefined;
  handleClick: () => void;
}

export function NotificationStatusRenderer({ status, handleClick }: NotificationStatusRendererProps) {
  if (!status) {
    return null;
  }
  return (
    <Tag color={statusColorMap[status]} className="error-notifications__tags" onClick={handleClick}>
      {status}
    </Tag>
  );
}
