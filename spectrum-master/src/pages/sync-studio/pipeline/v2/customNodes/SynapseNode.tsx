import { NodeProps } from '@xyflow/react';
import { memo } from 'react';

import { ReactComponent as SchedueIcon } from 'assets/icons/schedule-new.svg';
import { ReactComponent as RefreshIcon } from 'assets/images/refresh.svg';
import { cronToSummaryString } from 'components/inputs/schedule/cronUtils';
import { Stack } from 'components/layout';
import { tNamespaced } from 'utils/i18nUtil';

import NodeActions from '../components/NodeActions';
import { ReactFlowNodeV2 } from '../types/ReactFlow.types';
import BaseCustomNode from './BaseCustomNode';
import { NodeContentBody } from './customNodeComponents/NodeContent';

const tn = tNamespaced('PipelineV2');

const SynapseNode = memo((props: NodeProps<ReactFlowNodeV2>) => {
  const { data } = props;
  const node = data.fullNode;

  const fromOrTo = ['ENTITY_SOURCE', 'ATTRIBUTE_SOURCE'].includes(node.nodeType) ? tn('sync_from') : tn('sync_to');

  return (
    <BaseCustomNode
      nodeProps={props}
      color="blue"
      label={node.subLabel}
      nodeActions={<NodeActions nodeId={data.fullNode.id} edit palette trash />}
      content={
        <Stack>
          <NodeContentBody
            header={fromOrTo}
            label={node.apiName}
            icon={<RefreshIcon className="pipeline-node-content__row--icon" />}
          />
          {!!node.configuration.schedule && (
            <NodeContentBody
              header={tn('schedule')}
              label={cronToSummaryString(node.configuration.schedule)}
              icon={<SchedueIcon className="pipeline-node-content__row--icon" />}
            />
          )}
        </Stack>
      }
    />
  );
});

export default SynapseNode;
