import './ApiUserCreds.less';
import { Input } from 'antd';

import { CopyToClipboard } from 'components/copy-to-clipboard/CopyToClipboard';
import { tNamespaced } from 'utils/i18nUtil';

const t = tNamespaced('Profile');

interface ApiUserCredsProps {
  clientId: string;
  clientSecret: string;
}

export const ApiUserCreds = ({ clientId, clientSecret }: ApiUserCredsProps) => {
  return (
    <div className="api-user-credentials">
      <h3>{t('api_access')}</h3>
      <div className="synri-inline-message info api-user-credentials__message">
        {/* Manually replicating InlineMessage b/c it cannot handle elements in children */}
        Copy these credentials and store them in a secure place. <strong>API keys are not recoverable</strong>, this
        will be the only time you can access them.
      </div>
      <label className="synri-label" htmlFor="editProfile_email">
        {t('fields.clientId')}
      </label>
      <div className="api-user-credentials__field">
        <Input.Password readOnly value={clientId} />
        <CopyToClipboard textToCopy={clientId} textLabel={t('fields.clientId')} />
      </div>
      <label className="synri-label" htmlFor="editProfile_email">
        {t('fields.clientSecret')}
      </label>
      <div className="api-user-credentials__field">
        <Input.Password readOnly value={clientSecret} />
        <CopyToClipboard textToCopy={clientSecret} textLabel={t('fields.clientSecret')} />
      </div>
    </div>
  );
};
