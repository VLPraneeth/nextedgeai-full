import { useMatch } from '@reach/router';
import { Button, message } from 'antd';
import { Editor, EditorChange } from 'codemirror';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';
import { createPortal } from 'react-dom';

import { ReactComponent as Chemistry } from 'assets/icons/chemistry.svg';
import { ReactComponent as CodeIcon } from 'assets/icons/code.svg';
import { ReactComponent as HeadersIcon } from 'assets/icons/headers.svg';
import { ReactComponent as PaginationIcon } from 'assets/icons/pagination.svg';
import { ReactComponent as RecordsIcon } from 'assets/icons/records.svg';
import { ReactComponent as SchemaIcon } from 'assets/icons/schema.svg';
import { ReactComponent as WandIcon } from 'assets/icons/wand.svg';
import ActionHeader, { Header } from 'components/custom-action/ActionHeader';
import { decodeResponseBody } from 'components/custom-action/ActionRequestResponse';
import ActionTesting, { ActionTestingValue } from 'components/custom-action/ActionTesting';
import ActionVariable, { Variable } from 'components/custom-action/ActionVariable';
import { CustomAction } from 'components/custom-action/types';
import { InlineTab, InlineTabs } from 'components/InlineTabs';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { SelectTextValue } from 'components/inputs/select-text/SelectText';
import { Spacer, Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { Text } from 'components/typography';
import { entitiesItemPath } from 'pages/connector/custom-synapse/CustomSynapseBreadcrumb';
import { httpCustomSynapseEntitySteps } from 'pages/connector/custom-synapse/http/entities/Entity.skull';
import {
  useGenerateHttpCustomSynapseEntitySchemaMutation,
  useGetHttpCustomSynapseEntityPaginationQuery,
} from 'store/custom-synapse/http/api';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import AppConstants from 'utils/AppConstants';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { parseJSON } from 'utils/JsonUtil';
import { humanize } from 'utils/StringUtil';
import useSetState from 'utils/useSetState';

import { httpMethodPiclistValues } from '../HTTPTest';
import { entitiesInitialState } from './EntityBasicStep';
import { EntityPagination } from './EntityPagination';
import { EntityRecord } from './EntityRecord';
import { EntitySchema } from './EntitySchema';

import './EntityConfigStep.scss';

export interface EntityConfigStepProps extends SkullRenderTypeBaseProps {
  defaultValue: HTTPCustomSynapseEntity;
}

export enum EntityConfigTabs {
  HEADERS = 'headers',
  BODY = 'body',
  VARIABLES = 'variables',
  TESTING = 'testing',
  SCHEMA = 'schema',
  RECORDS = 'records',
  PAGINATION = 'pagination',
}

const TabIconMap = {
  [EntityConfigTabs.HEADERS]: HeadersIcon,
  [EntityConfigTabs.BODY]: CodeIcon,
  [EntityConfigTabs.VARIABLES]: WandIcon,
  [EntityConfigTabs.TESTING]: Chemistry,
  [EntityConfigTabs.SCHEMA]: SchemaIcon,
  [EntityConfigTabs.RECORDS]: RecordsIcon,
  [EntityConfigTabs.PAGINATION]: PaginationIcon,
};

const httpSynapseTn = tNamespaced('CustomSynapse.HttpCustomSynapse');
const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export const EntityConfigStep = ({ defaultValue, onChange }: EntityConfigStepProps) => {
  const [entity, setEntity] = useSetState(() => {
    return { ...entitiesInitialState, ...defaultValue };
  });

  const { close, previous, next } = useSkullConfigContext();

  const [currentTab, setCurrentTab] = useState('headers');
  const [testingValue, setTestingValue] = useState<ActionTestingValue | undefined>();
  const [generateSchema, { isLoading: generatingSchema }] = useGenerateHttpCustomSynapseEntitySchemaMutation();
  const { data: paginationOptions } = useGetHttpCustomSynapseEntityPaginationQuery();

  const entityMatch = useMatch(entitiesItemPath);
  const isPublished = entityMatch?.version === 'published';

  useEffect(() => {
    Object.keys(httpCustomSynapseEntitySteps).forEach((name) => {
      onChange({ name, value: entity });
    });
  }, [entity, onChange]);

  const handleEndpointChange = useCallback(
    (value: SelectTextValue) => {
      setEntity({
        endpoint: value.textValue,
        method: value.selectValue,
      });
    },
    [setEntity]
  );

  const handleHeadersChange = useCallback(
    (headers: Header[]) => {
      setEntity({
        headers: headers.reduce<Record<string, string>>((acc, header) => {
          if (header.key && header.value) {
            acc[header.key] = header.value;
          }
          return acc;
        }, {}),
      });
    },
    [setEntity]
  );

  const handleBodyChange = useCallback(
    (editor: Editor, data: EditorChange, body: string) => {
      setEntity({
        body,
      });
    },
    [setEntity]
  );

  const handleVariableChange = useCallback(
    (variables: Variable[]) => {
      setEntity({
        variables,
      });
    },
    [setEntity]
  );

  const headers = useMemo(
    () =>
      Object.keys(entity.headers || {}).map((key) => ({
        key,
        value: entity.headers?.[key],
      })),
    [entity.headers]
  );

  const setup = useMemo(
    (): CustomAction => ({
      actionConfiguration: {
        body: {
          bodyValue: entity.body,
        },
        headers,
        variables: entity.variables,
        endpoint: {
          selectValue: entity.method,
          textValue: entity.endpoint,
        },
      },
      displayName: '',
      apiName: '',
    }),
    [headers, entity.body, entity.endpoint, entity.variables, entity.method]
  );

  const handleGenerateSchema = useCallback(() => {
    if (!testingValue?.requestResponse?.response.body) {
      message.error(tn('schema_copy_empty'));
      return;
    }
    const body = decodeResponseBody(testingValue?.requestResponse?.response.body || '');
    const parsedBody = parseJSON(body);

    const payload = Array.isArray(parsedBody) ? parsedBody[0] || '' : parsedBody;

    generateSchema(payload)
      .unwrap()
      .then((data) => {
        setEntity({
          schema: JSON.stringify(data, null, 4),
        });
        message.success(tn('schema_copy_success'));
      })
      .catch(() => message.error(tn('schema_copy_error')));
  }, [generateSchema, setEntity, testingValue?.requestResponse?.response.body]);

  const handleNext = useCallback(() => {
    if (!entity.endpoint?.trim().length) {
      message.error(httpSynapseTn('empty_input_validation', { label: tc('endpoint') }));
      return;
    }
    if (!entity.method?.trim().length) {
      message.error(httpSynapseTn('empty_input_validation', { label: `${tc('endpoint')} ${tc('method')}` }));
      return;
    }

    const fields = paginationOptions?.find((option) => option.name === entity.type)?.fields || [];

    for (const field of fields) {
      if (field && field.required && !entity?.[field.name]?.toString()?.trim().length) {
        message.error(httpSynapseTn('empty_input_validation', { label: field.label }));
        return;
      }
    }
    next();
  }, [entity, next, paginationOptions]);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);

  const footerPortal = () => {
    if (!footerRootNode) {
      return null;
    }

    return createPortal(
      <>
        <Button onClick={close}>{tc('cancel')}</Button>

        <Button onClick={previous}>{tc('previous')}</Button>

        {isPublished ? (
          <Button onClick={close} type="primary">
            {tc('close')}
          </Button>
        ) : (
          <Button onClick={handleNext} type="primary">
            {tc('next')}
          </Button>
        )}
      </>,
      footerRootNode
    );
  };

  return (
    <Stack className="entity-config-step">
      <InputWithLabel
        required
        label={tc('endpoint')}
        datatype={AppConstants.INPUT_TYPE.SELECT_TEXT}
        disabled={isPublished}
        value={
          {
            selectValue: entity.method,
            textValue: entity.endpoint,
          } as SelectTextValue
        }
        selectPicklistValues={httpMethodPiclistValues}
        onChange={handleEndpointChange}
      />
      <div>
        <Stack fill className="synri-action-setup-container">
          <InlineTabs selectedTab={currentTab} onChange={setCurrentTab}>
            {Object.values(EntityConfigTabs).map((name) => {
              const Component = TabIconMap[name];
              return (
                <InlineTab id={name} key={name}>
                  <span className="synri-action-tab-icon">
                    <Component height={14} width={14} />
                  </span>
                  <Text>{humanize(name)}</Text>
                </InlineTab>
              );
            })}
          </InlineTabs>
        </Stack>

        <Stack fill>
          {currentTab === EntityConfigTabs.HEADERS && (
            <ActionHeader defaultValue={headers} onChange={handleHeadersChange} readOnly={isPublished} />
          )}
          {currentTab === EntityConfigTabs.BODY && (
            <>
              <Spacer y="lg" />
              <CodeMirror
                value={entity.body || ''}
                className="code-mirror-container"
                options={getCodeMirrorOptions(isPublished)}
                onBeforeChange={handleBodyChange}
              />
            </>
          )}
          {currentTab === EntityConfigTabs.VARIABLES && (
            <ActionVariable defaultValue={entity.variables} onChange={handleVariableChange} readOnly={isPublished} />
          )}
          {currentTab === EntityConfigTabs.TESTING && (
            <div className="entity-config-step__testing-container">
              <Spacer y="xs" />
              <ActionTesting
                customAction={setup}
                defaultValue={testingValue}
                onChange={setTestingValue}
                readOnly={isPublished}
                httpSynapseEntity={entity}
              />
              <div className="entity-config-step__schema-testing-button">
                <Button disabled={isPublished} loading={generatingSchema} onClick={handleGenerateSchema}>
                  {tn('use_response_for_schema')}
                </Button>
              </div>
            </div>
          )}
          {currentTab === EntityConfigTabs.SCHEMA && (
            <>
              <Spacer y="lg" />
              <EntitySchema
                entity={entity}
                setEntity={setEntity}
                handleGenerateSchema={handleGenerateSchema}
                readOnly={isPublished}
              />
            </>
          )}
          {currentTab === EntityConfigTabs.RECORDS && (
            <>
              <Spacer y="lg" />
              <EntityRecord entity={entity} setEntity={setEntity} readOnly={isPublished} />
            </>
          )}
          {currentTab === EntityConfigTabs.PAGINATION && (
            <>
              <Spacer y="lg" />
              <EntityPagination entity={entity} setEntity={setEntity} readOnly={isPublished} />
            </>
          )}
        </Stack>
      </div>
      {footerPortal()}
    </Stack>
  );
};
