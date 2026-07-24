import { NodeProps } from '@xyflow/react';
import cx from 'classnames';
import { memo, ReactNode } from 'react';

import { ReactComponent as ActionIcon } from 'assets/icons/pipeline-action.svg';
import { ReactComponent as PipelineIcon } from 'assets/icons/pipeline.svg';
import { HStack, Stack } from 'components/layout';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { humanize } from 'utils/StringUtil';

import NodeActions from '../components/NodeActions';
import { ExtraDataCoreEntityNode, ReactFlowNodeV2 } from '../types/ReactFlow.types';
import BaseCustomNode from './BaseCustomNode';
import { NodeContentBody } from './customNodeComponents/NodeContent';

import './CustomNode.scss';

const tn = tNamespaced('PipelineV2');

interface FieldCountRowProps {
  label: string;
  count: number;
  icon: ReactNode;
  className?: string;
}

const FieldCountRow = ({ label, count, icon, className }: FieldCountRowProps) => {
  return (
    <HStack className={cx('core-field-count-row', className)} justify="space-between">
      <HStack spacing="xs">
        <div className="core-field-count-row__icon-container">{icon}</div>
        <span className="core-field-count-row__label">{label}</span>
      </HStack>
      <span className="core-field-count-row__label">{count}</span>
    </HStack>
  );
};

interface FieldCountDotProps {
  color: string;
}

const FieldCountDot = ({ color }: FieldCountDotProps) => {
  return <div className={cx('field-count-dot', color)} />;
};

const CoreNode = memo((props: NodeProps<ReactFlowNodeV2>) => {
  const { data } = props;

  if (data.extraData.nodeType === AppConstants.NODE_TYPE.CORE_ATTRIBUTE) {
    return (
      <BaseCustomNode
        nodeProps={props}
        color="solid-blue"
        nodeActions={<NodeActions nodeId={data.fullNode.id} fields mergeStudio tags />}
        content={
          <>
            <Stack>
              <Stack spacing="xxxs">
                <NodeContentBody header={'Api Name'} label={data.fullNode.apiName} />
                <NodeContentBody header={'Data Type'} label={humanize((data.extraData as any).dataType)} />
                <NodeContentBody
                  header={'Is Multivalued?'}
                  label={(data.extraData as any).isMultivalued ? 'True' : 'False'}
                />
              </Stack>
            </Stack>
          </>
        }
      />
    );
  }

  const fieldsSummary = (data.extraData as ExtraDataCoreEntityNode).fieldsSummary || {};

  return (
    <BaseCustomNode
      nodeProps={props}
      color="solid-blue"
      nodeActions={<NodeActions nodeId={data.fullNode.id} fields mergeStudio tags />}
      content={
        <>
          <Stack>
            <Stack spacing="xxxs">
              <FieldCountRow
                label={tn('total_fields')}
                count={fieldsSummary.fieldsCount}
                icon={<PipelineIcon className="pipeline-node-content__row--icon" />}
                className="header"
              />
              <FieldCountRow label={tn('mapped')} count={fieldsSummary.mapped} icon={<FieldCountDot color="blue" />} />
              <FieldCountRow label={tn('draft')} count={fieldsSummary.draft} icon={<FieldCountDot color="orange" />} />
              <FieldCountRow label={tn('ready')} count={fieldsSummary.ready} icon={<FieldCountDot color="green" />} />
            </Stack>
          </Stack>
          <FieldCountRow
            label={tn('total_action_in_use')}
            // TODO: Wire this count up to the real value. Conditionally show this
            count={2}
            icon={<ActionIcon className="pipeline-node-content__row--icon" />}
            className="header margin-top"
          />
        </>
      }
    />
  );
});

export default CoreNode;
