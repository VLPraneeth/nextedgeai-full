import { Button, message } from 'antd';
import { useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';

import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { Text } from 'components/typography';
import { httpCustomSynapseSteps } from 'pages/connector/custom-synapse/http/HTTPCustomSynapse.skull';
import { SupportedAuthType } from 'store/credential/types';
import { useGetHttpCustomSypapseAuthtypesQuery } from 'store/custom-synapse/http/api';
import AppConstants from 'utils/AppConstants';
import { tNamespaced, tc } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import { CustomSynapse } from '../types';
import { httpCustomSynapseInitialState } from './HTTPCustomSynapseConfigureStep';
import { HTTPTest } from './HTTPTest';

export interface HTTPCustomSynapseReviewProps extends SkullRenderTypeBaseProps {
  defaultValue: CustomSynapse;
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');

export const getAuthInputs = (
  supportedAuthTypes: SupportedAuthType[] | undefined,
  customSynapse: CustomSynapse,
  onChange: (name: string, value: string) => void
) => {
  const inputs: Array<JSX.Element | undefined> = [];

  supportedAuthTypes?.forEach((auth: SupportedAuthType) => {
    if (customSynapse?.authType === auth.authType) {
      auth.fields.forEach((field) => {
        const { name, dataType, label, helpSummary: tooltip, required, options } = field;

        if (dataType !== 'httptest') {
          inputs.push(
            <InputWithLabel
              key={name}
              name={name}
              onChange={(arg: any) => {
                if (dataType === AppConstants.INPUT_TYPE.PICKLIST) {
                  onChange(name, arg);
                } else {
                  onChange(name, arg.target.value);
                }
              }}
              value={customSynapse.authConfig?.[name]}
              datatype={dataType === AppConstants.INPUT_TYPE.BOOLEAN ? AppConstants.INPUT_TYPE.CHECKBOX : dataType}
              tooltip={tooltip!}
              required={required}
              label={label}
              optionData={options}
            />
          );
        }
      });
    }
  });
  return inputs;
};

function getVariableInputs(httpSynapse: CustomSynapse, onChange: (name: string, value: string) => void) {
  const inputs: Array<JSX.Element | undefined> = [];
  httpSynapse.variableValues?.forEach(({ name, value }) => {
    if (name) {
      inputs.push(
        <InputWithLabel
          key={name}
          name={name}
          onChange={(event: any) => {
            onChange(name, event.target.value);
          }}
          value={value}
          label={name}
        />
      );
    }
  });
  return inputs;
}

const HTTPCustomSynapseAuthStep = ({ defaultValue, onChange }: HTTPCustomSynapseReviewProps) => {
  const { data: supportedAuthType } = useGetHttpCustomSypapseAuthtypesQuery();
  const { close, previous, next } = useSkullConfigContext();

  const [httpSynapse, setHttpSynapse] = useSetState(() => {
    return { ...httpCustomSynapseInitialState, ...defaultValue };
  });

  useEffect(() => {
    Object.keys(httpCustomSynapseSteps).forEach((name) => {
      onChange({ name, value: httpSynapse });
    });
  }, [httpSynapse, onChange]);

  const handleNext = useCallback(() => {
    const fields = supportedAuthType?.find((auth) => auth.authType === httpSynapse.authType)?.fields || [];

    for (const field of fields) {
      if (field && field.required && !httpSynapse?.authConfig?.[field.name]?.trim().length) {
        message.error(tn('empty_input_validation', { label: field.label }));
        return;
      }
    }
    next();
  }, [httpSynapse, next, supportedAuthType]);

  function handleAuthInputsChange(name: string, value: string) {
    setHttpSynapse({
      authConfig: {
        ...httpSynapse.authConfig,
        [name]: value,
      },
    });
  }

  function handleVariableInputsChange(name: string, value: string) {
    setHttpSynapse({
      variableValues: httpSynapse.variableValues?.map((variable) =>
        variable.name === name ? { ...variable, value } : variable
      ),
    });
  }

  const shouldShowEndpoint = supportedAuthType?.find((auth) => {
    return httpSynapse.authType === auth.authType && auth.fields.find((field) => field.dataType === 'httptest');
  });

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);

  const footerPortal = () => {
    if (!footerRootNode) {
      return null;
    }
    return createPortal(
      <>
        <Button onClick={close}>{tc('cancel')}</Button>

        <Button onClick={previous}>{tc('previous')}</Button>

        <Button onClick={handleNext} type="primary">
          {tc('next')}
        </Button>
      </>,
      footerRootNode
    );
  };

  return (
    <Stack>
      {httpSynapse.authType === 'None' ? (
        <InputWithLabel label={tn('authentication_type')} input={<Text>{httpSynapse.authType}</Text>} />
      ) : (
        <Stack>
          {getAuthInputs(supportedAuthType, httpSynapse, handleAuthInputsChange)}
          {getVariableInputs(httpSynapse, handleVariableInputsChange)}
          {shouldShowEndpoint && <HTTPTest httpSynapse={httpSynapse} setFormData={setHttpSynapse} />}
        </Stack>
      )}
      {footerPortal()}
    </Stack>
  );
};

export default HTTPCustomSynapseAuthStep;
