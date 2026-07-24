import { Icon, Tooltip } from 'antd';
import { useCallback } from 'react';

import Button from 'components/Button';
import { ColorOption } from 'components/ColorOptions';
import Fieldset from 'components/Fieldset';
import { HStack, Stack } from 'components/layout';
import PropertyPanelTitle from 'components/PropertyPanelTitle';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { Text } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { showConfirmUngroupModal, showCreateGroupPanel, showDeleteMultipleNodesModal } from 'store/pipeline/actions';
import { Group } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { tCommon as tc, tNamespaced } from 'utils/i18nUtil';
import { toTitleCase } from 'utils/StringUtil';

import NodePanelFieldGroup from './NodePanelFieldGroup';
import { useUpdateSelectedNodeIdsQueryParam } from './pipeline/PipelineEditor.hooks';

import './GroupNodePanel.less';

const tn = tNamespaced('GroupNodePanel');

export interface GroupNodePanelProps {
  selectedNode: Group;
  isEditable?: boolean;
}

const GroupNodePanel = ({ selectedNode, isEditable }: GroupNodePanelProps) => {
  const dispatch = useEnhancedDispatch();
  const setSelectedNodeIds = useUpdateSelectedNodeIdsQueryParam();

  const onClose = () => {
    setSelectedNodeIds();
  };

  const handleConfigureGroup = useCallback(() => {
    dispatch(showCreateGroupPanel({ visible: true, selectedGroup: selectedNode }));
  }, [dispatch, selectedNode]);

  const handleUngroup = useCallback(() => {
    dispatch(showConfirmUngroupModal({ visible: true, groupId: selectedNode.id }));
  }, [dispatch, selectedNode]);

  const deleteGroup = useCallback(() => {
    dispatch(showDeleteMultipleNodesModal(true));
  }, [dispatch]);

  const groupColorHex = AppConstants.GROUP_COLORS[selectedNode.color];
  const groupColorLabel = toTitleCase(selectedNode.color);

  return (
    <div className="group-node-panel">
      <PropertyPanelTitle title={selectedNode.label} onClose={onClose} />
      <ScrollableArea>
        <div className="group-node-panel__actions-container">
          <Button
            disabled={!isEditable}
            onClick={handleConfigureGroup}
            size="large"
            className="group-node-panel__action-button">
            <span>{tn('configure_group')}</span>
          </Button>
          <Button
            disabled={!isEditable}
            onClick={handleUngroup}
            size="large"
            className="group-node-panel__action-button">
            <span>{tn('ungroup')}</span>
            <Tooltip title={tn('ungroup_help')}>
              <div className="group-node-panel__icon-container">
                <Icon theme="filled" type="question-circle" />
              </div>
            </Tooltip>
          </Button>
          <Button disabled={!isEditable} onClick={deleteGroup} size="large" className="group-node-panel__action-button">
            <span>{tn('delete_group_and_contents')}</span>
            <Tooltip title={tn('delete_group_and_contents_help')}>
              <div className="group-node-panel__icon-container">
                <Icon theme="filled" type="question-circle" />
              </div>
            </Tooltip>
          </Button>
        </div>
        <Fieldset title={tn('group_configuration')}>
          <Stack spacing="xl">
            <NodePanelFieldGroup title={tn('label')}>{selectedNode.label}</NodePanelFieldGroup>
            <NodePanelFieldGroup title={tn('description')}>{selectedNode.description}</NodePanelFieldGroup>
            <NodePanelFieldGroup title={tn('group_color')}>
              <HStack spacing="xxxs" align="center">
                <ColorOption id={selectedNode.color} label={groupColorLabel} hex={groupColorHex} />
                <Text color="gray-1000">{groupColorLabel}</Text>
              </HStack>
            </NodePanelFieldGroup>
            <NodePanelFieldGroup title={tc('tags')}>{selectedNode.tags.join(', ')}</NodePanelFieldGroup>
          </Stack>
        </Fieldset>
      </ScrollableArea>
    </div>
  );
};

export default GroupNodePanel;
