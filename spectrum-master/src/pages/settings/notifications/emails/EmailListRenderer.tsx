import { Tag, Tooltip } from 'antd';

import { ReactComponent as WarningIcon } from 'assets/icons/warning.svg';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';
import { tNamespaced } from 'utils/i18nUtil';

interface EmailsRendererProps {
  data: ErrorNotificationConfig | undefined;
  handleClick: () => void;
}

const tn = tNamespaced('Settings.ErrorNotifications');

export function EmailsRenderer({ data, handleClick }: EmailsRendererProps) {
  if (!data) {
    return null;
  }
  const emails = data?.configuration?.emails || [];
  const pendingEmails = emails.filter((email) => email.status === 'Pending').length;
  return (
    <div className="error-notifications__email-list">
      <div>
        {emails.slice(0, 2).map(({ email }) => {
          return (
            <Tag key={email} className="error-notifications__tags">
              {email}
            </Tag>
          );
        })}
        {emails.length > 2 && (
          <Tooltip
            title={emails
              .slice(2, emails.length)
              .map((e) => e.email)
              .join(', ')}>
            <Tag className="error-notifications__tags">+{emails.length - 2}</Tag>
          </Tooltip>
        )}
      </div>
      <div onClick={handleClick} className="error-notifications__manage_subscribers">
        {!!pendingEmails && (
          <Tooltip
            title={tn('email_require_action', {
              count: pendingEmails,
            })}>
            <WarningIcon />
          </Tooltip>
        )}
        {tn('manage_subscribers')}
      </div>
    </div>
  );
}
