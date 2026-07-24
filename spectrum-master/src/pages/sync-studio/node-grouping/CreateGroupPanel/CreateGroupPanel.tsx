import { Button } from 'antd';
import { map } from 'lodash';
import { ChangeEvent, useCallback, useEffect, useState } from 'react';

import ColorOptions from 'components/ColorOptions';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { ValidateStatuses } from 'components/inputs/types';
import { SelectionInfoWithAction } from 'components/SelectionInfoWithAction';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useClearSelectedNodes, useCreateGroup } from 'pages/sync-studio/pipeline/PipelineEditor.hooks';
import { Editor } from 'pages/sync-studio/pipeline/PipelineEditor.types';
import {
  selectCreateGroupPanelVisible,
  selectCurrentGraphEdges,
  selectCurrentGraphGroups,
  selectCurrentGraphNodes,
  selectSelectedNodes,
} from 'selectors/pipelineSelectors';
import { groupNodeUpdateAction, showCreateGroupPanel } from 'store/pipeline/actions';
import { GroupColor } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { toTitleCase } from 'utils/StringUtil';

import { createGroupObject } from './CreateGroupPanel.utils';

import './CreateGroupPanel.less';

export interface CreateGroupForm {
  label: string;
  description: string;
  color: GroupColor;
  tags: string[];
}

const formInit: CreateGroupForm = {
  label: '',
  description: '',
  color: 'GRAY',
  tags: [],
};

export interface CreateGroupFormValidation {
  labelValidationStatus?: ValidateStatuses;
  labelHelp?: string;
}

const validationInit: CreateGroupFormValidation = {};

export interface CreateGroupPanelProps {
  editor: Editor;
}

export const CreateGroupPanel = withI18n(({ editor }: CreateGroupPanelProps) => {
  const { tn, tc } = useI18nContext();
  const clearSelectedNodes = useClearSelectedNodes();
  const dispatch = useEnhancedDispatch();
  const createGroup = useCreateGroup();

  const { visible, selectedGroup } = useEnhancedSelector(selectCreateGroupPanelVisible);
  const selectedNodes = useEnhancedSelector(selectSelectedNodes);
  const currentGraphNodes = useEnhancedSelector(selectCurrentGraphNodes);
  const currentGraphEdges = useEnhancedSelector(selectCurrentGraphEdges);
  const currentGraphGroups = useEnhancedSelector(selectCurrentGraphGroups);

  const [formState, setFormState] = useState<CreateGroupForm>(formInit);
  const [formValidation, setFormValidation] = useState<CreateGroupFormValidation>(validationInit);

  /**
   * Handlers
   */
  const handleClose = useCallback(() => {
    setFormState(formInit);
    setFormValidation(validationInit);
    dispatch(showCreateGroupPanel({ visible: false, selectedGroup: undefined }));
  }, [dispatch]);

  const handleClearSelection = useCallback(() => {
    clearSelectedNodes();
  }, [clearSelectedNodes]);

  const handleLabelChange = (e: ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value.trimStart();
    const isValid = value !== '';

    setFormState((state) => ({
      ...state,
      label: value,
    }));

    setFormValidation((state) => ({
      ...state,
      labelValidationStatus: isValid ? ValidateStatuses.BLANK : ValidateStatuses.ERROR,
      labelHelp: isValid ? '' : tn('group_label_validation_error'),
    }));
  };

  const handleDescriptionChange = (e: ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value.trimStart();

    setFormState((state) => ({
      ...state,
      description: value,
    }));
  };

  const handleColorChange = (color: string) => {
    setFormState((state) => ({
      ...state,
      color: color as GroupColor,
    }));
  };

  const handleTagsChange = (tags: string[]) => {
    const values = tags.filter((tag) => tag.trimStart() !== '');

    setFormState((state) => ({
      ...state,
      tags: values,
    }));
  };

  const handleCreateGroup = () => {
    const newGroup = createGroupObject({
      formData: formState,
      selectedNodes,
      nodes: currentGraphNodes,
      edges: currentGraphEdges,
      groups: currentGraphGroups,
    });
    createGroup(editor, newGroup);

    handleClose();
  };

  const handleUpdateGroup = () => {
    if (selectedGroup) {
      const groupUpdate = createGroupObject({ formData: formState, group: selectedGroup, groups: currentGraphGroups });

      dispatch(groupNodeUpdateAction({ groupId: selectedGroup?.id, action: 'update', data: groupUpdate }));

      handleClose();
    }
  };

  /**
   * Effects
   */
  // Get group data for configuration updates.
  useEffect(() => {
    if (selectedGroup) {
      setFormState({
        label: selectedGroup.label,
        description: selectedGroup.description,
        color: selectedGroup.color,
        tags: selectedGroup.tags,
      });
    }
  }, [selectedGroup]);

  // Close panel if multiple nodes are not selected.
  useEffect(() => {
    if (selectedNodes.length < 2) {
      handleClose();
    }
  }, [handleClose, selectedNodes]);

  // Set redux back to default state on component unmount.
  useEffect(() => {
    return () => {
      dispatch(showCreateGroupPanel({ visible: false, selectedGroup: undefined }));
    };
  }, [dispatch]);

  /**
   * Render
   */
  const footer = (
    <>
      <Button onClick={handleClose}>{tc('cancel')}</Button>
      <Button
        type="primary"
        onClick={selectedGroup ? handleUpdateGroup : handleCreateGroup}
        disabled={!formState.label}>
        {selectedGroup ? tc('save') : tn('create_group')}
      </Button>
    </>
  );

  return (
    <DrawerPanel
      title={selectedGroup ? selectedGroup.label : tn('new_group_title')}
      visible={visible}
      footer={footer}
      onClose={handleClose}
      className="create-group-panel">
      {!selectedGroup && (
        <SelectionInfoWithAction
          selectionText={tn('selected_nodes', { nodeCount: selectedNodes.length })}
          action={handleClearSelection}
          actionText={tn('clear_selection')}
          className="create-group-panel__selection-action"
        />
      )}
      <InputWithLabel
        label={tn('group_label')}
        datatype={AppConstants.INPUT_TYPE.STRING}
        required
        value={formState.label}
        validateStatus={formValidation.labelValidationStatus ? 'error' : undefined}
        help={formValidation.labelHelp}
        onChange={handleLabelChange}
        className="create-group-panel__group-label"
      />
      <InputWithLabel
        label={tn('group_description')}
        datatype={AppConstants.INPUT_TYPE.TEXTAREA}
        name="groupDescription"
        value={formState.description}
        onChange={handleDescriptionChange}
        className="create-group-panel__group-description"
      />
      <InputWithLabel
        label={tn('group_color')}
        name="groupColor"
        input={
          <ColorOptions
            colors={map(AppConstants.GROUP_COLORS, (value, key) => ({
              id: key,
              label: toTitleCase(key),
              hex: value,
            }))}
            onSelectionChange={handleColorChange}
            selectedColor={AppConstants.GROUP_COLORS[formState.color]}
          />
        }
      />
      <InputWithLabel
        label={tn('group_tags')}
        datatype={AppConstants.INPUT_TYPE.TAG}
        name="groupTags"
        value={formState.tags}
        onChange={handleTagsChange}
        className="create-group-panel__group-tags"
      />
    </DrawerPanel>
  );
}, 'CreateGroupPanel');
