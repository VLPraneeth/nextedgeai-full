//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { ConditionValue } from 'components/inputs/types';
import AppConstants from 'utils/AppConstants';

export const { PIPELINE_CONTEXT } = AppConstants;

export type PipelineContext = typeof PIPELINE_CONTEXT[keyof typeof PIPELINE_CONTEXT];

export type PredicateGraphNodeUI = GraphNodeUI<PredicateConfiguration>;

export interface GraphNodeUI<TNodeConfiguration> {
  shape: string;
  label: string;
  icon: string;
  hideLeftStrip: boolean;
  description: string;
  typeColor: string;
  nodeType: string;
  deleteable: boolean;
  id: string;
  x: number;
  y: number;
  metadata: GraphNodeUIMetadata<TNodeConfiguration>;
}

export interface GraphNodeUIMetadata<TNodeConfiguration> {
  id: string;
  name: string;
  apiName: string;
  label: string;
  subLabel: string;
  inputPorts: GraphNodePort[];
  outputPorts: GraphNodePort[];
  configuration: TNodeConfiguration;
  nodeType: string;
  location: GraphNodeLocation;
  displayName: string;
  description: string;
  deleteable: boolean;
  nodeId: string;
  nodeName: string;
}

export interface GraphNodeLocation {
  x: number;
  y: number;
}

export interface PredicateConfiguration {
  predicate: GroupPredicate;
  definition: string;
  syncariEntityDefId: string;
  configId: string;
}

export interface GroupPredicate {
  predicates: Partial<ConditionValue[]>;
  groupPredicateId: string;
  operator: string;
}

export interface GraphNodePort {
  portType: string;
  datatype: string;
  maxConnections: number;
}

export enum DirectionId {
  BIDIRECTIONAL = 'BIDIRECTIONAL',
  SYNC_TO = 'SYNC_TO',
  SYNC_FROM = 'SYNC_FROM',
}
