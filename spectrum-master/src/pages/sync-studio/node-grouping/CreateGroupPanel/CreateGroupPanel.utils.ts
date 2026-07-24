import ObjectID from 'bson-objectid';

import { EMPTY_ARRAY } from 'store/constants';
import { Edge, Group, Node } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';
import { getNodeSummary } from 'utils/Pipeline.utils';
import { generateUniqueNamesCallback } from 'utils/StringUtil';

import { CreateGroupForm } from './CreateGroupPanel';

export interface CreateGroupObjectProps {
  formData: CreateGroupForm;
  groups: Group[];
  group?: Group;
  selectedNodes?: Node[];
  nodes?: Node[];
  edges?: Edge[];
}

export const createGroupObject = ({ formData, group, groups, selectedNodes, nodes, edges }: CreateGroupObjectProps) => {
  const id = group?.id || ObjectID.generate();

  const childNodeSummary = group ? group.childNodeSummary : getNodeSummary(selectedNodes ?? EMPTY_ARRAY);

  const uniqueGroupName = generateUniqueNamesCallback(groups.map((group) => group.name))(formData.label);

  return {
    id,
    label: uniqueGroupName,
    name: uniqueGroupName,
    description: formData.description,
    color: formData.color,
    tags: formData.tags,
    apiName: id,
    shape: AppConstants.GRAPH_NODE_SHAPES.CUSTOM_GROUP,
    nodeType: AppConstants.NODE_TYPE.CUSTOM_GROUP,
    childNodeSummary,
  } as Group;
};
