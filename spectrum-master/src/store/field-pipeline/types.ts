//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import fieldPipeline from 'reducers/fieldPipelineReducer';
import { ConnectorEntityNode } from 'store/entity-pipeline/types';

export type FieldPipelineState = ReturnType<typeof fieldPipeline>;

export type AttributeNodeNodeType = 'sink' | 'source' | 'core';

export interface AttributeNode extends Omit<ConnectorEntityNode, 'coreNode'> {
  isCoreNode: boolean;
  connectorName?: string;
  connectorId?: string;
  label: string;
  type: AttributeNodeNodeType;
  entityDefinitionId: string;
}
