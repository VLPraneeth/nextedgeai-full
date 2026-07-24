//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { InfoBoxProps } from 'components/InfoBox';
import { CompositeValue, CompositeValues } from 'components/inputs/composite/types';
import { FilterValue } from 'components/inputs/types';
import { FieldDataType } from 'components/types';
import { PageInfo } from 'store/transactions/types';
import AppConstants from 'utils/AppConstants';
import { NodeTypeKeys } from 'utils/AppConstants.types';
import { KeysOf, ValuesOf } from 'utils/TypeUtils';

import { GroupNodeUpdateParams, NodeKebabActionParams } from './actions';

export type Scope = ValuesOf<typeof AppConstants.SCOPE>;
export type EntityType = KeysOf<typeof AppConstants.ENTITY_TYPES>;
export type GraphStatus = ValuesOf<typeof AppConstants.GRAPH_STATUS>;
export type GraphNodeShapes = ValuesOf<typeof AppConstants.GRAPH_NODE_SHAPES>;
export type NodeType = ValuesOf<typeof AppConstants.NODE_TYPE>;
export type GroupColor = KeysOf<typeof AppConstants.GROUP_COLORS>;

export interface NodeConfiguration {
  mapping: Mapping | Mapping[];
  dependsOn?: DependsOn;
  datatype: FieldDataType;
  defaultValue?: string | null;
  helpPath?: string | null;
  helpSummary?: string | null;
  name: string;
  fieldSet: string;
  label: string;
  id: string;
  values?: NodeFieldPicklistValue[] | InfoBoxProps | Record<string, any>;
  renderType?: string;
  type?: string;
}

export interface NodeFieldPicklistValue {
  label: string;
  value: string;
  datatype?: FieldDataType;
  type?: string;
  picklistGroup?: string;
}

export interface DependsOn {
  dependantField: string;
  dependantType: string;
  params?: { name: string; value: string }[];
}

export interface Mapping {
  graphKey: string;
  configKey?: string;
}

export interface GraphModel {
  nodes: Node[];
  edges?: Edge[];
  groups?: Group[];
  id: string;
  targetId: string;
  parentId?: string;
  scope: string;
  name: string;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
  lastSyncedTime: string;
  syncStatus: string;
  ready: boolean;
  draftStatus: string;
  readOnly: boolean;
  readOnlyReason: string;
  draft: Omit<GraphModel, 'draft'>;
}

export interface DedupMergeConfiguration {
  selectWinner?: SelectWinner;
  fieldLevelOverrides?: FieldLevelOverrides;
  findDupes?: FindDupes;
  entityDefinition: string;
  enableDeduplicate?: boolean;
  configId: string;
  defaultMergePolicy?: string;
  dataAuthorityStrategy?: string;
  defaultOverridePolicy?: string;
  schedule?: string;
  connectorId?: string;
}

export type FindDupes = CompositeValues<FindDupesCompositeValue>;

interface FindDupesCompositeValue {
  repeatId: string;
  findDupesPredicate: FindDupesPredicate;
}

export type FindDupesPredicate = CompositeValue<FilterValue>;

export type FieldLevelOverrides = CompositeValues<FieldLevelOverridesCompositeValue[]>;

interface FieldLevelOverridesCompositeValue {
  repeatId: string;
  field: CompositeValue;
  fieldMergePolicy: CompositeValue;
  fieldOverridePolicy: CompositeValue;
}

export type SelectWinner = CompositeValues<SelectWinnerCompositeValue[]>;

export interface SelectWinnerCompositeValue {
  repeatId: string;
  winnerSelectionPredicate: CompositeValue<FilterValue>;
}

export interface Edge {
  id: string;
  source: Source;
  destination: Source;
}

export interface Source {
  nodeId: string;
  port: InputPort;
  anchor: string;
}

export interface EditorBaseItem {
  toFront: () => void;
  toBack: () => void;
}

export interface EditorEdge extends EditorBaseItem {
  isGroup: false;
  isItem: true;
  isNode: false;
  isEdge: true;
  source: EditorNode;
  target: EditorNode;
}

export interface Node {
  id: string;
  name: string;
  apiName: string;
  label: string;
  subLabel: string;
  inputPorts: InputPort[];
  outputPorts: InputPort[];
  configuration: ConnectorEntityConfiguration | DedupMergeConfiguration;
  nodeType: NodeTypeKeys;
  location: Location;
  iconPath?: string;
  iconAssetPath?: string;
  groupId?: string;
  connectorEntityName?: string;
}

export interface EditorNode extends EditorBaseItem {
  id: string;
  model: Omit<Node, 'groupId'> & { parent?: string };
  isAnchorShow: boolean;
  isGroup: false;
  isItem: true;
  isNode: true;
  isSelected: boolean;
  type: 'node';
  visible: boolean;
  zIndex: number;
  parent?: string;
}

export interface Group {
  id: string;
  label: string;
  name: string;
  description: string;
  childNodeSummary: string;
  subLabel: string;
  inputPorts: InputPort[];
  outputPorts: InputPort[];
  color: GroupColor;
  tags: string[];
  apiName: string;
  shape: typeof AppConstants.GRAPH_NODE_SHAPES.CUSTOM_GROUP;
  nodeType: NodeType;
  collapsed: boolean;
  configuration: ConnectorEntityConfiguration | DedupMergeConfiguration;
  location: Location;
  iconPath?: string;
  iconAssetPath?: string;
}

export interface EditorGroup extends EditorBaseItem {
  id: string;
  model: Group;
  isAnchorShow: boolean;
  isGroup: true;
  isItem: true;
  isNode: false;
  isSelected: boolean;
  type: 'group';
  visible: boolean;
  zIndex: number;
  iconAssetPath?: string;
}

export type NodeOrGroup = Node | Group;

export interface Location {
  y: string;
  x: string;
}

export interface ConnectorEntityConfiguration {
  entityDefinition: string;
  enableDeduplicate?: boolean;
  dataAuthorityStrategy?: string;
  configId: string;
  schedule?: string;
  connectorId?: string;
}

export interface InputPort {
  portType: string;
  datatype: string;
  maxConnections: number;
}

export interface TooltipCoordinates {
  top: number | string;
  left: number | string;
}

export interface CreateGroupPanelParams {
  visible: boolean;
  selectedGroup?: Group;
}

export interface ConfirmUngroupModalParams {
  visible: boolean;
  groupId?: string;
}
export interface CreateVersionModalParams {
  visible: boolean;
}

export interface RestoreVersionModalParams {
  visible: boolean;
  versionId?: string;
  name?: string;
  versionTwoId?: string;
  versionOneNumber?: number;
  versionTwoNumber?: number;
}

export interface ConfirmDuplicateModalParams {
  visible: boolean;
  node?: Node;
}

export interface SettingsPanelParams {
  visible: boolean;
}

export interface PipelineState {
  changed: boolean;
  changedId?: string | null;
  changedScope?: Scope | null;
  currentGraph?: GraphModel | null;
  displayedGraph?: GraphStatus | null;
  dragSelectMode: boolean;
  groupNodeUpdate?: GroupNodeUpdateParams | null;
  nodeKebabAction?: NodeKebabActionParams | null;
  selectedNodeIds: string[];
  pipelineId?: string | null;
  unsavedConfirmModalVisible: boolean;
  deleteMultipleNodesModalVisible: boolean;
  createGroupPanel: CreateGroupPanelParams;
  confirmUngroupModal: ConfirmUngroupModalParams;
  createVersionModal: CreateVersionModalParams;
  restoreVersionModal: RestoreVersionModalParams;
  confirmDuplicateModal: ConfirmDuplicateModalParams;
  createTestVisible: boolean;
  testResultVisible: boolean;
  tooltipCoordinates: TooltipCoordinates;
  settingsPanel?: SettingsPanelParams;
}

export interface PipelineVersion {
  versionId: string; // auto assigned version id
  versionNumber: number; // auto assigned version number
  name: string; // user provided version name
  createdBy: string; // user name
  createdAt: string; // datetime string
  numberOfChanges: number; // total count of nodes that changes in all pipelines. Should match the number of list items in diff view from previous version.
  summary: string; // user provided description
  actionType: string; // saved/manual, deleted, restored, published
}

export type PipelineChangeTypes = 'Modified' | 'Unchanged' | 'Created' | 'Deleted';

export interface PipelineVersionPipeline {
  targetId: string;
  id: string;
  pipelineType: 'ATTRIBUTE' | 'ENTITY';
  displayName: string;
  apiName: string;
  changeType: PipelineChangeTypes;
}

export interface CreatePipelineVersionRequest {
  name: string;
  syncariEntityId: string;
  summary?: string;
}

export interface RestorePipelineVersionRequest {
  syncariEntityId: string;
  versionId: string;
  restoreAll: boolean;
  fieldIds?: string[];
  restoreEntity?: boolean;
}

export type PipelineDiffOps = 'add' | 'remove' | 'modified';

export interface PipelineDiff {
  op: PipelineDiffOps;
  displayName: string;
  itemName: string;
  nodeType: string;
  values: DiffValue[];
}

export interface DiffValue {
  id: string;
  label: string;
  previousValue: null | string;
  value: null | string;
  renderHtml?: boolean;
}

export interface ResyncDetails {
  entitiesToResync?: Record<string, string>;
  startTime?: string;
  endTime?: string;
  status?: string;
  errorMessage?: string;
  lastResyncTime?: string;
  syncStatus?: string;
}

export interface SyncDurationDetails {
  duration: number;
  durationUnit: string;
}

export interface SinkOrSourceDetails {
  connectorName: string;
  connectorType: string;
  connectorId: string;
  entityId: string;
  entityName: string;
}

export interface PipelineDetails {
  syncariEntityId: string;
  fieldsMapped: number;
  lastModifiedBy: string;
  lastModifiedOn: Date;
  lastPublishedOn: Date;
  mergeConfig: boolean;
  numberOfVersions: number;
  sinks: SinkOrSourceDetails[];
  sources: SinkOrSourceDetails[];
  resyncDetail: ResyncDetails;
  settings?: Record<string, string | boolean>;
}

export interface PipelineTransactionDetails {
  syncariEntityId: string;
  transactionsInLastCycle: number;
}

export interface PipelineSyncMetricDetails {
  syncariEntityId: string;
  currentActivity: string | null;
  lastCycleDuration: SyncDurationDetails;
}

export interface PipelineDocumentation {
  syncariEntityId: string;
  content: string | null;
}

export interface PipelineLogsParams {
  cursor: string;
  direction: string;
  count: string;
  start: string;
  end: string;
  syncariRecordId?: string;
  syncariEntityId: string;
  status: string;
}

export interface PipelineLog {
  batchId: string;
  entityPipelineId: string;
  syncariAttributeId: string;
  error?: string;
  errorDetails?: string;
  externalEntity?: string;
  externalRecordIds?: Record<string, string>;
  id: string;
  input: Record<string, any>;
  nodeId: string;
  nodeName: string;
  nodeType?: string;
  occurredTime?: string;
  output: Record<string, any>;
  pipelineId: string;
  pipelineName: string;
  scope: Scope;
  syncariRecordId: string;
  batchMode?: string;
  runMode?: string;
  timeTakenInMillis?: string;
}

export interface PipelineLogsResponse {
  records: PipelineLog[];
  pageInfo: PageInfo;
}
