//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';
import cx from 'classnames';
import { capitalize } from 'lodash';
import { useEffect, useState } from 'react';

import CenterLayout from 'components/layout/CenterLayout';
import { useSelectedNodes, useUpdateSelectedNodeIdsQueryParam } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { TestResultDetail } from 'store/test/types';
import { tNamespaced } from 'utils/i18nUtil';

import { useTestResultContext } from '../test-panels/test-hooks/TestResultPanel.hooks';
import { OVERVIEW_ID, TEST_STATUS } from '../Test.util';

import './TestResultNodeContent.scss';

const tn = tNamespaced('TestResultContent');

interface Props {
  run?: TestResultDetail;
  errorMessage?: string | null;
  status: string;
  testRunIsProcessing?: boolean;
}

const TestResultNodeContent = ({ run, errorMessage, status, testRunIsProcessing = false }: Props) => {
  const { selectedNodeIds } = useSelectedNodes();
  const selectedNodeId = selectedNodeIds.length === 1 ? selectedNodeIds[0] : undefined;
  const updateSelectedNodeIdsQueryParam = useUpdateSelectedNodeIdsQueryParam();

  // Used for styling the node selection menu in the test result panel.
  const [visibleSelectedNodeId, setVisibleSelectedNodeId] = useState<string | undefined>();

  const { isLive } = useTestResultContext();

  useEffect(() => {
    if (selectedNodeId) {
      // Update the selected node in the panel depending on what node we are
      // navigated to.
      setVisibleSelectedNodeId(selectedNodeId);
    } else if (isLive && run?.nodes) {
      setVisibleSelectedNodeId(run.nodes?.[0]?.nodeId);
    } else if (!isLive) {
      // If we are in the Simulated Test Result panel & we have all nodes unselected,
      // then default to selecting the "Overview" result.
      setVisibleSelectedNodeId(OVERVIEW_ID);
    }
  }, [isLive, run?.nodes, selectedNodeId]);

  const handleNodeSelection = (nodeId: string) => {
    if (nodeId === OVERVIEW_ID) {
      updateSelectedNodeIdsQueryParam(null);
    } else {
      updateSelectedNodeIdsQueryParam(null, undefined, nodeId);
    }
  };

  const getContent = () => {
    if (errorMessage) {
      return (
        <CenterLayout>
          <div className="synri-test-node-status-failed">{errorMessage}</div>
        </CenterLayout>
      );
    }

    if (testRunIsProcessing) {
      return (
        <CenterLayout>
          <div>{tn('test_is_processing')}</div>
        </CenterLayout>
      );
    }

    if (!run?.nodes?.length) {
      return (
        <CenterLayout>
          <div>
            {tn(
              status === TEST_STATUS.ERROR ? 'unexpected_error' : isLive ? 'no_records_processed' : 'not_yet_available'
            )}
          </div>
        </CenterLayout>
      );
    }

    return run?.nodes.map((node) => (
      <div
        key={node.nodeId}
        className={cx('synri-test-node-result-container', {
          'synri-test-node-result-active': visibleSelectedNodeId === node.nodeId,
        })}
        onClick={() => handleNodeSelection(node.nodeId)}>
        <Tooltip mouseEnterDelay={2} title={node.displayName}>
          <div className="synri-test-node-name">
            {node.nodeId === OVERVIEW_ID ? tn('overview') : tn('node_name', { name: node.displayName })}
          </div>
        </Tooltip>
        {node.nodeId !== OVERVIEW_ID && (
          <Tooltip title={node.errorMsg}>
            <div className={cx('synri-test-node-status', `synri-test-node-status-${node.status.toLowerCase()}`)}>
              {capitalize(node.status)}
            </div>
          </Tooltip>
        )}
      </div>
    ));
  };

  return (
    <div
      className={cx('synri-test-nodes-result-container', {
        empty: !run?.nodes?.length,
      })}>
      {getContent()}
    </div>
  );
};

export default TestResultNodeContent;
