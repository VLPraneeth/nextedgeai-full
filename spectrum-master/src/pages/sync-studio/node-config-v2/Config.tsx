//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { ReactNode, useEffect } from 'react';

import { showNodeConfigModal } from 'actions/entityPipelineActions';
import { ConfigRenderer, SkullConfigMetadata, useSkullConfig } from 'components/skull';
import { SkullConfigProvider } from 'components/skull/skullConfigContext';
import {
  useEnhancedDispatch as useDispatch,
  useEnhancedSelector,
  useEnhancedSelector as useSelector,
} from 'hooks/redux';
import {
  selectConfigRenderer,
  selectSelectedGraphNode,
  selectSkullMetadataForSelectedNode,
} from 'store/pipeline/selectors';
import { Connector } from 'store/schema/types';
import AppConstants from 'utils/AppConstants';

import { useDynamicConfig } from '../node-config/Config.hooks';
import ConfigWizard from '../node-config/ConfigWizard';
import QuickStartLegacyWizard from '../node-config/QuickStartLegacyWizard';
import { usePipelineEditor } from '../pipeline/v2/context/PipelineEditorV2.context';
import NodeConfigModal from './NodeConfigModal';

// We are exposing a seperate set of configuration objects on Syncari connector
// nodes. We have to differentiate between these nodes and the core node in a
// pipeline in order for these configuration objects to be rendered properly.
export const syncariConnectorNodeTypes: string[] = [
  AppConstants.NODE_TYPE.ENTITY_SOURCE,
  AppConstants.NODE_TYPE.CONNECTOR_ENTITY,
];

const Config = () => {
  const configRenderer = useSelector(selectConfigRenderer);
  const staticSkullMetaData = useSelector(selectSkullMetadataForSelectedNode);

  const { selectedGraphNode } = usePipelineEditor();

  const { isCoreEntityNode, dynamicConfig, getDynamicNodeConfig } = useDynamicConfig();

  useEffect(() => {
    if (isCoreEntityNode) {
      getDynamicNodeConfig();
    }
    // This only need to get called once since the selected node cannot change during this component's lifetime.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Core entity are always dynamic config so we make sure there is one available
  if (configRenderer === ConfigRenderer.WIZARD && isCoreEntityNode && dynamicConfig) {
    const nodeConfig = dynamicConfig;
    const skullMetaData = {
      nodeConfig,
      configTitle: nodeConfig?.renderer?.title,
      configSteps: nodeConfig?.renderer?.steps,
      configInputs: nodeConfig?.configuration,
      configValue: selectedGraphNode?.data.fullNode,
      groupConfiguration: staticSkullMetaData.groupConfiguration,
    };
    return <StaticConfig skullMetaData={skullMetaData} />;
  }
  // None core are static config flow
  if (!isCoreEntityNode) {
    return <StaticConfig skullMetaData={staticSkullMetaData} />;
  }
  return null;
};

export default Config;

const StaticConfig = ({ skullMetaData }: { skullMetaData: SkullConfigMetadata<any> }) => {
  const dispatch = useDispatch();

  const selectedGraphNode = useSelector(selectSelectedGraphNode);

  const { connectorEntities } = useEnhancedSelector((state) => state.entityPipeline);

  const syncariConnectorEntity = connectorEntities.find(
    (connector: Connector) => connector.name.toLowerCase() === 'syncari'
  );

  const configRenderer = useSelector(selectConfigRenderer);

  const context = useSkullConfig(skullMetaData);

  // If a user double clicks a node and then quickly clicks off the entity to
  // deselect it, we want to unmount the config modal.
  useEffect(() => {
    if (!selectedGraphNode && (!configRenderer || configRenderer === ConfigRenderer.FORM)) {
      dispatch(showNodeConfigModal(false));
    }
  }, [configRenderer, dispatch, selectedGraphNode]);

  let content: ReactNode = null;

  if (configRenderer === ConfigRenderer.WIZARD) {
    const configId = selectedGraphNode?.metadata?.configuration?.connectorId;
    if (syncariConnectorNodeTypes.includes(selectedGraphNode.nodeType) && configId === syncariConnectorEntity.id) {
      content = <NodeConfigModal key="config-form" />;
    } else {
      content = <ConfigWizard key="config-wizard" />;
    }
  } else if (configRenderer === ConfigRenderer.QUICK_START_WIZARD) {
    // TODO: Test this
    content = <QuickStartLegacyWizard key="config-wizard" />;
  } else if (selectedGraphNode) {
    // The default renderer is ConfigForm, we only render this if there is a
    // selectedGraphNode
    content = <NodeConfigModal key="config-form" />;
  }

  return <SkullConfigProvider value={context}>{content}</SkullConfigProvider>;
};
