import { Button, message } from 'antd';
import { useState } from 'react';
import { animated, useSpring } from 'react-spring';

import EmailInput from 'components/EmailInput';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useUpdateErrorNotificationsConfigMutation } from 'store/error-notifications-v2/api';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { EMAIL_REGEX } from 'utils/RegexUtil';

import { useErrorNotificationContext } from '../context/ErrorNotificationFormContext';

import './AddEmailBox.scss';

const tn = tNamespaced('Settings.ErrorNotifications');

interface AddEmailBoxProps {
  setShowAddEmailBox: (show: boolean) => void;
  showAddEmailBox: boolean;
}

export function AddEmailBox({ setShowAddEmailBox, showAddEmailBox }: AddEmailBoxProps) {
  const [recipients, setRecipients] = useState<string[]>([]);

  const { currentNotificationConfig } = useErrorNotificationContext();

  const [updateErrorNotificationsConfig, { isLoading: isUpdating }] = useUpdateErrorNotificationsConfigMutation();

  function clearState() {
    setShowAddEmailBox(false);
    setRecipients([]);
  }

  function onEmailsChange(values: string[]) {
    const emails = values.map((email) => email.trim());

    const areEmailsValid = emails.every((email) => EMAIL_REGEX.test(email));

    const existingEmails = currentNotificationConfig?.configuration?.emails?.map((e) => e.email);

    const hasNoDuplicates = emails.some((email) => existingEmails?.includes(email));

    if (!areEmailsValid) {
      message.error(tn('email_invalid'));
      return;
    }

    if (hasNoDuplicates) {
      message.error(tn('duplicate_email'));
      return;
    }

    setRecipients(emails);
  }

  const handleAddEmails = () => {
    updateErrorNotificationsConfig({
      ...currentNotificationConfig,
      configuration: {
        ...(currentNotificationConfig?.configuration || {}),
        emails: [
          ...(currentNotificationConfig?.configuration?.emails || []),
          ...recipients.map((email) => ({ email })),
        ],
      },
    })
      .unwrap()
      .then(() => {
        message.success(tn('subscribers_added_success', { count: recipients.length }));
        clearState();
      })
      .catch((error) => message.error(getRtkQueryErrorMessage(error, tn('subscribers_added_error'))));
  };

  const animation = useSpring({
    from: { opacity: 0, maxHeight: '0px' },
    to: {
      opacity: showAddEmailBox ? 1 : 0,
      maxHeight: showAddEmailBox ? '200px' : '0px',
    },
    config: { tension: 300 },
  });

  return (
    <animated.div style={animation}>
      <div className="add-email-box">
        <InputWithLabel
          label={tn('add_recipients')}
          input={
            <div data-testid="emailInput">
              <EmailInput placeholder={tn('email_placeholder')} value={recipients} onChange={onEmailsChange} />
            </div>
          }
        />

        <div className="add-email-box__buttons">
          <Button onClick={clearState}>{tc('cancel')}</Button>
          <Button type="primary" onClick={handleAddEmails} loading={isUpdating} disabled={!recipients.length}>
            {tc('add')}
          </Button>
        </div>
      </div>
    </animated.div>
  );
}
