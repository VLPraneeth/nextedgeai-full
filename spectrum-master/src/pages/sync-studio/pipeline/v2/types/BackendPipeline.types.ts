// The types here are revamped for the v2 pipeline project.

import { SwitchCaseValue } from 'components/switch-case/SwitchCaseComposite';
import { NodeType } from 'store/pipeline/types';
export interface PipelineV2 {
  nodes: PipelineNodeV2[];
  edges: PipelineEdgeV2[];
  groups: any[];
  id: string;
  targetId: string;
  parentId: null | string;
  scope: string;
  name: string;
  createdBy: null | string;
  updatedBy: string;
  createdAt: Date | null;
  updatedAt: Date;
  lastSyncedTime: Date | null;
  syncStatus: null | string;
  ready: boolean;
  draftStatus: string;
  readOnly: boolean;
  readOnlyReason: string;
  draft: PipelineV2 | null;
  resyncDetail: null;
  pausedBy: null;
  settings: null;
}

export interface PipelineEdgeV2 {
  id: string;
  source: Destination;
  destination: Destination;
  originalId: string;
}

export interface Destination {
  nodeId: string;
  port: NodePort;
  anchor: string;
}

export interface NodePort {
  portType: 'INPUT' | 'OUTPUT';
  datatype: 'object' | 'string';
  maxConnections: number;
}

export interface PipelineNodeV2 {
  id: string;
  name: string;
  label: string;
  configuration: NodeConfiguration;
  nodeType: NodeType;
  location: Location;
  apiName?: string;
  subLabel?: string;
  iconPath?: null | string;
  groupId?: null | string;
  originalId?: string;

  // Original design used these
  inputPorts: NodePort[];
  outputPorts: NodePort[];
}

export interface NodeConfiguration {
  configId: string;
  newValue?: string; // Used on the Set Value field for some special handing in the NodeConfigPanel file.
  targetId?: string;
  definition?: string; // Used in PipelineEditor to define special nodes like loop and case. I think it's used on the backend to determine the type of node.
  predicate?: ConfigurationPredicate;
  value?: boolean | string;
  connectorId?: string;
  entityDefinition?: string;
  entity?: string;

  // Fields below don't seem to be used in the frontend
  enableDeduplicate?: boolean;
  enableNodeLogs?: boolean;
  defaultOverridePolicy?: string;
  attachPredicate?: AttachPredicate;
  sourceParams?: SourceParams;
  exhaustAllRecords?: string;
  createDisconnectedMapping?: boolean;
  syncOnTxnLog?: boolean;
  rejectEmpty?: boolean;
  updateFields?: UpdateField[];
  fieldLevelOverrides?: {};
  realtime?: boolean;
  selectMergeAction?: boolean;
  fieldMergePolicies?: {};
  defaultMergePolicy?: string;
  findDupes?: {};
  progressiveSelection?: boolean;
  syncari_src_predicate?: string;
  acceptsDeletesFrom?: any[];
  schedule?: string;
  skipWhen?: null;
  dataAuthorityStrategy?: string;
  selectWinner?: SelectWinner;
  pipeLineBatchSize?: null;
  destinationParams?: {};
  sortFields?: any[];
  maxDupes?: number | null;
  case?: SwitchCaseValue;
}

export interface AttachPredicate {
  predicates: AttachPredicatePredicate[];
  groupPredicateId: string;
  operator: string;
}

export interface AttachPredicatePredicate {
  left: Left;
  operator: string;
  right: PredicateRight;
  predicateId: string;
  name?: string;
}

export interface Left {
  value: string;
  label: string;
  type: Type;
  datatype?: LeftDatatype;
}

export enum LeftDatatype {
  Picklist = 'picklist',
  Reference = 'reference',
  String = 'string',
}

export enum Type {
  Variable = 'variable',
}

export interface PredicateRight {
  value: string;
  type: string;
}

export interface ConfigurationPredicate {
  predicates: PredicatePredicate[];
  groupPredicateId: string;
  operator: string;
}

export interface PredicatePredicate {
  left: Left;
  operator: string;
  right: ConfigurationRight;
  predicateId: string;
  name?: string;
}

export interface ConfigurationRight {
  value: string[] | string;
  type: string;
}

export interface SelectWinner {
  name: string;
  compositeValues: SelectWinnerCompositeValue[];
}

export interface SelectWinnerCompositeValue {
  repeatId: string;
  winnerSelectionPredicate: WinnerSelectionPredicate;
}

export interface WinnerSelectionPredicate {
  name: string;
  value: ValueClass;
}

export interface ValueClass {
  predicates: PredicatePredicate[];
  groupPredicateId: string;
  operator: string;
}

export interface SourceParams {
  // The value of these keys appears to be an empty string
  syncari_instance__c_syncari_src_predicate?: string;
  authprovider_syncari_src_predicate?: string;
}

export interface UpdateField {
  repeatId: string;
  newValue: NewValue;
  updateField: NewValue;
  operation: NewValue;
}

export interface NewValue {
  name: string;
  value: string;
}

export interface Location {
  x: number;
  y: number;
}

export interface EntitySchemaField {
  id: string;
  apiName: string;
  displayName: string;
  dataStoreName: null;
  description: null | string;
  dataType: string;
  status: Status;
  type: null;
  compositeKey: null;
  tags: any[];
  values: any[];
  isMapped: boolean;
  hasChanges: boolean;
  draftStatus: DraftStatus;
  readOnly: boolean;
  createOnly: boolean;
  required: boolean;
  unique: boolean;
  watermarkField: boolean;
  ready: boolean;
  referenceTo: null | string;
  referenceTargetField: null | string;
  isSyncariDefined: boolean;
  parentAttributeId: null;
  length: number;
  precision: number;
  scale: number;
  index: number;
  subDataType: null;
  schemaUpdatable: boolean;
  schemaDeletable: boolean;
  hasPublishedPipeline: boolean;
  pipelineStatus: PipelineStatus;
  system: boolean;
  idField: boolean;
  multiValueField: boolean;
  reference: boolean;
  potentialWatermarkField: boolean;
}

export enum DraftStatus {
  Approved = 'APPROVED',
  New = 'NEW',
}

export enum PipelineStatus {
  Draft = 'DRAFT',
  Unmapped = 'UNMAPPED',
}

export enum Status {
  Active = 'ACTIVE',
}
