//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon } from 'antd';

import EmptyGraphContent from 'components/EmptyGraphContent';
import { tNamespaced } from 'utils/i18nUtil';

import './TestNodeNotFound.less';

const tn = tNamespaced('TestNodeNotFound');

const TestNodeNotFound = () => {
  return (
    <EmptyGraphContent className="synri-test-node-not-found" icon={<Icon type="eye-invisible" />} actionDisabled>
      <span>{tn('node_not_available')}</span>
      <span>{tn('pipeline_changed')}</span>
    </EmptyGraphContent>
  );
};

export default TestNodeNotFound;
