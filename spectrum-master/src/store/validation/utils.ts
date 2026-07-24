import { find, keyBy } from 'lodash';

import { Entity, EntityField } from 'store/entity/types';
import { PipelineSyncError } from 'store/pipeline-error/types';
import { EditorGroup } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';

import { ValidationErrorLevel, ValidationMode, ValidationResult, ValidationResultType } from './types';

export const countValidationResults = (results: ValidationResult[]) => {
  return {
    numberOfErrors: results.filter((result: ValidationResult) => result.type === ValidationResultType.ERROR).length,
    numberOfWarnings: results.filter((result: ValidationResult) => result.type === ValidationResultType.WARNING).length,
  };
};

export const countValidationResultsByFieldId = (results: (ValidationResult | PipelineSyncError)[], fieldId: string) => {
  return results.filter((result) => result.targetId === fieldId).length;
};

export const getValidationResultsByNodeId = (results: (ValidationResult | PipelineSyncError)[], nodeId: string) => {
  return results.filter((result) => result.nodeId === nodeId);
};

type ObjectMap<T> = { [key: string]: T };

export const getValidationResultCountsByGroupId = (
  results: ValidationResult[],
  nodes: any[],
  groups: EditorGroup[]
) => {
  let counts: ObjectMap<number> = {};
  let nodeGroupMap: ObjectMap<string | undefined> = {};

  nodes.forEach((node) => (nodeGroupMap[node.id] = node.model.parent));
  groups.forEach((group) => (counts[group.id] = 0));

  results.forEach((result) => {
    const groupId = nodeGroupMap[result?.nodeId ?? ''];

    if (result.nodeId && groupId) {
      if (counts[groupId]) {
        counts[groupId] += 1;
      } else {
        counts[groupId] = 1;
      }
    }
  });

  return counts;
};

export const getValidationResultCountsByNodeId = (
  results: ValidationResult[],
  mode: ValidationMode,
  coreNodeId?: string,
  entityId?: string,
  entityPipelineId?: string,
  fields?: EntityField[]
) => {
  let counts: ObjectMap<number> = {};
  let fieldLookup = keyBy(fields, 'id');

  results.forEach((result) => {
    if (result.nodeId) {
      let id = result.nodeId;

      // count errors to be displayed on core entity nodes
      if (mode === ValidationMode.ENTITY && coreNodeId) {
        if (result.level === ValidationErrorLevel.ATTRIBUTE && result.targetId && fieldLookup[result.targetId]) {
          id = coreNodeId;
        }

        if (result.level === ValidationErrorLevel.ENTITY) {
          if (result.targetId && result.targetId === entityId && result.nodeId === entityPipelineId) {
            id = coreNodeId;
          }
        }
      }

      // count errors to be displayed on all other nodes
      if (counts[id]) {
        counts[id] += 1;
      } else {
        counts[id] = 1;
      }
    }
  });

  return counts;
};

export const getCoreNode = (nodes: any) => {
  const node = find(nodes ?? [], (node) => {
    return node.nodeType === AppConstants.NODE_TYPE.CORE_ENTITY;
  });

  if (node) {
    return node;
  }
};

export const getEntity = (entityId: string, entities: Entity[]) => {
  const entity = find(entities, (entity) => {
    return entity.id === entityId;
  });

  if (entity) {
    return entity;
  }
};

export const getFieldName = (fieldId: string, fields: EntityField[]) => {
  const field = find(fields, (field: any) => {
    return field.id === fieldId;
  });

  if (field) {
    return field.displayName;
  }
};

export const getNodeName = (nodeId: string, nodes: any): string | undefined => {
  const node = find(nodes, (node: any) => {
    return node.id === nodeId;
  });

  if (node) {
    return node.name;
  }
};
