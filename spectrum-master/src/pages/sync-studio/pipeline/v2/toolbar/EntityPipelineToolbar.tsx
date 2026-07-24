import { Modal } from 'antd';
import { MenuProps } from 'antd/lib/menu';
import ObjectID from 'bson-objectid';
import { keyBy } from 'lodash';
import { useCallback } from 'react';

import { useCurrentSyncStudioRootTab } from 'pages/sync-studio/entity/SyncStudioRootTabs';
import { TestPanelView } from 'store/test/types';
import AppConstants from 'utils/AppConstants';
import { getNavigateParams, navigateTo } from 'utils/AppUtil';
import { connectorIsCustomDraft } from 'utils/ConnectorUtil';
import { tc } from 'utils/i18nUtil';
import { createDraftGraph, navigateToGraphVersion } from 'utils/PipelineUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { PipelineToolbarProps } from '../../PipelineEditor.types';
import useEnhancedReactFlow from '../hooks/useEnhancedReactFlow';
import { isGraphStatusEditable } from '../PipelineEditorV2.utils';
import { flowToLegacyEdges, flowToLegacyNodes } from '../PipelineTransformer';
import PipelineToolbarV2, { GraphToolbarPropsV2 } from './PipelineToolbarV2';
import useToolbarProps from './useToolbarProps';

const { GRAPH_STATUS } = AppConstants;

const EntityPipelineToolbar = (props: PipelineToolbarProps) => {
  const onSaveChanges = () => {};
  const reactFlow = useEnhancedReactFlow();

  const isApproveOnlyGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft === null;
  }, [props]);

  const isApproveWithDraftGraph = useCallback(() => {
    const { pipeline = {} } = props;
    return pipeline?.draftStatus === GRAPH_STATUS.APPROVED && pipeline?.draft !== null;
  }, [props]);

  const onTest = () => {
    props.setTestPanelView(TestPanelView.LIVE_RUN);
  };

  const onStop = () => {
    props.stop(props.entityId);
  };

  const onStart = () => {
    props.start(props.entityId);
  };

  const { pipeline: storedPipeline } = props;

  const setGraphForPublishReadyOnly = () => {
    const pipeline = reactFlow.toObject();
    const nodes = flowToLegacyNodes(pipeline.nodes);
    const edges = flowToLegacyEdges(pipeline.edges);

    const draftGraph = createDraftGraph(nodes, edges);

    const id = ObjectID.generate();
    const { draftStatus, draft, ...metadataSpread } = storedPipeline;

    // // Save the draft to the state
    const graph = {
      ...storedPipeline,
      nodes,
      edges,
      draft: {
        ...metadataSpread,
        id,
        parentId: storedPipeline.id,
        nodes: draftGraph.nodes,
        edges: draftGraph.edges,
      },
    };
    props.setGraphForPublishReadyOnly(graph);
  };

  const publishPipeline = () => {
    setGraphForPublishReadyOnly();
    const connectorsMap = keyBy(props.connectors, 'id');
    const connectorsMetadataMap = keyBy(props.connectorsMetadata, 'id');
    const hasUnpublishedCustomSynapse = props.pipeline.nodes.some((node: any) => {
      const connector = connectorsMap[node?.configuration?.connectorId];
      return connectorIsCustomDraft(connectorsMetadataMap[connector?.metadataId]);
    });
    props.showPublishDraftModal(true, props.entityId, hasUnpublishedCustomSynapse);
    // Close the test pannel when publishing to prevent users from running tests
    // on a published pipeline
    props.setTestPanelView(TestPanelView.CLOSED);
  };

  const onPublishPipeline = async () => {
    if (props.changed) {
      await onSaveChanges();
      if (props.errorMessage) {
        Modal.error({
          title: props.errorTitle,
          content: props.errorMessage,
        });
      } else {
        publishPipeline();
      }
    } else {
      publishPipeline();
    }
  };

  const onChangeGraph: MenuProps['onClick'] = (evt) => {
    const newVersion = evt.key;
    // if (state.haveUnsavedChanges) {
    //   const url = getGraphVersionUrl(entityId, newVersion.toLowerCase(), fieldId);
    //   props.setNavigatingTo(url);
    //   props.showUnsavedConfirmModal(true);
    // } else {
    navigateToGraphVersion({
      graphVersion: newVersion,
      entityId: props.entityId,
      fieldId: props.fieldId,
      replace: false,
    });
    // }
  };

  const { currentTab } = useCurrentSyncStudioRootTab();

  const navigateToEntities = () => {
    const url = makeUrl(RouteConstants.ENTITY, {
      entityId: props.entityId,
      tabId: currentTab,
    });

    navigateTo(url, getNavigateParams({ ...props }));
  };

  const epToolbarProps: Partial<GraphToolbarPropsV2> = {
    onPublishPipeline,
    onTest,
    onStop,
    onStart,
    publishProps: {},
    disableValidate: false,
    disableTest: false,
    showTest: false,
    pausedBy: props.pausedBy,
    onChangeGraph,

    goToName: tc('entities'),
    navigateUp: navigateToEntities,
    // lastSyncedTime is not used by the PipelineToolbar. Instead it uses the
    // readOnlyMsg which has the lsat synced time from the backend. I think it
    // would be better to explicitely use the lastSyncedTime and convert it to
    // the user's timezone.
    // lastSyncedTime: `${lastSyncedTime}`,
    readOnlyMsg: '',
  };

  const setDraftReadOnly = () => {
    epToolbarProps.disableTest = true;
    epToolbarProps.publishProps!.disabled = true;
    epToolbarProps.publishProps!.tooltip = epToolbarProps.readOnlyMsg;
    // currentGraphRef.current.readOnly = true;
    // currentGraphRef.current.readOnlyMsg = epToolbarProps.readOnlyMsg as string;
  };

  const setDraftEditable = () => {
    epToolbarProps.disableTest = false;
    epToolbarProps.publishProps!.disabled = false;
    epToolbarProps.publishProps!.tooltip = '';
    // currentGraphRef.current.readOnly = false;
    // currentGraphRef.current.readOnlyMsg = '';
  };

  if (isApproveOnlyGraph()) {
    if (props.displayedGraph === GRAPH_STATUS.DRAFT) {
      epToolbarProps.showPublishDraft = true;
      epToolbarProps.disableValidate = false;
      epToolbarProps.readOnlyMsg = props.pipeline.readOnlyReason;
      if (props.pipeline.draft && props.pipeline.draft.readOnly) {
        setDraftReadOnly();
      } else {
        setDraftEditable();
      }
    }
  } else if (isApproveWithDraftGraph()) {
    if (props.displayedGraph === GRAPH_STATUS.DRAFT) {
      epToolbarProps.showPublishDraft = true;
      epToolbarProps.disableValidate = false;
      epToolbarProps.readOnlyMsg = props.pipeline.draft.readOnlyReason;
      if (props.pipeline.draft.readOnly) {
        setDraftReadOnly();
      } else {
        setDraftEditable();
      }
    }
  } else if (props.displayedGraph === GRAPH_STATUS.NEW) {
    epToolbarProps.showPublishDraft = true;
    epToolbarProps.disableValidate = false;
    epToolbarProps.readOnlyMsg = props.pipeline.readOnlyReason;
    if (props.pipeline.readOnly) {
      setDraftReadOnly();
    } else {
      setDraftEditable();
    }
  }
  if (props.displayedGraph === GRAPH_STATUS.APPROVED) {
    epToolbarProps.readOnlyMsg = props.pipeline.readOnlyReason;
  } else {
    if (props.pipeline.draft) {
      epToolbarProps.readOnlyMsg = props.pipeline.draft.readOnlyReason;
      if (props.pipeline.draft.readOnly) {
        setDraftReadOnly();
      } else {
        setDraftEditable();
      }
    }
    epToolbarProps.showTest = true;
  }

  if (isGraphStatusEditable(props.displayedGraph)) {
    epToolbarProps.showTest = false;
  }
  const generalepToolbarProps = useToolbarProps(props);

  return <PipelineToolbarV2 {...generalepToolbarProps} {...epToolbarProps} />;
};

export default EntityPipelineToolbar;
