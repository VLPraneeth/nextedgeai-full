import { RouteComponentProps } from '@reach/router';
import Alert from 'antd/lib/alert';
import Icon from 'antd/lib/icon';
import Input, { InputProps } from 'antd/lib/input';
import message from 'antd/lib/message';
import Modal from 'antd/lib/modal';
import Spin from 'antd/lib/spin';
import * as React from 'react';
import { useCallback, useEffect, useState } from 'react';

import Button from 'components/Button';
import Can from 'components/Can';
import { HStack, Stack } from 'components/layout';
import Switch from 'components/Switch';
import { TranslatedText } from 'components/typography';
import useClipboard from 'hooks/clipboard';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { useWindowTitle } from 'hooks/windowTitle';
import {
  selectSsoIdpDeleteStatus,
  selectSsoIdpFetchStatus,
  selectSsoIdpUpdateError,
  selectSsoIdpUpdateStatus,
} from 'store/organization/selectors';
import {
  getOrganizationSsoConfig,
  removeOrganizationSsoConfig,
  updateOrganizationSsoConfig,
} from 'store/organization/thunks';
import { IdentityProvider, IdentityProviderConfig } from 'store/organization/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { currentOrigin, replaceToken } from 'utils/UrlUtil';

import './SsoConfig.less';

const { FETCH_STATUS } = AppConstants;

const tn = tNamespaced('Settings.SsoConfig');

const makeSsoUrl = (organizationId: string) =>
  `${currentOrigin}${replaceToken(RouteConstants.SSO_ENTITY_URL, { organizationId })}`;

const makeAssertionUrl = (organizationId: string) =>
  `${currentOrigin}${replaceToken(RouteConstants.SSO_ASSERTION_URL, { organizationId })}`;

interface CopyableInputProps extends Omit<InputProps, 'value'> {
  value: string;
}

export const CopyableInput = ({ value, ...props }: CopyableInputProps) => {
  const { addToClipboard } = useClipboard();

  return (
    <Input
      disabled
      readOnly
      value={value}
      {...props}
      addonAfter={
        <span
          className="input-addon-button"
          role="button"
          onClick={() => {
            try {
              addToClipboard(value);
              message.success(tc('copy_to_clipboard_success'));
            } catch (err) {
              message.error(tc('copy_to_clipboard_failure'));
            }
          }}>
          <Icon type="copy" />
        </span>
      }
    />
  );
};

interface FieldProps {
  label: string;
  children?: React.ReactNode;
}

const Field = ({ label, children }: FieldProps) => {
  return (
    <label>
      <Stack spacing="xs">
        <span className="syncari-label">{label}</span>
        {children}
      </Stack>
    </label>
  );
};

interface IdpConfigFormProps {
  orgId: string;
  provider: IdentityProvider;
  config?: IdentityProviderConfig;
  title: string;
  submitting: boolean;
}

const IdpConfigForm = ({ orgId, provider, title, submitting, config }: IdpConfigFormProps) => {
  const [certificate, setCertificate] = useState(() => config?.certificate || '');

  const dispatch = useEnhancedDispatch();
  const updateError = useEnhancedSelector((state) => selectSsoIdpUpdateError(state, provider));

  const ssoUrl = makeSsoUrl(orgId);
  const updateErrorMsg = updateError?.errorMessage || updateError?.message;

  return (
    <form
      onSubmit={(evt) => {
        evt.preventDefault();

        dispatch(
          updateOrganizationSsoConfig(orgId, {
            entityId: orgId,
            provider,
            certificate,
            ssoUrl,
          })
        );
      }}>
      <Stack>
        {updateErrorMsg && <Alert type="error" message={updateErrorMsg} />}
        <Field label={tn(`${provider}.sso_entity_url`)}>
          <CopyableInput value={ssoUrl} />
        </Field>

        <Field label={tn(`${provider}.sso_assertion_url`)}>
          <CopyableInput value={makeAssertionUrl(orgId)} />
        </Field>

        <Field label={tn(`${provider}.x509`)}>
          <Input.TextArea
            className="certificate-textarea"
            datatype="textarea"
            rows={20}
            value={certificate}
            onChange={(evt: React.ChangeEvent<HTMLTextAreaElement>) => setCertificate(evt.target.value)}
            required
          />
        </Field>

        <Can permission={AllPermissions.WRITE_SSO}>
          <Button htmlType="submit" type="primary" loading={submitting}>
            {tc('save')}
          </Button>
        </Can>
      </Stack>
    </form>
  );
};

interface IdpPanelProps {
  loading: boolean;
  orgId: string;
  provider: IdentityProvider;
  config?: IdentityProviderConfig;
}

const IdpPanel = ({ loading, orgId, provider, config }: IdpPanelProps) => {
  const hasConfig = Boolean(config);
  const [isOpen, setIsOpen] = useState(() => hasConfig);
  const title = tn(`${provider}.title`);
  const dispatch = useEnhancedDispatch();

  useEffect(() => {
    setIsOpen(!!hasConfig);
  }, [hasConfig]);

  const updateStatus = useEnhancedSelector((state) => selectSsoIdpUpdateStatus(state, provider));
  const deleteStatus = useEnhancedSelector((state) => selectSsoIdpDeleteStatus(state, provider));

  useToastForFetchStatusChange(updateStatus, {
    error: tn('sso_update_failure'),
    success: tn('sso_update_success'),
  });
  useToastForFetchStatusChange(deleteStatus, {
    error: tn('sso_delete_failure'),
    success: tn('sso_delete_success'),
  });

  const toggleEnable = useCallback(() => {
    if (isOpen) {
      Modal.confirm({
        title: tn('disable_confirm_title'),
        content: <TranslatedText namespace="Settings.SsoConfig" text="disable_warning_message" beDangerous />,
        onOk: () => {
          setIsOpen(false);
          dispatch(removeOrganizationSsoConfig(orgId, { provider, entityId: '', certificate: '', ssoUrl: '' }));
        },
        okText: tn('disable_ok_text'),
        okType: 'danger',
        cancelText: tn('disable_cancel_text'),
      });
    } else {
      setIsOpen(true);
    }
  }, [dispatch, isOpen, orgId, provider]);

  return (
    <div className="idp-section">
      <HStack className="idp-section-header" align="center">
        <Can permission={AllPermissions.WRITE_SSO}>
          <Switch checked={isOpen} onChange={() => toggleEnable()} />
        </Can>
        <h2 className="title">{title}</h2>
        {loading && <Spin size="small" style={{ marginLeft: 'auto' }} />}
      </HStack>
      {!loading && isOpen && (
        <IdpConfigForm
          key={config?.entityId}
          title={title}
          config={config}
          orgId={orgId}
          provider={provider}
          submitting={updateStatus === FETCH_STATUS.LOADING}
        />
      )}
    </div>
  );
};

// eslint-disable-next-line no-empty-pattern
const SsoConfig = ({}: RouteComponentProps) => {
  useWindowTitle(tn('page_title'));
  const dispatch = useEnhancedDispatch();
  const orgId = useEnhancedSelector((state) => state.user.orgId);

  const fetchStatus = useEnhancedSelector((state) => selectSsoIdpFetchStatus(state));
  const providers = useEnhancedSelector((state) => state.organization.sso.identityProviders);

  useEffect(() => {
    if (Object.keys(providers).length < 1 && fetchStatus === FETCH_STATUS.IDLE) {
      dispatch(getOrganizationSsoConfig(orgId));
    }
  }, [dispatch, fetchStatus, orgId, providers]);

  return (
    <div>
      <Stack divider spacing="lg">
        <IdpPanel
          provider="OKTA"
          config={providers.OKTA}
          loading={fetchStatus === FETCH_STATUS.LOADING}
          orgId={orgId}
        />
      </Stack>
    </div>
  );
};

export default SsoConfig;
