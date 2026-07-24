import { Alert, Button, message } from 'antd';

import Fieldset from 'components/Fieldset';
import { usePostErrorNotificationsTestMutation } from 'store/error-notifications-v2/api';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('Settings.ErrorNotifications');

export function TestEmail() {
  const [sendTest, { isLoading }] = usePostErrorNotificationsTestMutation();

  return (
    <Fieldset defaultCollapsed collapsible className="error-notifications__form--collapse" title={tn('test_email')}>
      <Alert
        className="error-notifications__form__email-alert"
        message={tn('test_email_helper')}
        type="info"
        showIcon
      />
      <Button
        loading={isLoading}
        className="error-notifications__form__test-email-button"
        type="primary"
        onClick={() => {
          sendTest({ type: 'email' })
            .unwrap()
            .then(() => message.success(tn('test_email_success')))
            .catch(() => message.error(tn('test_email_error')));
        }}>
        {tc('send_test')}
      </Button>
    </Fieldset>
  );
}
