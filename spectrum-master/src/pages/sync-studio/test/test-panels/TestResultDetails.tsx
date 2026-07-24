//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Empty } from 'antd';
import cx from 'classnames';
import { find } from 'lodash';
import { useMemo } from 'react';

import ChangeAwareLink from 'components/ChangeAwareLink';
import DrawerPanel from 'components/DrawerPanel';
import { HStack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import SelectInput from 'components/SelectInput';
import TabPanelSpin from 'components/TabPanelSpin';
import { Text } from 'components/typography';
import { useEnhancedDispatch, useEnhancedSelector, useEnhancedSelector as useSelector } from 'hooks/redux';
import { useSelectedNodes } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { selectConnectorsForCurrentEntityPipeline } from 'selectors/entityPipelineSelectors';
import { setLiveTestRunRecordId } from 'store/test/actions';
import {
  selectFieldPiepelineTestNode,
  selectFieldPipelineTestRunTest,
  selectGetFieldTestRunsStatus,
  selectGetFieldTestRunStatus,
} from 'store/test/selectors';
import { PipelineContextTypes } from 'store/test/types';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { truncateMiddle } from 'utils/StringUtil';
import { makeUrl } from 'utils/UrlUtil';

import TestResultDetailsTable from '../test-components/TestResultDetailsTable';
import TestResultStatusIndicator from '../test-components/TestResultStatusIndicator';
import { doesTestStatusHaveResult, TEST_STATUS, TestStatus } from '../Test.util';
import { useTestResultContext } from './test-hooks/TestResultPanel.hooks';

import './test-styles/TestResultDetails.less';

const tn = tNamespaced('TestResultDetails');
const { FETCH_STATUS } = AppConstants;

export interface TestResultDetailsProps {
  pipelineId: string;
  pipelineContext: PipelineContextTypes;
  className?: string;
}

const PENDING_RESULT_STATUS: TestStatus[] = [TEST_STATUS.QUEUED, TEST_STATUS.PENDING, TEST_STATUS.RUNNING];

const TestResultDetails = ({ pipelineId, pipelineContext, className }: TestResultDetailsProps) => {
  const dispatch = useEnhancedDispatch();
  const { selectedNodeIds } = useSelectedNodes();
  const nodeId = selectedNodeIds.length === 1 ? selectedNodeIds[0] : undefined;

  const selectedTestRun = useSelector(selectFieldPipelineTestRunTest);
  const selectedNodeId = useSelector((state) => selectFieldPiepelineTestNode(state, nodeId));
  const getFieldTestRunStatus = useSelector(selectGetFieldTestRunStatus);
  const getFieldTestRunsStatus = useSelector(selectGetFieldTestRunsStatus);
  const pipelineConnectors = useEnhancedSelector(selectConnectorsForCurrentEntityPipeline);

  const { visible, isLive, liveTestRunRecords, liveTestRunRecordId } = useTestResultContext();

  const updateLiveTestRecordId = (newId: string) => dispatch(setLiveTestRunRecordId(newId));

  const recordLink = useMemo(() => {
    if (liveTestRunRecordId) {
      const syncariRecord = liveTestRunRecords?.find((record) => record.id === liveTestRunRecordId);
      return (
        syncariRecord?.syncariRecordId &&
        makeUrl(RouteConstants.DATA_STUDIO_RECORD_FIELDS, {
          entityId: pipelineId,
          recordId: syncariRecord.syncariRecordId,
        })
      );
    }
  }, [liveTestRunRecordId, liveTestRunRecords, pipelineId]);

  const getTestResultStatusOrDropdown = () => {
    if (isLive && liveTestRunRecords) {
      const selectOptions = liveTestRunRecords.map((record) => {
        const syncariId = record.syncariRecordId ? `(${truncateMiddle(record.syncariRecordId, 20)})` : '';
        const externalId = truncateMiddle(record.externalRecordId, 20);
        const pipelineConnector = find(pipelineConnectors, { sourceEntityId: record.entityId });
        const connectorName = pipelineConnector?.label
          ? `${pipelineConnector.label}: `
          : record.connectorName
          ? `${record.connectorName}: `
          : '';
        const label = `${connectorName}${externalId} ${syncariId}`;
        const value = record.id;
        const status = record.status;

        const labelComponent = (
          <HStack>
            <span>{label}</span>
            {/* Eventually the backend will support recording errors at the record level for live tests. */}
            {status === TEST_STATUS.ERROR && <TestResultStatusIndicator status={status} />}
          </HStack>
        );

        return { value, label, labelComponent };
      });

      return (
        <HStack spacing="sm">
          <SelectInput
            onChange={(id) => updateLiveTestRecordId(id)}
            value={liveTestRunRecordId}
            options={selectOptions}
            placeholder="select a record"
            style={{ minWidth: 530 }}
            showSearch
            defaultActiveFirstOption={false}
            showArrow
            filterOption={(searchText, option) => {
              return !!option?.props?.title?.toLowerCase().includes(searchText.toLowerCase());
            }}
            notFoundContent={<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tn('no_filter_results')} />}
          />
          {recordLink && <ChangeAwareLink to={recordLink}>{tn('goto_record')}</ChangeAwareLink>}
        </HStack>
      );
    }

    return (
      <HStack spacing="sm">
        <TestResultStatusIndicator status={selectedNodeId?.status} />
        {selectedNodeId?.errorMsg && <Text>{selectedNodeId?.errorMsg}</Text>}
      </HStack>
    );
  };

  return (
    <DrawerPanel
      title={
        selectedTestRun?.displayName +
        (selectedNodeId?.displayName ? tn('title_half', { displayName: selectedNodeId?.displayName }) : '')
      }
      className={cx('synri-test-result-details', className)}
      visible={selectedTestRun && visible}
      width={window.innerWidth - 420}
      additionalHeightOffset={pipelineContext === 'entity' ? ENTITY_DRAWER_HEIGHT_OFFSET : FIELD_DRAWER_HEIGHT_OFFSET}
      height={350}
      closable={false}
      placement="bottom">
      <div className="synri-test-result-container">
        <TabPanelSpin
          spinning={getFieldTestRunStatus === FETCH_STATUS.LOADING || getFieldTestRunsStatus === FETCH_STATUS.LOADING}
          tip={tn('loading_test_result')}>
          {selectedTestRun ? (
            <>
              <div className="synri-test-result-summary">
                <div className="synri-test-result-summary-overview">{getTestResultStatusOrDropdown()}</div>
              </div>
              {doesTestStatusHaveResult(selectedTestRun?.status) ? (
                <div className="synri-test-result-input-output">
                  <div className="synri-test-result-input">
                    {selectedNodeId && <TestResultDetailsTable selectedTestNodeResult={selectedNodeId} />}
                  </div>
                </div>
              ) : (
                <>
                  <CenterLayout>
                    {PENDING_RESULT_STATUS.includes(selectedTestRun?.status) && (
                      <div>{tn('not_yet_available', { status: selectedTestRun?.status })}</div>
                    )}
                    {selectedTestRun?.status === TEST_STATUS.ABORTED && <div>{tn('test_aborted')}</div>}
                    {selectedTestRun?.status === TEST_STATUS.ERROR && <div>{selectedTestRun.errorMsg}</div>}
                  </CenterLayout>
                </>
              )}
            </>
          ) : (
            <></>
          )}
        </TabPanelSpin>
      </div>
    </DrawerPanel>
  );
};

export default TestResultDetails;
