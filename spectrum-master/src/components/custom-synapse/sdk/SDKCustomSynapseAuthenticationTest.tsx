import { Alert } from 'antd';
import { ChangeEvent, useCallback, useEffect, useState } from 'react';

import Button from 'components/Button';
import InfoBox from 'components/InfoBox';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import Spinner from 'components/Spinner';
import { AuthConfig, Connector } from 'reducers/connectorReducer';
import { AuthTypes } from 'store/credential/types';
import { useGetCustomSynapseDraftQuery, useTestCustomSynapseMutation } from 'store/custom-synapse/sdk/api';
import { TestConnectionResponse } from 'store/custom-synapse/types';
import AppConstants from 'utils/AppConstants';
import { t, tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import { SDKCustomSynapseFileState } from './SDKCustomSynapseFileUpload';

export interface SDKCustomSynapseAuthenticationTestProps extends SkullRenderTypeBaseProps {
  defaultValue: SDKCustomSynapseFileState;
}

const tn = tNamespaced('CustomSynapse');

const SDKCustomSynapseAuthenticationTest = ({ defaultValue }: SDKCustomSynapseAuthenticationTestProps) => {
  const { data: customSynapseDraft, isLoading } = useGetCustomSynapseDraftQuery({
    connectorMetaDefinitionId: defaultValue.id as string,
  });

  const [testCustomSynapse] = useTestCustomSynapseMutation();

  const [loading, setLoading] = useState(false);
  const [testResponse, setTestResponse] = useState<TestConnectionResponse | null>(null);

  const [formData, setFormData] = useSetState<Partial<Connector & { authType: AuthTypes }>>({
    id: defaultValue.id,
    name: defaultValue.name,
    metadataId: defaultValue.id,
  });

  const onChange = useCallback(
    (evt: ChangeEvent<HTMLInputElement>) => {
      setFormData({
        [evt.target.name]: evt.target.value,
      });
    },
    [setFormData]
  );

  const onChangeAuthType = useCallback((authType: AuthTypes) => setFormData({ authType }), [setFormData]);

  const [authConfig, setAuthConfig] = useSetState<Partial<AuthConfig>>({});

  const onChangeAuth = useCallback(
    (evt: ChangeEvent<HTMLInputElement>) => {
      setAuthConfig({ [evt.target.name]: evt.target.value });
    },
    [setAuthConfig]
  );

  useEffect(() => {
    const authType = customSynapseDraft?.supportedAuthTypes?.[0]?.authType;
    if (authType) {
      setFormData({ authType });
    }
  }, [customSynapseDraft?.supportedAuthTypes, setFormData]);

  if (isLoading) {
    return (
      <CenterLayout>
        <Spinner key="loading-spin" />
      </CenterLayout>
    );
  }

  const values = customSynapseDraft?.supportedAuthTypes?.map(({ label, authType: value }) => ({ label, value }));

  const selectedAuthTypeObject = customSynapseDraft?.supportedAuthTypes?.find(
    (authType) => authType.authType === formData.authType
  );

  return (
    <Stack>
      <InfoBox
        message={tn('test_auth_title', { name: defaultValue.displayName })}
        description={tn('test_auth_description')}
      />
      <InputWithLabel
        name="authType"
        onChange={onChangeAuthType}
        value={formData.authType}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        optionData={values}
        label={t('ConnectorConfigure.authentication_method')}
      />
      <InputWithLabel
        name="endpoint"
        label="Endpoint URL"
        onChange={onChange}
        value={formData.endpoint ?? ''}
        datatype={AppConstants.INPUT_TYPE.STRING}
      />

      {selectedAuthTypeObject?.fields.map(({ name, label, dataType, helpSummary }) => {
        return (
          <InputWithLabel
            key={name}
            name={name}
            onChange={onChangeAuth}
            value={authConfig[name as keyof AuthConfig] || ''}
            tooltip={helpSummary ?? ''}
            datatype={dataType}
            label={label}
          />
        );
      })}

      {!!testResponse && (
        <Alert
          type={testResponse.success ? 'success' : 'error'}
          message={
            testResponse.success
              ? tn('authentication_was_successful')
              : testResponse.errors?.length
              ? testResponse.errors.join('; ')
              : testResponse.message
          }
        />
      )}

      <Button
        loading={loading}
        onClick={() => {
          setTestResponse(null);
          setLoading(true);

          testCustomSynapse({ ...formData, authConfig: authConfig as AuthConfig })
            .unwrap()
            .then(setTestResponse)
            .then(() => setLoading(false));
        }}>
        {tn('test_authentication')}
      </Button>
    </Stack>
  );
};

export default SDKCustomSynapseAuthenticationTest;
