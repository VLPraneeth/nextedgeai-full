//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Empty } from 'antd';

import { tNamespaced } from 'utils/i18nUtil';

import { useSetSelectedTest, useTestResultContext } from '../test-panels/test-hooks/TestResultPanel.hooks';
import TestResultCriteria from './TestResultCriteria';
import TestResultNodeContent from './TestResultNodeContent';
import TestResultStatusIndicator from './TestResultStatusIndicator';

import './TestResultCardLive.scss';

const tn = tNamespaced('TestResultContent');

const TestResultCardLive = () => {
  useSetSelectedTest();

  const { testRun, testRunIsProcessing, liveTestRunRecords } = useTestResultContext();

  if (!testRun) {
    return <Empty description={tn('no_records_processed')} image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  const run = testRun.resultDetails[0];

  return (
    <div className="synri-test-live-result-card">
      <div className="synri-test-result-card-live-header">
        <div className="synri-test-result-title">
          {tn('number_records_processed', { count: liveTestRunRecords?.length || 0 })}
        </div>
        <TestResultStatusIndicator status={testRun.status} />
      </div>
      <TestResultCriteria testRun={testRun} />
      <TestResultNodeContent
        run={run}
        status={testRun.status}
        errorMessage={testRun.errorMsg}
        testRunIsProcessing={testRunIsProcessing}
      />
    </div>
  );
};

export default TestResultCardLive;
