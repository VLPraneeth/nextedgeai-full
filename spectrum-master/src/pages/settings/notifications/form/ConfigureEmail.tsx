import { message } from 'antd';

import EmailInput from 'components/EmailInput';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { LOOSE_EMAIL_REGEX } from 'utils/RegexUtil';

import { useErrorNotificationContext } from '../context/ErrorNotificationFormContext';

const tn = tNamespaced('Settings.ErrorNotifications');

export function ConfigureEmail() {
  const {
    errorNotificationFormState: { emails },
    setErrorNotificationFormState,
  } = useErrorNotificationContext();

  function onEmailsChange(value: string[]) {
    const emails = value.map((email) => email.trim());

    const areEmailsValid = emails.every((email) => LOOSE_EMAIL_REGEX.test(email));

    if (areEmailsValid) {
      setErrorNotificationFormState({ emails });
    } else {
      message.error(tn('email_invalid'));
    }
  }
  return (
    <div>
      <InputWithLabel
        label={tc('emails')}
        id="emails"
        required
        input={
          <div data-testid="emailInput">
            <EmailInput value={emails} onChange={onEmailsChange} />
          </div>
        }
      />
      <p>{tn('email_help_text')}</p>
    </div>
  );
}
