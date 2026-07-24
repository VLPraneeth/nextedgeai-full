//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CellClassParams, ColDef, ColGroupDef } from 'ag-grid-community';
import { Button, Radio, message } from 'antd';
import cx from 'classnames';
import { noop } from 'lodash/fp';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';
import { createPortal } from 'react-dom';

import AgTable from 'components/AgTable';
import ActionHeader, { Header } from 'components/custom-action/ActionHeader';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { JsonRendererPopover } from 'components/JsonRendererPopover';
import { HStack, Stack } from 'components/layout';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { webhookCustomSynapseSteps } from 'pages/connector/custom-synapse/webhook/WebhookCustomSynapse.skull';
import {
  useGetWebhookCustomSypapseAuthtypesQuery,
  useWebhookCustomSypapseTestMutation,
} from 'store/custom-synapse/webhook/api';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import { getAuthInputs } from '../http/HTTPCustomSynapseAuthStep';
import {
  WebhookCustomSynapseConfigurationProps,
  webhookCustomSynapseInitialState,
} from './WebhookCustomSynapseConfigureStep';

import './WebhookCustomSynapseConfigureStep.scss';
const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

type TestTab = 'records' | 'response';

export const WebhookCustomSynapseTestPayloadStep = ({
  onChange,
  defaultValue,
}: WebhookCustomSynapseConfigurationProps) => {
  const { close, next, previous } = useSkullConfigContext();
  const [webhookSynapse, setWebhookSynapse] = useSetState(() => {
    return {
      ...webhookCustomSynapseInitialState,
      ...defaultValue,
      authConfig: {
        ...(defaultValue.authConfig || {}),
      },
    };
  });
  const [webhookTest, { isLoading, data: testResult }] = useWebhookCustomSypapseTestMutation();
  const { data: authtypes } = useGetWebhookCustomSypapseAuthtypesQuery();
  const [tab, setTab] = useState<TestTab>('records');

  useEffect(() => {
    Object.keys(webhookCustomSynapseSteps).forEach((name) => {
      onChange({ name, value: webhookSynapse });
    });
  }, [webhookSynapse, onChange]);

  useEffect(() => {
    const fields = authtypes?.find((auth) => auth.authType === webhookSynapse.authType)?.fields || [];

    setWebhookSynapse((prev) => {
      const authConfigObject = fields.reduce((config, field) => {
        config[field.name] = prev.authConfig[field.name] ?? field.defaultValue;
        return config;
      }, {} as Record<string, any>);

      return {
        ...prev,
        authConfig: {
          additionalHeaders: prev.authConfig?.additionalHeaders,
          ...authConfigObject,
        },
      };
    });
  }, [authtypes, webhookSynapse.authType, setWebhookSynapse]);

  const handleAuthInputsChange = useCallback(
    (name: string, value: string) => {
      setWebhookSynapse({
        authConfig: {
          ...webhookSynapse.authConfig,
          [name]: value,
        },
      });
    },
    [webhookSynapse, setWebhookSynapse]
  );

  const responseBody = useMemo(() => {
    const resp = testResult?.response;
    return JSON.stringify(resp);
  }, [testResult]);

  const columns: (ColDef | ColGroupDef)[] = useMemo(() => {
    const record = testResult?.records[0];
    return Object.keys(record || {}).map((key) => {
      if (typeof record?.[key] === 'object' && record?.[key] !== null) {
        return {
          headerName: key,
          field: key,
          resizable: true,
          cellRendererFramework: ({ value }: CellClassParams) => {
            return <JsonRendererPopover jsonString={value} />;
          },
        };
      }
      return {
        headerName: key,
        field: key,
        resizable: true,
      };
    });
  }, [testResult]);

  const tableData = useMemo(() => {
    return testResult?.records.map((record) => {
      const newRecord: Record<string, any> = {};
      for (const key in record) {
        const value = record[key];

        if (typeof value === 'object' && value !== null) {
          newRecord[key] = JSON.stringify(value);
        } else {
          newRecord[key] = value;
        }
      }
      return newRecord;
    });
  }, [testResult]);

  const handleHeadersChange = useCallback(
    (headers: Header[]) => {
      setWebhookSynapse((prevState) => {
        return {
          ...prevState,
          authConfig: {
            ...prevState.authConfig,
            additionalHeaders: headers.reduce((acc, header) => {
              if (header.key && header.value) {
                acc[header.key] = header.value;
              }
              return acc;
            }, {} as Record<string, string>),
          },
        };
      });
    },
    [setWebhookSynapse]
  );

  const handleTest = useCallback(() => {
    const fields = authtypes?.find((auth) => auth.authType === webhookSynapse.authType)?.fields || [];

    for (const field of fields) {
      if (field && field.required && !webhookSynapse?.authConfig?.[field.name]?.trim().length) {
        message.error(tn('empty_input_validation', { label: field.label }));
        return;
      }
    }
    if (!webhookSynapse?.body?.trim()) {
      message.error(tn('empty_input_validation', { label: tn('test_payload') }));
      return;
    }
    webhookTest({
      authType: webhookSynapse.authType,
      authConfig: webhookSynapse.authConfig,
      schema: webhookSynapse.schema,
      body: webhookSynapse.body,
      idSelector: webhookSynapse.idSelector || '',
      recordSelector: webhookSynapse.recordSelector || '',
      responseCode: webhookSynapse.responseCode,
      responseTemplate: webhookSynapse.responseTemplate,
    })
      .unwrap()
      .catch((err) => message.error(getRtkQueryErrorMessage(err)));
  }, [webhookSynapse, authtypes, webhookTest]);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tc('cancel')}</Button>

      <Button onClick={previous}>{tc('previous')}</Button>

      <Button onClick={next} type="primary">
        {tc('next')}
      </Button>
    </>,
    footerRootNode
  );

  return (
    <Stack className="webhook_custom_synapse_config_step">
      {getAuthInputs(authtypes, webhookSynapse, handleAuthInputsChange)}

      <InputWithLabel
        label={tn('additional_request_headers')}
        input={
          <ActionHeader
            defaultValue={Object.keys(webhookSynapse.authConfig?.additionalHeaders || {}).map((key) => ({
              key,
              value: webhookSynapse.authConfig?.additionalHeaders?.[key],
            }))}
            onChange={handleHeadersChange}
          />
        }
      />

      <InputWithLabel
        label={tn('test_payload')}
        input={
          <CodeMirror
            className="code-mirror-container"
            value={webhookSynapse.body || ''}
            options={getCodeMirrorOptions()}
            onBeforeChange={(editor, data, body) => setWebhookSynapse({ body })}
          />
        }
      />

      <HStack justify="end">
        <Button type="primary" onClick={handleTest} loading={isLoading}>
          {tc('test')}
        </Button>
      </HStack>

      <HStack>
        <Radio.Group
          value={tab}
          className="synri-radio-container-flex"
          onChange={(e) => {
            setTab(e.target.value);
          }}>
          <Radio.Button key="response" value="response" className="synri-radio-option-flex">
            {tn('test_response')}
          </Radio.Button>
          <Radio.Button key="records" value="records" className="synri-radio-option-flex">
            {tn('records_extracted')}
          </Radio.Button>
        </Radio.Group>
      </HStack>
      {tab === 'response' && (
        <CodeMirror
          className="code-mirror-container"
          value={responseBody}
          options={getCodeMirrorOptions()}
          onBeforeChange={noop}
        />
      )}
      {tab === 'records' && (
        <AgTable
          className={cx('custom-synapse__table', !tableData?.length && 'empty')}
          domLayout="autoHeight"
          columnDefs={columns}
          rowData={tableData}
          noRowsOverlayComponentParams={{
            description: tc('no_records_found'),
          }}
        />
      )}
      {footerPortal}
    </Stack>
  );
};
