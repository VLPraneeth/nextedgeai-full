//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Alert, Button, Tooltip } from 'antd';
import cx from 'classnames';
import * as React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { ReactComponent as InfoOutline } from 'assets/icons/info-outline.svg';
import DrawerPanel from 'components/DrawerPanel';
import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { NODE_GRAPH_TEST } from 'components/icons/Icons';
import InlineSvg from 'components/icons/InlineSvg';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import Select, { Option, OptionProps } from 'components/inputs/Select';
import { HStack, Spacer } from 'components/layout';
import TabPanelSpin from 'components/TabPanelSpin';
import { useUtcTimeInUsersTimezone } from 'hooks/moment';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import usePreviousValue from 'hooks/usePreviousValue';
import { useSelectedEntityIsRunningTest } from 'store/test';
import { getFieldTestRun, setTestPanelView } from 'store/test/actions';
import { selectTestRun, selectUpdatedTestRunId } from 'store/test/selectors';
import { PipelineContextTypes, RunLiveTestPayload, TestPanelView } from 'store/test/types';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import AppConstants from 'utils/AppConstants';
import { format, PARSABLE_DATE_TIME_FORMAT } from 'utils/DateUtil';
import { SHORT_DATE_TIME_REGEXP } from 'utils/DateUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';

import TestResultCardLive from '../test-components/TestResultCardLive';
import TestResultCollapse from '../test-components/TestResultCollapse';
import {
  useFetchLatestTestWhenCompleted,
  useFetchTestResults,
  useTestResultContext,
} from './test-hooks/TestResultPanel.hooks';
import { useRunLiveTest } from './test-hooks/TestRunLive.hooks';
import './test-styles/TestResultPanel.scss';

const tn = tNamespaced('TestResultPanel');
const te = tNamespaced('PipelineEditor');
const { FETCH_STATUS } = AppConstants;

export interface TestResultPanelProps {
  pipelineId: string;
  pipelineContext: PipelineContextTypes;
  onSaveChanges: () => void;
  validate: () => void;
  className?: string;
  errorMessage?: string;
}

const TestResultPanel = ({
  pipelineId,
  pipelineContext,
  onSaveChanges,
  validate,
  className,
  errorMessage,
}: TestResultPanelProps) => {
  useFetchLatestTestWhenCompleted();
  const formatUtcToConfiguredTime = useUtcTimeInUsersTimezone();

  const dispatch = useDispatch();
  const testIsRunning = useSelectedEntityIsRunningTest();
  const testPipeline = useRunLiveTest();

  const { isLive, testRuns, testRunsStatus, testRunStatus, visible, testRun } = useTestResultContext();
  const { getTestRun: onTestRunChange } = useFetchTestResults(pipelineId, pipelineContext);

  const [runDefaultValue, setRunDefaultValue] = useState<string>();
  const updatedTestRunId = useSelector(selectUpdatedTestRunId);
  const previousUpdatedTestRunId = usePreviousValue(updatedTestRunId);
  const selectedTestRun = useSelector(selectTestRun);

  useEffect(() => {
    if (testRuns?.length > 0) {
      setRunDefaultValue(testRuns?.[testRuns.length - 1]?.id);
    }
  }, [testRuns]);

  useEffect(() => {
    if (updatedTestRunId && updatedTestRunId !== previousUpdatedTestRunId && updatedTestRunId === selectedTestRun?.id) {
      // There is an update on the currently selected test run, refresh it
      dispatch(getFieldTestRun({ pipelineContext, fieldPipelineId: pipelineId, runId: updatedTestRunId }));
    }
  }, [updatedTestRunId, previousUpdatedTestRunId, selectedTestRun, dispatch, pipelineContext, pipelineId]);

  const close = useCallback(() => dispatch(setTestPanelView(TestPanelView.CLOSED)), [dispatch]);

  const gotoTestPanel = useCallback(() => {
    close();
    dispatch(setTestPanelView(TestPanelView.SIMULATED_RUN));
  }, [dispatch, close]);

  const openLiveTestRunModal = useCallback(() => {
    dispatch(setTestPanelView(TestPanelView.LIVE_RUN));
  }, [dispatch]);

  const filterOption = (input: string, option: React.ReactElement<OptionProps>) => {
    return option?.props?.title ? option.props.title.toString().toLowerCase().indexOf(input.toLowerCase()) >= 0 : false;
  };

  const showTestRuns = useMemo(() => {
    if (!testRuns?.length) {
      if (isLive) {
        return (
          <EmptyGraphPanel
            onActionClick={openLiveTestRunModal}
            panelIcon={<InlineSvg size="2x" src={NODE_GRAPH_TEST} title={te('test')} />}
            actionText={te('run_live_test')}>
            <span>{tn('no_live_test_runs')}</span>
          </EmptyGraphPanel>
        );
      } else {
        return (
          <EmptyGraphPanel
            onActionClick={gotoTestPanel}
            panelIcon={<InlineSvg size="2x" src={NODE_GRAPH_TEST} title={te('test')} />}
            actionText={te('run_simulated_test')}>
            <span>{tn('no_simulated_test_runs')}</span>
          </EmptyGraphPanel>
        );
      }
    }

    if (runDefaultValue) {
      return (
        <>
          <Select
            defaultValue={runDefaultValue}
            filterOption={filterOption}
            onChange={onTestRunChange}
            value={selectedTestRun?.id}>
            {testRuns.map((run) => {
              const countString = isLive
                ? tn('record_count', { count: run?.recordsProcessed })
                : tn('test_count', { count: run?.testNames.length });

              // If run name is a date generated by backend (live tests), handle conversion
              // from UTC to user's configured timezone
              let runName = run.runName;
              const runNameIsDate = runName.match(SHORT_DATE_TIME_REGEXP);
              if (runNameIsDate) {
                runName = formatUtcToConfiguredTime(runName);
              }

              // If test name is a date generated by backend (live tests), handle conversion
              // from UTC to user's configured timezone
              let testName = run?.testNames?.join(', ');
              const testNameIsDate = run.testNames.length === 1 && run.testNames[0].match(SHORT_DATE_TIME_REGEXP);
              if (testNameIsDate) {
                testName = formatUtcToConfiguredTime(run.testNames[0]);
              }

              // It testName and runName are the same, omit testName to avoid duplicate tooltip/title
              if (testName === runName) {
                testName = '';
              }

              return (
                <Option key={run.id} value={run.id} title={runName}>
                  <Tooltip title={testName}>
                    <div className="synri-test-result-panel__option-text">
                      <span>{runName}</span>
                      <span>{countString}</span>
                    </div>
                  </Tooltip>
                </Option>
              );
            })}
          </Select>
          <div className="synri-test-result-panel__test-container">
            {isLive ? <TestResultCardLive /> : <TestResultCollapse />}
          </div>
        </>
      );
    }

    return null;
  }, [
    testRuns,
    runDefaultValue,
    isLive,
    openLiveTestRunModal,
    gotoTestPanel,
    onTestRunChange,
    selectedTestRun?.id,
    formatUtcToConfiguredTime,
  ]);

  const runAgain = async () => {
    validate();

    await onSaveChanges();

    if (!testRun) {
      return;
    }

    const criteria: RunLiveTestPayload = testRun.startTime
      ? {
          // convert UTC date strings to backend-parsable date format
          start: format(testRun.startTime, PARSABLE_DATE_TIME_FORMAT),
          end: format(testRun.endTime, PARSABLE_DATE_TIME_FORMAT),
          limit: testRun.limit,
        }
      : testRun.webhook
      ? {
          webhook: testRun.webhook,
        }
      : {
          recordIds: testRun.recordIds,
          limit: null,
        };

    testPipeline(criteria);
  };

  return (
    <DrawerPanel
      absolutePositioning
      additionalHeightOffset={pipelineContext === 'entity' ? ENTITY_DRAWER_HEIGHT_OFFSET : FIELD_DRAWER_HEIGHT_OFFSET}
      className={cx('synri-test-result-panel', className)}
      onClose={close}
      title={isLive ? tn('live_title') : tn('simulated_title')}
      footer={
        <HStack>
          <Spacer flex />
          <Button onClick={close}>{tc('close')}</Button>
          <Tooltip title={testIsRunning ? tn('test_is_running') : ''}>
            <Button type="primary" onClick={runAgain} disabled={testIsRunning}>
              {tn('run_again')}
            </Button>
          </Tooltip>
        </HStack>
      }
      visible={visible}>
      {errorMessage && (
        <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
          {errorMessage}
        </InlineMessage>
      )}

      {isLive && testIsRunning && (
        <>
          <Alert
            type="info"
            className="test-running-alert"
            message={tn('results_shown_after_test')}
            showIcon
            icon={
              <>
                <InfoOutline width={32} height={32} />
              </>
            }
          />
          <Spacer y="md" />
        </>
      )}
      <TabPanelSpin
        spinning={testRunStatus === FETCH_STATUS.LOADING || testRunsStatus === FETCH_STATUS.LOADING}
        tip={tn('loading_test_results')}>
        {showTestRuns}
      </TabPanelSpin>
    </DrawerPanel>
  );
};

export default TestResultPanel;
