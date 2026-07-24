import { RouteComponentProps } from '@reach/router';

import { saveFragment } from 'store/fragment/thunks';
import { FragmentActionTypes } from 'store/fragment/types';
import { PipelineFunction } from 'store/pipeline-functions';
import { EditorEdge, EditorGroup, EditorNode, Group } from 'store/pipeline/types';
import { GraphStatus } from 'store/schema/types';
import { TestPanelView } from 'store/test/types';

export interface Editor {
  [key: string]: any;
}

export interface EditorPage {
  getNodes: () => EditorNode[];
  getGroups: () => EditorGroup[];
  getEdges: () => EditorEdge[];
  getSelected: () => (EditorNode | EditorGroup | EditorEdge)[];
  getItems: () => (EditorNode | EditorGroup)[];
  find: (id: string) => EditorNode | EditorGroup | undefined;
  update: (itemOrItemId: string | EditorNode | EditorGroup | EditorEdge, update: Record<string, any>) => void;
}

export interface PipelineEditorState {
  approvedGraphJson: any;
  attributeNodes: any;
  connectorEntities: any;
  draftGraphJson: any;
  edges: any;
  editable: any;
  entityName: any;
  haveUnsavedChanges: any;
  lastAction: any;
  metadata: any;
  newGraphJson: any;
  nodes: any;
  groups: Group[] | null;
  pipelineFunctions: any;
  pipelineActions: any;
  selectedItemId: any;
  selectedNode: any;
  testNodeNotFoundVisible: any;
  shiftKeyActive: boolean;
  changeKey?: string;
  ready?: boolean;
}

export interface PipelineEditorProps extends RouteComponentProps {
  entityId: string;
  fieldId?: string;
  isEntityPipeline: Boolean;
  isFieldPipeline: Boolean;
  remountComponent: () => void;
  getSyncStatuses: () => void;
  nodeId?: string;
  renderGraph?: boolean;
  graphVersion?: GraphStatus;

  // Mapped state to props
  attributeNodes: any;
  attributeNodesFetching: any;
  changed: any;
  changedId: any;
  changedScope: any;
  connectorEntities: any;
  connectorEntitiesFetching: any;
  connectors: any;
  connectorsMetadata: any;
  createFragmentVisible: boolean;
  createTestVisible: any;
  currentInstanceState: any;
  deleteFragmentErrorMessage: any;
  deleteFragmentStatus: any;
  deleteMappingsResponse: any;
  deleteMultipleNodesModalVisible: boolean;
  displayedGraph: any;
  dragSelectMode: boolean;
  entities: any;
  entitiesFetching: boolean;
  entitySchema?: any;
  errorMessage: any;
  errorTitle: any;
  fragments: any;
  fragmentSaving: any;
  getFragmentStatus: any;
  hideFragmentErrorMessage: any;
  hideFragmentStatus: any;
  isGotoBetweenFieldPipelines: any;
  lastSyncedTime: any;
  pausedBy?: string;
  liveTestCompletedTimestamp: any;
  liveTestGraphId: any;
  nodeCheckId: any;
  nodeCheckMode: any;
  nodeCheckValue: any;
  nodeCheckValues: any;
  nodeConfigModalVisible: any;
  pipeline: any;
  pipelineActions: any;
  pipelineActionsFetching: any;
  pipelineApproving: any;
  pipelineCreating?: boolean;
  pipelineDeleting: any;
  pipelineDiscarding: any;
  pipelineError: any;
  pipelineExists: any;
  pipelineFetching: any;
  pipelineFunctions: PipelineFunction[];
  pipelineFunctionsFetching: any;
  pipelineSaved: any;
  pipelineSaving: any;
  pipelineValidated: any;
  pipelineValidating: any;
  pipelineViewportMatrix?: number[];
  readOnly: any;
  savedNodeConfig: any;
  saveFragmentErrorMessage: any;
  saveMappingsResponse: any;
  schemas: any;
  selectedNode: any;
  selectedTestNodeId: any;
  showFragmentErrorMessage: any;
  showFragmentStatus: any;
  syncStatus: any;
  testPanelView: any;
  testResultVisible: any;
  validationErrors: any;
  validationResultsPanelVisible: any;
  validationWarnings: any;

  // Mapped dispatch to props
  approveFieldPipeline: () => void;
  clearAttributeNodes: () => void;
  clearConnectorEntities: () => void;
  clearError: () => void;
  clearNodeCheckValues: () => void;
  clearPipeline: () => void;
  createDraftFieldPipeline: (fieldId: string) => void;
  createDraftEntityPipeline: (entityId: string) => void;
  deleteFieldPipeline: (fieldId: string) => void;
  deleteFragment: () => void;
  deletePublishedPipeline: (entityId: string) => void;
  discardPipeline: (fieldOrEntityId: string, options: any) => void;
  enableNodeCheck: (enable?: boolean) => void;
  getAsyncNodeConfig: () => void;
  getAttributeNodes: (attributeId: string) => void;
  getConnectorEntities: (entityId: string) => void;
  getConnectors: () => void;
  getEntities: () => void;
  getPipeline: () => void;
  getPipelineActions: (entityOrGraphId: string) => void;
  getPipelineFunctions: (entityOrGraphId: string) => void;
  getSchemaForEntity?: (values: { entityId: string; graphVersion: string }) => void;
  getUserPreference: () => void;
  graphChanged: (options: any) => void;
  hideFragment: () => void;
  resetFragmentModal: () => void;
  saveFragment: typeof saveFragment;
  setCurrentGraph: (graphJson: any) => void;
  setDisplayedGraph: (graphVersion?: GraphStatus) => void;
  setGraphForPublishReadyOnly: (graph: any) => void;
  setIsGotoBetweenFieldPipelines: (goto: boolean) => void;
  setNavigatingTo: (url?: string) => void;
  setNodeCheck: (nodeId: string, value: boolean) => FragmentActionTypes;
  setPipelineContext: (pipelineContext: string) => void;
  setPipelineId: (id: string) => void;
  setSelectedGraphNode: (node?: any) => void;
  setTestPanelView: (view: TestPanelView) => void;
  setUserPreference: () => void;
  showCreateFragmentModal: (visible?: boolean) => void;
  showCreateVersionModal: (options: { visible: boolean }) => void;
  showCreateTest: (visible: boolean) => void;
  showDeleteMultipleNodesModal: (visible: boolean) => void;
  showFastMapper: (options: { visible: boolean; entityId: string }) => void;
  showFragment: () => void;
  showNodeConfigModal: (visible: boolean) => void;
  showPipelineError: (error?: string) => void;
  showDeleteDraftModal: (visible?: boolean, entityId?: string, refreshOnDelete?: boolean) => void;
  showPublishDraftModal: (visible?: boolean, entityId?: string, hasUnpublishedSynapse?: boolean) => void;
  showShareFragmentModal: () => void;
  showUnsavedConfirmModal: (visible?: boolean) => void;
  showValidationResultsPanel: (visible: boolean) => void;
  start: (entityId: string) => void;
  stop: (entityId: string) => void;
  updatePipeline: (
    id: string,
    graphJson: any,
    options?: { refreshPipelineOnUpdate?: boolean; entityId?: string }
  ) => void;
  validate: (id: string, draftGraph: any) => void;
}

export interface PipelineToolbarProps extends PipelineEditorProps {
  hasUnsavedChanges: boolean;
  setHasUnsavedChanges: (hasUnsavedChanges: boolean) => void;
}
