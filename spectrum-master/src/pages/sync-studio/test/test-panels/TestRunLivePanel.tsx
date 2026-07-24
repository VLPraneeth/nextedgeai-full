//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Col, DatePicker, Form, Icon, Input, Radio, Row, Select, Tooltip } from 'antd';
import { RadioChangeEvent } from 'antd/lib/radio';
import { SelectValue } from 'antd/lib/select';
import { isEqual, uniqBy } from 'lodash';
import moment, { Moment } from 'moment';
import { ChangeEvent, useCallback, useEffect } from 'react';
import { Controlled as CodeMirror } from 'react-codemirror2';

import { getConnectors } from 'actions/connectorActions';
import Button from 'components/Button';
import ActionHeader, { Header } from 'components/custom-action/ActionHeader';
import { getAuthInputs } from 'components/custom-synapse/http/HTTPCustomSynapseAuthStep';
import { CustomSynapse } from 'components/custom-synapse/types';
import DrawerPanel from 'components/DrawerPanel';
import HelpLink from 'components/HelpLink';
import InfoBox from 'components/InfoBox';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack, Stack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { AuthConfig, Connector } from 'reducers/connectorReducer';
import { selectAllConnectors } from 'selectors/connectorSelectors';
import { selectConnectorsForCurrentEntityPipeline } from 'selectors/entityPipelineSelectors';
import { EMPTY_ARRAY } from 'store/constants';
import { AuthTypes, SupportedAuthType, SupportedAuthTypeField } from 'store/credential/types';
import { useGetWebhookCustomSypapseAuthtypesQuery } from 'store/custom-synapse/webhook/api';
import { setTestPanelView } from 'store/test/actions';
import { selectTestPanelView } from 'store/test/selectors';
import { RunLiveTestPayload, TestPanelView } from 'store/test/types';
import { ENTITY_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import { replaceItem } from 'utils/ArrayUtil';
import { nextEdgeHelpUrl } from 'utils/Branding';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { PARSABLE_DATE_TIME_FORMAT, SHORT_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { tNamespaced } from 'utils/i18nUtil';
import useSetState from 'utils/useSetState';

import { useNavigateWhenLiveTestCompletes, useRunLiveTest } from './test-hooks/TestRunLive.hooks';
import { LiveTestDateOptions, LiveTestExternalIds, TestRunLivePanelState, TestType } from './Test.types';

import './test-styles/TestRunLivePanel.less';

const tn = tNamespaced('TestRunLivePanel');
const tc = tNamespaced('Common');
const tnWebhook = tNamespaced('CustomSynapse.WebhookCustomSynapse');

const { Group: InputGroup } = Input;
const { Option } = Select;

const groupBySynapse = (items: LiveTestExternalIds[]) => {
  const synapseGroups: Record<string, string[]> = {};

  return items.reduce((acc, item) => {
    if (!(item.source_entity_id in acc)) {
      acc[item.source_entity_id] = [];
    }
    acc[item.source_entity_id] = [...acc[item.source_entity_id], item.external_id];
    return acc;
  }, synapseGroups);
};

const initialState: TestRunLivePanelState = {
  start: null,
  end: null,
  limit: 10,
  testType: TestType.DATE,
  externalIds: [],
  sourceId: '',
  validationMessage: '',
  loading: false,
};

export interface TestRunLivePanelProps {
  onSaveChanges: () => void;
  validate: () => void;
  pipelineValidationError?: string;
}

const TestRunLivePanel = ({
  onSaveChanges,
  validate: validatePipeline,
  pipelineValidationError,
}: TestRunLivePanelProps) => {
  useNavigateWhenLiveTestCompletes();

  const [state, setState] = useSetState<TestRunLivePanelState>(initialState);

  const dispatch = useEnhancedDispatch();
  const testPipeline = useRunLiveTest();
  const visible = useEnhancedSelector(selectTestPanelView) === TestPanelView.LIVE_RUN;

  const pipelineConnectors = useEnhancedSelector(selectConnectorsForCurrentEntityPipeline);
  const errors = useEnhancedSelector((state) => state?.validation?.errors ?? EMPTY_ARRAY);
  const connectors = useEnhancedSelector(selectAllConnectors);

  const resetState = useCallback(() => setState(initialState), [setState]);

  const { data: authtypes } = useGetWebhookCustomSypapseAuthtypesQuery();

  const hasWebhookSource = pipelineConnectors.some((connector) => connector.isWebhook);

  useEffect(() => {
    if (visible && !connectors) {
      dispatch(getConnectors());
    }
  }, [connectors, dispatch, visible]);

  useEffect(() => {
    const fields: SupportedAuthTypeField[] = [];
    authtypes?.forEach((auth: SupportedAuthType) => {
      fields.push(...auth.fields);
    });

    const getAuthConfig = (connector: Connector | undefined) => {
      const authConfigObject: Record<string, any> = {};

      fields.forEach((field) => {
        if (field.dataType !== 'password' && connector?.authConfig) {
          authConfigObject[field.name] = connector?.authConfig[field.name as keyof AuthConfig];
        } else {
          authConfigObject[field.name] = null;
        }
      });
      return authConfigObject;
    };

    if (state.testType === TestType.PAYLOAD && authtypes?.length) {
      if (!state.sourceId) {
        const activePipelines = pipelineConnectors
          .filter(({ disabled }) => !disabled)
          .filter((connector) => connector.isWebhook);

        const connector = connectors.find((connector) => connector.connectorId === activePipelines[0].connectorId);
        setState({
          sourceId: activePipelines[0].sourceEntityId,
          webhookSynapse: {
            authConfig: getAuthConfig(connector),

            authType: connector?.metaConfig.authType as AuthTypes,
            id: connector?.connectorId || '',
          },
        });
      } else {
        const pipeline = pipelineConnectors.find((pipeline) => pipeline.sourceEntityId === state.sourceId);
        if (pipeline) {
          const connector = connectors.find((connector) => connector.connectorId === pipeline.connectorId);
          setState({
            webhookSynapse: {
              authConfig: getAuthConfig(connector),
              authType: connector?.metaConfig.authType as AuthTypes,
              id: connector?.connectorId || '',
            },
          });
        }
      }
    }
  }, [connectors, pipelineConnectors, setState, state.testType, state.sourceId, authtypes]);

  const close = useCallback(() => {
    dispatch(setTestPanelView(TestPanelView.CLOSED));
  }, [dispatch]);

  useEffect(() => {
    if (!visible) {
      resetState();
    }
  }, [resetState, visible]);

  const previousVisible = usePreviousValue(visible);

  useEffect(() => {
    // validatePipeline is new on every re-render so checking the
    // previousVisible to only call validate when that changes.
    if (previousVisible !== visible && visible) {
      validatePipeline();
    }
  }, [previousVisible, validatePipeline, visible]);

  const handleAuthInputsChange = useCallback(
    (name: string, value: string) => {
      setState((prevState) => {
        const currentAuthConfig = prevState.webhookSynapse?.authConfig || {};
        const updatedAuthConfig = { ...currentAuthConfig, [name]: value };

        return {
          ...prevState,
          webhookSynapse: {
            ...prevState.webhookSynapse,
            authConfig: updatedAuthConfig,
            authType: prevState.webhookSynapse?.authType,
            id: prevState.webhookSynapse?.id,
          },
        };
      });
    },
    [setState]
  );

  const handleHeadersChange = useCallback(
    (headers: Header[]) => {
      setState((prevState) => {
        const currentAuthConfig = prevState.webhookSynapse?.authConfig || {};

        const additionalHeaders = headers.reduce((acc, header) => {
          if (header.key && header.value) {
            acc[header.key] = header.value;
          }
          return acc;
        }, {} as Record<string, string>);

        const updatedAuthConfig = { ...currentAuthConfig, additionalHeaders };

        return {
          ...prevState,
          webhookSynapse: {
            ...prevState.webhookSynapse,
            authConfig: updatedAuthConfig,
          },
        };
      });
    },
    [setState]
  );

  const validate = () => {
    let newValidationMessage;

    switch (state.testType) {
      case TestType.DATE:
        const limit = Number(state.limit);
        if (!state.start && !state.end) {
          newValidationMessage = tn('select_start_end_times');
        } else if (!state.start) {
          newValidationMessage = tn('select_start_time');
        } else if (!state.end) {
          newValidationMessage = tn('select_end_time');
        } else if (moment(state.start).isSameOrAfter(moment(state.end))) {
          newValidationMessage = tn('start_before_end');
        } else if (isNaN(limit) || limit < 1 || limit > 50) {
          newValidationMessage = tn('limit_out_of_range');
        }
        break;

      case TestType.ID:
        const hasValidIds = state.externalIds.every((id) => id.external_id && id.source_entity_id);

        if (state.externalIds.length === 0) {
          newValidationMessage = tn('missing_external_ids');
        } else if (!hasValidIds) {
          newValidationMessage = tn('missing_external_id_value');
        }
        break;

      case TestType.PAYLOAD:
        if (!state.sourceId?.trim()) {
          newValidationMessage = tn('missing_source_entity');
          break;
        }
        if (!state.webhookSynapse?.body?.trim()) {
          newValidationMessage = tn('missing_payload');
          break;
        }

        const fields = authtypes?.find((auth) => auth.authType === state.webhookSynapse?.authType)?.fields || [];

        for (const field of fields) {
          if (field && field.required && !state.webhookSynapse?.authConfig?.[field.name]?.trim().length) {
            newValidationMessage = tnWebhook('empty_input_validation', { label: field.label });
            break;
          }
        }

        break;
    }

    setState({ validationMessage: newValidationMessage });

    return !newValidationMessage;
  };

  const runLiveTest = async () => {
    if (!validate()) {
      return;
    }
    setState({ loading: true });

    // Save any changes in the entity pipeline before executing the test
    await onSaveChanges();

    const { externalIds, testType, start, end, limit, webhookSynapse, sourceId } = state;
    const dedupedExternalIds = uniqBy(externalIds, (exID) => `${exID.source_entity_id}${exID.external_id}`);

    const criteria: RunLiveTestPayload =
      testType === TestType.ID
        ? {
            recordIds: groupBySynapse(dedupedExternalIds),
            limit: null,
          }
        : testType === TestType.PAYLOAD
        ? {
            webhook: sourceId
              ? {
                  [sourceId]: {
                    payload: webhookSynapse?.body || '',
                    headers: webhookSynapse?.authConfig?.additionalHeaders,
                    authConfig: webhookSynapse?.authConfig,
                    authType: webhookSynapse?.authType,
                  },
                }
              : {},
          }
        : {
            start: moment(start as string)
              .utc()
              .format(PARSABLE_DATE_TIME_FORMAT),
            end: moment(end as string)
              .utc()
              .format(PARSABLE_DATE_TIME_FORMAT),
            limit: Number(limit),
          };

    testPipeline(criteria);
  };

  const onLimitChange = (eventObj: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = eventObj.currentTarget;
    setState({ [name]: value });
  };

  const onTestTypeChange = (e: RadioChangeEvent) => {
    setState({ testType: e.target.value });
  };

  const handleDateChange = (dateKey: string) => (moment: Moment | null) => {
    const newValue = moment?.format(SHORT_DATE_TIME_FORMAT) || null;
    setState({ [dateKey]: newValue });
  };

  const onEntityTestInputChange = (i: number, name: 'source_entity_id' | 'external_id', value: SelectValue) => {
    setState((prev) => {
      const itemToUpdate = prev.externalIds[i];
      return {
        ...prev,
        externalIds: replaceItem(prev.externalIds, i, {
          ...itemToUpdate,
          [name]: value,
        }),
      };
    });
  };

  const onRemoveEntityTestRow = (i: number) => {
    setState((prev) => ({
      ...prev,
      externalIds: prev.externalIds.filter((_, idx) => idx !== i),
    }));
  };

  const addEntityTestRow = useCallback(() => {
    // When there's only one connector option pre-select it for the new row. Otherwise default is blank
    const activePipelines = pipelineConnectors
      .filter(({ disabled }) => !disabled)
      .filter((connector) => !connector.isWebhook);
    const sourceEntityId = activePipelines.length === 1 ? activePipelines[0].sourceEntityId : '';

    setState((prevState) => ({
      ...prevState,
      externalIds: [...prevState.externalIds, { source_entity_id: sourceEntityId, external_id: '' }],
    }));
  }, [pipelineConnectors, setState]);

  /**
   * If the user changes the testType to use external ids, create an empty row.
   */
  useEffect(() => {
    if (state.testType === TestType.ID && state.externalIds.length === 0) {
      addEntityTestRow();
    }
  }, [addEntityTestRow, state.externalIds.length, state.testType]);

  const createIdUi = () => {
    const { externalIds } = state;
    return (
      <Stack>
        <InputGroup>
          <label style={{ fontWeight: 'bold' }}>{tn('external_ids')}</label>

          <Stack>
            {externalIds.map((currentLiveTestIds, currentIndex) => {
              const hasDuplicate = externalIds.some(
                (otherId, index) => index < currentIndex && isEqual(otherId, currentLiveTestIds)
              );

              return (
                <HStack key={currentIndex} align="start" className="synri-child-value">
                  <Select
                    className="synri-live-test-synapse-select"
                    placeholder={tn('select_source_entitiy')}
                    value={state.externalIds[currentIndex].source_entity_id || undefined}
                    dropdownMatchSelectWidth={false}
                    onChange={(value: string) => onEntityTestInputChange(currentIndex, 'source_entity_id', value)}>
                    {pipelineConnectors
                      .filter((connector) => !connector.isWebhook)
                      .map(({ label, disabled, sourceEntityId }) => (
                        <Option key={sourceEntityId} value={sourceEntityId} disabled={disabled}>
                          {label}
                        </Option>
                      ))}
                  </Select>
                  <Form.Item
                    className="synri-live-test-synapse-id"
                    validateStatus={hasDuplicate ? 'error' : ''}
                    help={hasDuplicate ? tn('duplicate_id') : ''}>
                    <Input
                      name="external_id"
                      value={state.externalIds[currentIndex].external_id}
                      onChange={(evt) => onEntityTestInputChange(currentIndex, 'external_id', evt.target.value)}
                      placeholder={tn('enter_external_id')}
                    />
                  </Form.Item>
                  <Button icon="delete" type="danger" onClick={() => onRemoveEntityTestRow(currentIndex)} />
                </HStack>
              );
            })}
          </Stack>
          <HStack spacing="xs" className="synri-composite-key-container">
            <TranslatedText namespace="TestRunLivePanel" text="using_composite_key" color="gray-600" size="sm" />
            <Tooltip title={tn('composite_key_instructions')}>
              <Icon type="question-circle" theme="filled" className="synri-subtext-help-icon" />
            </Tooltip>
          </HStack>
        </InputGroup>

        {/* limit of 50 external IDs */}
        {state.externalIds.length < 50 && (
          <Button icon="plus" type="primary" onClick={addEntityTestRow}>
            {tc('add')}
          </Button>
        )}
      </Stack>
    );
  };

  const createPayloadUi = () => {
    return (
      <Stack>
        <InputGroup>
          <label style={{ fontWeight: 'bold' }}>{tn('source_entitiy')}</label>
          <br />

          <Select
            className="synri-live-test-external-id-select"
            placeholder={tn('select_source_entitiy')}
            value={state.sourceId || undefined}
            dropdownMatchSelectWidth={false}
            onChange={(value: string) => setState({ sourceId: value })}>
            {pipelineConnectors
              .filter((connector) => connector.isWebhook)
              .map(({ label, disabled, sourceEntityId }) => (
                <Option key={sourceEntityId} value={sourceEntityId} disabled={disabled}>
                  {label}
                </Option>
              ))}
          </Select>

          {getAuthInputs(authtypes, state.webhookSynapse as CustomSynapse, handleAuthInputsChange)}
          <InputWithLabel
            label={tn('test_payload')}
            input={
              <CodeMirror
                className="code-mirror-container"
                value={state?.webhookSynapse?.body || ''}
                options={getCodeMirrorOptions()}
                onBeforeChange={(editor, data, body) => setState({ webhookSynapse: { ...state.webhookSynapse, body } })}
              />
            }
          />

          <ActionHeader
            defaultValue={Object.keys(state?.webhookSynapse?.authConfig?.additionalHeaders || {}).map((key) => ({
              key,
              value: state?.webhookSynapse?.authConfig?.additionalHeaders?.[key],
            }))}
            onChange={handleHeadersChange}
          />
        </InputGroup>
      </Stack>
    );
  };

  const createDateUi = () => {
    return (
      <InputGroup>
        <Row>
          <Col>
            <span className="synri-label">{tn('start_time')}</span>
          </Col>
          <Col span={24}>
            <DatePicker
              className="synri-live-test-date-range-input"
              showTime
              value={state.start ? moment(state.start) : undefined}
              name={LiveTestDateOptions.start}
              placeholder={tn('start_time_placeholder')}
              format={SHORT_DATE_TIME_FORMAT}
              onChange={handleDateChange(LiveTestDateOptions.start)}
            />
          </Col>
        </Row>
        <Row>
          <Col>
            <span className="synri-label">{tn('end_time')}</span>
          </Col>
          <Col span={24}>
            <DatePicker
              className="synri-live-test-date-range-input"
              showTime
              value={state.end ? moment(state.end) : undefined}
              name={LiveTestDateOptions.end}
              placeholder={tn('end_time_placeholder')}
              format={SHORT_DATE_TIME_FORMAT}
              onChange={handleDateChange(LiveTestDateOptions.end)}
            />
          </Col>
        </Row>
        <Row>
          <Col>
            <span className="synri-label">{tn('limit')}</span>
          </Col>
          <Col span={12}>
            <Input name="limit" value={state.limit} onChange={onLimitChange} />
          </Col>
        </Row>
      </InputGroup>
    );
  };

  const footer = (
    <>
      <Button key="cancel" onClick={close}>
        {tn('cancel')}
      </Button>
      <Button disabled={state.loading || errors.length > 0} key="ok" type="primary" onClick={runLiveTest}>
        {tn('start_test')}
      </Button>
    </>
  );

  return (
    <DrawerPanel
      absolutePositioning
      title={
        <div className="synri-test-panel__title">
          {tn('title')} <HelpLink href={nextEdgeHelpUrl('run-live-test')} />
        </div>
      }
      // Larger width to accomodate the test by external id layout
      width={550}
      additionalHeightOffset={ENTITY_DRAWER_HEIGHT_OFFSET}
      className="synri-test-panel"
      onClose={close}
      visible={visible}
      footer={footer}
      footerClassName="synri-test-panel-footer">
      <Stack>
        <InfoBox type="info" message={tn('warning')} showIcon />
        {(pipelineValidationError || errors.length > 0) && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={tn('validation_errors_message')}>
            {tn('validation_errors_message')}
          </InlineMessage>
        )}
        {state.validationMessage && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={state.validationMessage}>
            {state.validationMessage}
          </InlineMessage>
        )}
        <Radio.Group onChange={onTestTypeChange} value={state.testType}>
          <Radio value={TestType.DATE}>{tn('by_date_time')}</Radio>
          <br />
          <Radio value={TestType.ID}>{tn('by_external_id')}</Radio>
          <br />
          <Radio disabled={!hasWebhookSource} value={TestType.PAYLOAD}>
            {tn('webhook_payload')}
          </Radio>
        </Radio.Group>

        {state.testType === TestType.DATE && createDateUi()}
        {state.testType === TestType.ID && createIdUi()}
        {state.testType === TestType.PAYLOAD && createPayloadUi()}
      </Stack>
    </DrawerPanel>
  );
};

export default TestRunLivePanel;
