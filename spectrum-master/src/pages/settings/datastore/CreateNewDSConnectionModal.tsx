import { Button, Form, Modal, message } from 'antd';
import produce from 'immer';
import { camelCase, delay, get, omit } from 'lodash';
import { ChangeEvent, useCallback, useState } from 'react';

import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { Stack } from 'components/layout';
import { AuthTypeConfigFields, ConfigureFields, Connector } from 'reducers/connectorReducer';
import {
  useCreateConnectionMutation,
  useCreateOauthRedirectUrlQueryMutation,
  useGetDataStoreDescribeQuery,
  useOAuthenticateDatastoreMutation,
  useUpdateDatastoreConnectionMutation,
  useGetConnectorInfoMutation,
} from 'store/datastore/api';
import AppConstants from 'utils/AppConstants';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tCommon, tNamespaced } from 'utils/i18nUtil';
import { copyStringToClipboard, humanize } from 'utils/StringUtil';
import classNames from 'classnames';
import { DataStoreConfig } from 'store/datastore/types';
import { findConnectorById } from 'utils/ConnectorUtil';
import { waitForWindowClose } from './datstore.utils';

const tn = tNamespaced('Settings.DataStore');

export interface CreateNewDSConnectionModalProps {
  open: boolean;
  onClose: () => void;
}

const CreateNewDSConnectionModal = ({ open, onClose: closeModal }: CreateNewDSConnectionModalProps) => {
  const toastDuration = 4;
  const { data: dsList } = useGetDataStoreDescribeQuery();
  const [createConnection, { isLoading: isCreating }] = useCreateConnectionMutation();
  const [updateDatastoreConnection] = useUpdateDatastoreConnectionMutation();
  const [newConnectionInfo, setNewConnectionInfo] = useState<any>({});
  const [oAuthenticateDatastore] = useOAuthenticateDatastoreMutation();
  const [getConnectorInfo] = useGetConnectorInfoMutation();

  const [createOauthRedirectUrl] = useCreateOauthRedirectUrlQueryMutation();

  const [redirectUrl, setRedirectUrl] = useState('');
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  const [formState, setFormState] = useState<Partial<Connector>>({});

  const onClose = useCallback(() => {
    setRedirectUrl('');
    setIsAuthenticating(false);
    setNewConnectionInfo({});
    setFormState({});
    closeModal();
  }, [closeModal]);

  const verifyConnection = async () => {
    try {
      const connectorResponse = await getConnectorInfo();
      if ('error' in connectorResponse) {
        message.error(getRtkQueryErrorMessage(connectorResponse.error), toastDuration);
        return;
      }
      const connector = findConnectorById(newConnectionInfo?.id, connectorResponse.data);
      if (!connector) return;
      if (connector.status === AppConstants.CONNECTOR_STATUS.AUTHENTICATED) {
        setIsAuthenticating(false);
        message.success(tn('connection_saved', { connectionStatus: humanize(connector.status) }), toastDuration);
        onClose();
      } else {
        setIsAuthenticating(false);
        message.error(getRtkQueryErrorMessage({ message: tn('validation_failed') }), toastDuration);
      }
    } catch {
      setIsAuthenticating(false);
      message.error(getRtkQueryErrorMessage({ message: tn('validation_failed') }));
    }
  };

  const oAuthClose = (closeParams: any) => {
    const { oAuthWindow } = window.oAuth || {};
    delay(verifyConnection, 0);
    if (closeParams.success && oAuthWindow) oAuthWindow.close();
  };

  const handleOauthFlow = async () => {
    const formPayload = { ...formState, id: newConnectionInfo.id };

    try {
      const response = await updateDatastoreConnection(omit(formPayload, ['authenticationConfig']) as DataStoreConfig);
      if ('error' in response) {
        setIsAuthenticating(false);
        message.error(getRtkQueryErrorMessage(response?.error), toastDuration);
        return;
      }
      await startOAuthProcess(response?.data?.id);
    } catch (err: any) {
      setIsAuthenticating(false);
      message.error(getRtkQueryErrorMessage(err), toastDuration);
    }
  };

  const startOAuthProcess = async (connectionId: string) => {
    try {
      const res = await oAuthenticateDatastore(connectionId);
      if ('error' in res) {
        setIsAuthenticating(false);
        message.error(getRtkQueryErrorMessage(res.error), toastDuration);
        return;
      }
      const authUrl = res?.data?.location;
      const oauthWindow = window.open(
        authUrl,
        '_blank',
        'toolbar=yes,scrollbars=yes,resizable=yes,top=150,left=500,width=650,height=750'
      );
      if (!oauthWindow) {
        setIsAuthenticating(false);
        message.error(getRtkQueryErrorMessage({ message: 'Failed to open OAuth window.' }), toastDuration);
        return;
      }
      await waitForWindowClose(oauthWindow);
      oAuthClose({ success: true });
    } catch (err: any) {
      setIsAuthenticating(false);
      message.error(getRtkQueryErrorMessage(err), toastDuration);
    }
  };

  const handleCreateConnection = async () => {
    if (formState.metaConfig?.authType === AppConstants.AUTH_TYPES.OAUTH) {
      setIsAuthenticating(true);
      handleOauthFlow();
    } else {
      createConnection(formState).then((res) => {
        if ('error' in res) {
          message.error(getRtkQueryErrorMessage(res.error), toastDuration);
        } else {
          message.success(tn('connection_saved', { connectionStatus: humanize(res?.data?.status) }), toastDuration);
          onClose();
        }
      });
    }
  };

  const getInput = (field: ConfigureFields, namespace: string, isDisabled?: boolean) => {
    const { name, label, dataType, helpSummary } = field;

    const value = get(formState, [namespace, name].filter(Boolean).join('.'));

    const isCheckbox = dataType === AppConstants.INPUT_TYPE.CHECKBOX;

    return (
      <>
        <InputWithLabel
          key={name}
          name={name}
          label={label}
          value={value}
          disabled={isDisabled}
          checked={isCheckbox ? value : undefined}
          onChange={(evt: ChangeEvent<HTMLInputElement>) => {
            let newValue: string | number | boolean = typeof evt !== 'object' ? evt : evt.target.value;

            if (isCheckbox) {
              newValue = evt.target.checked;
            }

            setFormState((currentState) =>
              produce(currentState, (draft: any) => {
                if (namespace) {
                  if (draft[namespace]) {
                    draft[namespace][name] = newValue;
                  } else {
                    draft[namespace] = { [name]: newValue };
                  }
                } else {
                  draft[name] = newValue;
                }
              })
            );
          }}
          required={field.required}
          tooltip={helpSummary || ''}
          datatype={dataType}
          optionData={
            name === 'authType'
              ? selectedDS?.supportedAuthTypes?.map(({ label, authType: value }) => {
                  return { label, value };
                })
              : null
          }
        />
      </>
    );
  };

  const renderOauthComp = (authType: string) => {
    const cls = classNames('synri-oauth-container');

    const triggerClick = async () => {
      try {
        const res = await createOauthRedirectUrl(formState);
        if ('error' in res) {
          message.error(getRtkQueryErrorMessage(res.error), toastDuration);
        } else {
          setNewConnectionInfo(res?.data);
          setRedirectUrl(String(res?.data?.oauthRedirectUrl));
        }
      } catch (error) {
        message.error(getRtkQueryErrorMessage({ message: tn('oauth_redirect_url_error') }), toastDuration);
      }
    };

    const copyRedirectUrl = () => {
      copyStringToClipboard(redirectUrl);
    };

    const generateButton = !redirectUrl ? (
      <Button onClick={triggerClick} value={redirectUrl} disabled={Boolean(redirectUrl)} icon="file-add">
        {tn('generate')}
      </Button>
    ) : (
      <Button onClick={copyRedirectUrl} icon="copy">
        {tc('copy')}
      </Button>
    );

    return (
      <div className={cls}>
        <Form.Item>
          <InputWithLabel key="generate_register" label={tn('generate_register')} disabled value={redirectUrl} />
        </Form.Item>
        <InputWithLabel
          key="default-auth-type"
          label={tn('generate')}
          name={`btn${camelCase(authType)}`}
          input={generateButton}
        />
      </div>
    );
  };

  const selectedDS = dsList?.find((item) => item.id === formState.metadataId);

  let authFields: AuthTypeConfigFields[] = [];
  const authTypeConfig = selectedDS?.supportedAuthTypes?.find(
    (authInfo) => authInfo.authType === formState.metaConfig?.authType
  );
  if (authTypeConfig) {
    authFields = authTypeConfig.fields;
  }
  const isDisabledCreateconnection = !redirectUrl && authTypeConfig?.authType === AppConstants.AUTH_TYPES.OAUTH;

  return (
    <Modal
      title={tn('create_new_connection')}
      visible={open}
      onCancel={onClose}
      destroyOnClose
      className="synri-create-ds-connection-modal"
      footer={
        <>
          <Button key="cancel" onClick={onClose} disabled={isCreating || isAuthenticating}>
            {tCommon('cancel')}
          </Button>
          <Button
            key="ok"
            type="primary"
            loading={isCreating || isAuthenticating}
            disabled={isDisabledCreateconnection}
            onClick={handleCreateConnection}>
            {isAuthenticating ? tn('authenticating') : tn('create_connection')}
          </Button>
        </>
      }>
      <div className="ds-modal-content">
        <Stack>
          <InputWithLabel
            label={tCommon('name')}
            value={formState.name}
            placeholder={tn('add_connection_name')}
            datatype="string"
            onChange={(event: ChangeEvent<HTMLInputElement>) => {
              setFormState((currentState) =>
                produce(currentState, (draft) => {
                  draft.name = event?.target.value;
                })
              );
            }}
          />
          <InputWithLabel
            label={tn('data_store')}
            input={
              <Select
                disabled={Boolean(redirectUrl)}
                value={formState.metadataId}
                onChange={(value: string) => {
                  setFormState((currentState) =>
                    produce(currentState, (draft) => {
                      draft.metadataId = value;
                    })
                  );
                }}
                placeholder={tn('select_data_store')}
                optionData={dsList
                  ?.filter(({ creatable }) => creatable)
                  ?.map((item) => {
                    return {
                      label: item.displayName || '',
                      value: item.id || '',
                    };
                  })}
              />
            }
          />
        </Stack>
        {selectedDS?.configureFields.map((field) => getInput(field, 'metaConfig', Boolean(redirectUrl)))}

        {authFields?.map((field) => getInput(field, 'authConfig'))}

        {authTypeConfig?.authType === AppConstants.AUTH_TYPES.OAUTH && renderOauthComp(authTypeConfig.authType)}
      </div>
    </Modal>
  );
};

export default CreateNewDSConnectionModal;
