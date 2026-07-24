import { Button, DatePicker, Input, Modal, message } from 'antd';
import moment, { Moment } from 'moment';
import { useState } from 'react';

import EmailInput from 'components/EmailInput';
import InlineMessage from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import {
  useCreateShareDashboardInviteMutation,
  useGetAllowedDomainsQuery,
  useGetDashboardQuery,
} from 'store/insights-studio';
import { FULL_DATE_TIME, SHORT_DATE_TIME_FORMAT, disablePastDate, disablePastTime } from 'utils/DateUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { EMAIL_REGEX } from 'utils/RegexUtil';

import { useUnifiedDataCardNavigate } from '../utils/useUnifiedDataCardNavigate';
import './ShareWithModal.scss';

const tn = tNamespaced('InsightsStudio');

export function ShareWithModal() {
  const { dashboardShareWithMatch, navigateToCurrentDashboard } = useUnifiedDataCardNavigate();
  const [recipients, setRecipients] = useState<string[]>([]);
  const [emailMessage, setEmailMessage] = useState('');
  const [expiry, setExpiry] = useState<Moment>();
  const [createInvite, { isLoading }] = useCreateShareDashboardInviteMutation();
  const { data: allowedDomains } = useGetAllowedDomainsQuery();
  const { data: dashboard } = useGetDashboardQuery(dashboardShareWithMatch?.dashboardId!);
  const [emailError, setEmailError] = useState<string>();

  function onEmailsChange(values: string[]) {
    const emails = values.map((email) => email.trim());
    const email = emails[emails.length - 1];

    const areEmailsValid = emails.every((email) => EMAIL_REGEX.test(email));

    if (!areEmailsValid) {
      setEmailError(tc('email_invalid_with_email', { email }));
      return;
    }

    const areEmailsWithAllowedDomains = emails.every((email) => {
      const domain = email.split('@')[1];
      return allowedDomains?.domains?.includes(domain);
    });

    if (allowedDomains?.domains?.length && !areEmailsWithAllowedDomains) {
      setEmailError(tn('InsightsSharing.email_domain_invalid', { domain: email.split('@')[1] }));
    } else {
      setEmailError(undefined);
      setRecipients(emails);
    }
  }

  function handleClose() {
    setRecipients([]);
    setEmailMessage('');
    setExpiry(undefined);
    navigateToCurrentDashboard();
    setEmailError(undefined);
  }

  function sendInvite() {
    if (!dashboardShareWithMatch?.dashboardId) {
      return null;
    }
    setEmailError(undefined);
    createInvite({
      dashboardId: dashboardShareWithMatch?.dashboardId,
      emails: recipients,
      expiryDate: expiry && moment(expiry)?.utc().format(FULL_DATE_TIME),
      message: emailMessage,
    })
      .unwrap()
      .then((data) => {
        const count = data.length;
        const successCount = data.filter((item) => item.errorMessage === null).length;
        const errorCount = data.filter((item) => item.errorMessage !== null).length;

        if (successCount === count) {
          message.success(tn('invite_success', { count }));
          handleClose();
        } else if (errorCount === count) {
          message.error(tn('invite_failed', { count }));
        } else {
          message.warning(tn('invite_partial', { errorCount, successCount }));
          handleClose();
        }
      })
      .catch((error) => message.error(getRtkQueryErrorMessage(error)));
  }

  if (!dashboardShareWithMatch?.dashboardId) {
    return null;
  }

  return (
    <Modal
      title={tn('share', { dashboardName: dashboard?.displayName })}
      visible={!!dashboardShareWithMatch?.dashboardId}
      footer={
        <>
          <Button key="cancel" onClick={handleClose}>
            {tc('cancel')}
          </Button>
          <Button key="ok" type="primary" disabled={!recipients.length} onClick={sendInvite} loading={isLoading}>
            {tn('send_link')}
          </Button>
        </>
      }
      onCancel={handleClose}>
      <div className="share-with-modal">
        {emailError && (
          <InlineMessage type="error" title={emailError}>
            {emailError}
          </InlineMessage>
        )}
        <div>
          <InputWithLabel
            label={tn('add_recipients')}
            id="emails"
            required
            input={
              <div data-testid="emailInput">
                <EmailInput
                  placeholder={tn('InsightsSharing.email_placeholder')}
                  value={recipients}
                  onChange={onEmailsChange}
                />
              </div>
            }
          />
          <p className="share-with-modal__recipients-help-text">{tn('recipients_help_text')}</p>
        </div>
        <InputWithLabel
          label={tc('message_optional')}
          input={
            <Input.TextArea
              placeholder={tn('InsightsSharing.message_placeholder')}
              value={emailMessage}
              onChange={(e) => setEmailMessage(e.target.value)}
            />
          }
        />

        <InputWithLabel
          label={tc('expiration_date_optional')}
          tooltip={tn('Tooltips.dashboard_expiry')}
          className="share-with-modal__expiry-date"
          input={
            <DatePicker
              allowClear
              placeholder={tn('InsightsSharing.expiry_placeholder')}
              format={SHORT_DATE_TIME_FORMAT}
              disabledDate={disablePastDate}
              disabledTime={disablePastTime}
              onChange={(date: Moment | null) => {
                if (date) {
                  setExpiry(date);
                }
              }}
              showTime={{
                format: 'HH:mm',
              }}
              showToday={false}
            />
          }
        />
      </div>
    </Modal>
  );
}
