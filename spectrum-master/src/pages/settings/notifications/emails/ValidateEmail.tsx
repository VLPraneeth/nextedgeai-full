import { navigate, RouteComponentProps } from '@reach/router';
import { message } from 'antd';
import { useEffect } from 'react';

import TabPanelSpin from 'components/TabPanelSpin';
import { useErrorNotificationInviteUserQuery } from 'store/error-notifications-v2/api';
import { ErrorNotificationInvitationQuery } from 'store/error-notifications-v2/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';

const tn = tNamespaced('Settings.ErrorNotifications');

export default function ErrorNotificationValidateEmail({
  encInstanceId,
  invitationId,
  status,
}: RouteComponentProps & ErrorNotificationInvitationQuery) {
  const { data, error } = useErrorNotificationInviteUserQuery({ encInstanceId, invitationId, status });

  useEffect(() => {
    const messageText = {
      success: {
        Active: tn('verify_email_success'),
        OptOut: tn('opt_out_email_success'),
      },
      error: {
        Active: tn('verify_email_error'),
        OptOut: tn('opt_out_email_error'),
      },
    };
    if (data) {
      message.success(messageText.success[status]);
      navigate(RouteConstants.HOME);
    } else if (error) {
      message.error(getRtkQueryErrorMessage(error, messageText.error[status]));
    }
  }, [data, error, status]);

  return <TabPanelSpin spinning />;
}
