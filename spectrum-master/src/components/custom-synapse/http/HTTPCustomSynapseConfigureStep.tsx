//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Button, message } from 'antd';
import { RcFile } from 'antd/lib/upload';
import { ChangeEvent, useCallback, useEffect } from 'react';
import { createPortal } from 'react-dom';

import { SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS } from 'components/imageUpload/ImageUpload';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { httpCustomSynapseSteps } from 'pages/connector/custom-synapse/http/HTTPCustomSynapse.skull';
import { AuthTypes } from 'store/credential/types';
import { useGetHttpCustomSypapseAuthtypesQuery } from 'store/custom-synapse/http/api';
import AppConstants from 'utils/AppConstants';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { createApiName } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';
import useSetState from 'utils/useSetState';

import { DEFAULT_CUSTOM_SYNAPSE_ICON } from '../sdk/SDKCustomSynapseFileUpload';
import { CustomSynapse, HttpSynapseMetadata } from '../types';
import { AdditionalMetadata } from './AdditionalMetadata';

import './HTTPCustomSynapseConfigureStep.scss';

export interface HTTPCustomSynapseConfigureStepProps extends SkullRenderTypeBaseProps {
  defaultValue: CustomSynapse;
}

export const httpCustomSynapseInitialState: Partial<CustomSynapse> & { iconFile?: RcFile } = {
  id: '',
  name: '',
  displayName: '',
  authType: 'None',
};

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse');
const tnCustomSynapse = tNamespaced('CustomSynapse');

export const HTTPCustomSynapseConfigureStep = ({ onChange, defaultValue }: HTTPCustomSynapseConfigureStepProps) => {
  const { data: authtypes } = useGetHttpCustomSypapseAuthtypesQuery();
  const { close, next } = useSkullConfigContext();
  const [httpSynapse, setHttpSynapse] = useSetState(() => {
    return { ...httpCustomSynapseInitialState, ...defaultValue };
  });

  useEffect(() => {
    Object.keys(httpCustomSynapseSteps).forEach((name) => {
      onChange({ name, value: httpSynapse });
    });
  }, [httpSynapse, onChange]);

  const defaultCustomIconUrl = httpSynapse.id
    ? makeUrl(DataUrlConstants.CUSTOM_SYNAPSE_ICON, {
        connectorMetaDefinitionId: httpSynapse.id,
      })
    : DEFAULT_CUSTOM_SYNAPSE_ICON;

  const onMetadataChange = useCallback(
    (variables: HttpSynapseMetadata[]) => {
      setHttpSynapse((prevState) => {
        return {
          ...prevState,
          variables,
          variableValues: variables.map((vari) => ({
            name: vari.name,
            value: prevState.variableValues?.find((val) => val.name === vari.name)?.value,
          })),
        };
      });
    },
    [setHttpSynapse]
  );

  const handleNext = useCallback(() => {
    if (!httpSynapse.displayName.trim().length) {
      message.error(tn('empty_input_validation', { label: tc('display_name') }));
      return;
    }
    if (!httpSynapse.name.trim().length) {
      message.error(tn('empty_input_validation', { label: tc('api_name') }));
      return;
    }
    next();
  }, [httpSynapse, next]);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tc('cancel')}</Button>

      <Button onClick={handleNext} type="primary">
        {tc('next')}
      </Button>
    </>,
    footerRootNode
  );

  return (
    <Stack className="http_custom_synapse_config_step">
      <InputWithLabel
        label={tc('display_name')}
        required
        value={httpSynapse.displayName}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setHttpSynapse({ displayName: newName.target.value });
        }}
        onBlur={() => {
          if (httpSynapse.displayName && !httpSynapse.id && !httpSynapse.name) {
            setHttpSynapse({ name: createApiName(httpSynapse.displayName) });
          }
        }}
      />

      <InputWithLabel
        label={tc('api_name')}
        // The name is not editable except when creating a new custom synapse
        disabled={!!httpSynapse.id}
        required
        value={httpSynapse.name}
        onChange={(newName: ChangeEvent<HTMLInputElement>) => {
          setHttpSynapse({ name: createApiName(newName.target.value) });
        }}
      />

      <InputWithLabel
        className="logo-upload"
        tooltip={tnCustomSynapse('custom_synapse_icon_tooltip')}
        defaultValue={defaultCustomIconUrl}
        datatype="image"
        id="iconFile"
        name="iconFile"
        label={tn('custom_icon')}
        value={httpSynapse.iconFile}
        accept={SUPPORTED_CUSTOM_SYNAPSE_ICON_FORMATS}
        onChange={(iconFile: RcFile) => {
          setHttpSynapse({ iconFile });
        }}
      />

      <InputWithLabel
        label={tn('authentication_type')}
        value={httpSynapse.authType}
        onChange={(authType: AuthTypes) => {
          setHttpSynapse({ authType, authConfig: {} });
        }}
        datatype={AppConstants.INPUT_TYPE.PICKLIST}
        optionData={authtypes?.map((type) => ({
          label: type.label,
          value: type.authType,
        }))}
      />

      <InputWithLabel
        label={tn('additional_metadata')}
        input={<AdditionalMetadata onChange={onMetadataChange} defaultValue={httpSynapse.variables} />}
      />

      {footerPortal}
    </Stack>
  );
};
