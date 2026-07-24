import { Icon } from 'antd';
import cx from 'classnames';
import { capitalize } from 'lodash';
import * as React from 'react';

import { doesTestStatusHaveResult, TestStatus, TEST_STATUS } from '../Test.util';

import './TestResultStatusIndicator.less';

const testStatusIconMap: Partial<Record<TestStatus, React.ReactElement | null>> = {
  [TEST_STATUS.SUCCESS]: <Icon type="check-circle" theme="filled" />,
  [TEST_STATUS.FAILED]: <Icon type="exclamation-circle" theme="filled" />,
  [TEST_STATUS.ERROR]: <Icon type="exclamation-circle" theme="filled" />,
  [TEST_STATUS.COMPLETED]: null,
};

interface Props {
  status?: TestStatus;
}

const TestResultStatusIndicator = ({ status }: Props) => {
  const lcStatus = status?.toLowerCase();
  if (!lcStatus || !doesTestStatusHaveResult(lcStatus)) {
    return null;
  }

  return (
    <div className={cx('synri-test-result-status', `synri-test-result-status-${lcStatus}`)}>
      {testStatusIconMap[lcStatus]}
      <div>{capitalize(lcStatus)}</div>
    </div>
  );
};

export default TestResultStatusIndicator;
