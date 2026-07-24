//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Collapse, Empty, Icon } from 'antd';

import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { selectTestRunTestId } from 'store/test/actions';
import { selectFieldPipelineTestRunTest, selectTestRun } from 'store/test/selectors';
import { tNamespaced } from 'utils/i18nUtil';

import { useSetSelectedTest } from '../test-panels/test-hooks/TestResultPanel.hooks';
import TestResultNodeContent from './TestResultNodeContent';
import TestResultStatusIndicator from './TestResultStatusIndicator';

import './TestResultCollapse.less';

const { Panel } = Collapse;
const tn = tNamespaced('TestResultContent');

const TestResultCollapse = () => {
  const dispatch = useDispatch();
  const testRun = useSelector(selectTestRun);
  const selectedTestRun = useSelector(selectFieldPipelineTestRunTest);

  useSetSelectedTest();

  if (testRun?.resultDetails?.length === 0) {
    return <Empty description={tn('no_records_processed')} image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  if (!testRun?.resultDetails || !selectedTestRun) {
    return null;
  }

  return (
    <Collapse
      accordion
      bordered={false}
      onChange={(key) => {
        if (typeof key === 'string') {
          dispatch(selectTestRunTestId(key));
        }
      }}
      defaultActiveKey={[selectedTestRun?.id]}
      className="synri-test-result-collapse"
      expandIcon={(panelProps) => {
        return <Icon type="caret-right" theme="filled" rotate={panelProps.isActive ? 90 : undefined} />;
      }}>
      {testRun.resultDetails.map((run) => (
        <Panel
          key={run.id}
          header={
            <div className="synri-test-result-header">
              <div className="synri-test-result-title">{run.displayName}</div>
              <TestResultStatusIndicator status={run.status} />
            </div>
          }>
          <TestResultNodeContent run={run} status={testRun.status} errorMessage={run.errorMsg} />
        </Panel>
      ))}
    </Collapse>
  );
};

export default TestResultCollapse;
