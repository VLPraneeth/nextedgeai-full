import { Button, Form, Modal } from 'antd';
import Select, { OptionProps } from 'antd/lib/select';
import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { sortBy } from 'lodash';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { getEntityPipeline } from 'actions/entityPipelineActions';
import InlineMessage from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EmptyPanelContent } from 'pages/insights-studio/components/empty-panel-content/EmptyPanelContent';
import { CopyableInput } from 'pages/settings/SsoConfig';
import { makeSynapseOption } from 'pages/sync-studio/fast-mapper/Mapper';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { selectUserConnectorsForDisplay } from 'store/connectors/selectors';
import { selectConnectorEntitiesOnly } from 'store/entity/selectors';
import { getConnectorEntities } from 'store/entity/thunks';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';

import { usePipelineSettings } from '../settings/Settings.hooks';
import { useRealtimePipelineContext } from './RealtimePipeline.context';

import './RealtimePipelineModal.scss';

export interface RealtimePipelineModalProps {
  editor: any;
  saveChanges: any;
  nodes: any[];
  edges: any[];
  entityId: string;
}

const tn = tNamespaced('RealtimePipeline');

const RealtimePipelineModal = ({ editor, saveChanges, nodes, edges, entityId }: RealtimePipelineModalProps) => {
  const { visible, setVisible, setEnabled, enabled, ipWhitelist, setIpWhitelist } = useRealtimePipelineContext();
  const [showValidation, setShowValidation] = useState(false);
  const dispatch = useEnhancedDispatch();
  const { settings, version } = usePipelineSettings();
  const connectorIdToMetadataMap = useConnectorIdToMetadataMap();
  const connectors = useEnhancedSelector(selectUserConnectorsForDisplay);
  const connectorEntities = useEnhancedSelector(selectConnectorEntitiesOnly);
  const [selectedSynapse, setSelectedSynapse] = useState<string | undefined>();
  const [initialShow, setInitialShow] = useState(false);

  const webhookSynapse = useMemo(() => {
    return sortBy(
      connectors?.filter((connector) => connectorIdToMetadataMap[connector.id]?.webhook),
      'name'
    );
  }, [connectorIdToMetadataMap, connectors]);

  const connectorOptions = useMemo(() => {
    // @ts-ignore
    return webhookSynapse?.map(makeSynapseOption(connectorIdToMetadataMap));
  }, [connectorIdToMetadataMap, webhookSynapse]);

  useEffect(() => {
    if (selectedSynapse && !connectorEntities?.[selectedSynapse]) {
      dispatch(getConnectorEntities(selectedSynapse, false, true));
    }
  }, [connectorEntities, dispatch, selectedSynapse]);

  // Use effect initialization when show/hide this modal
  useEffect(() => {
    if (!visible) {
      setSelectedSynapse(undefined);
      setInitialShow(false);
    } else {
      let existingWebhookNode = nodes.find((node: any) => node.configuration?.isRealtimeWebhookSource);

      if (!existingWebhookNode && enabled && version?.toLocaleUpperCase() === AppConstants.GRAPH_STATUS.APPROVED) {
        // Use the webhook in the pipeline if realtime is enabled and only one webhook in the a published pipeline
        const webhooks = nodes.filter((node: any) => {
          return (
            node.nodeType === AppConstants.NODE_TYPE.ENTITY_SOURCE &&
            connectorIdToMetadataMap[node.configuration.connectorId]?.webhook
          );
        });
        if (webhooks.length === 1) {
          existingWebhookNode = webhooks[0];
        }
      }

      // Adding this since this useEffect can get triggered again by other
      // async dependency objects.
      if (!initialShow) {
        setSelectedSynapse(existingWebhookNode?.configuration?.configId);
      }
      setInitialShow(true);
    }
  }, [connectorIdToMetadataMap, enabled, initialShow, nodes, version, visible]);

  const onClose = useCallback(() => {
    setVisible(false);
  }, [setVisible]);

  const filterOption = useCallback(
    (input: string, option: React.ReactElement<OptionProps>) =>
      (option.props.title?.toLowerCase() || '').indexOf(input.toLowerCase()) >= 0,
    []
  );

  const onSave = useCallback(() => {
    setShowValidation(false);

    if (!selectedSynapse) {
      setShowValidation(true);
      return;
    }

    const page = editor?.getCurrentPage();
    const editorNodes = page?.getNodes();
    const coreNode = editorNodes?.find((node: any) => node.model.nodeType === AppConstants.NODE_TYPE.CORE_ENTITY);

    const entity = connectorEntities?.[selectedSynapse]?.data;
    // Entity and core node sanity checks
    if (!entity || !coreNode) {
      return;
    }

    const sourceNodes = editorNodes.filter((node: any) => node.model.nodeType === AppConstants.NODE_TYPE.ENTITY_SOURCE);
    const existingWebhookNode = nodes.find((node: any) => node.configuration?.isRealtimeWebhookSource);

    let updateNodes = [];

    const synapse = connectors.find((connector) => connector.id === selectedSynapse);

    let newEdges = [...edges];
    if (existingWebhookNode) {
      updateNodes = nodes.map((node) => {
        if (node.id !== existingWebhookNode.id) {
          return node;
        }

        return {
          ...node,
          name: synapse?.displayName,
          label: tn('sync_from_display_name', { displayName: synapse?.displayName }),
          apiName: synapse?.displayName,
          subLabel: synapse?.displayName,
          configuration: {
            ...node.configuration,
            configId: selectedSynapse,
            connectorId: selectedSynapse,
            entityDefinition: entity?.[0]?.id,
            isRealtimeWebhookSource: true,
          },
        };
      });
    } else {
      const sourceId = ObjectID.generate();

      const webhookNode = {
        id: sourceId,
        name: synapse?.displayName,
        nodeType: AppConstants.NODE_TYPE.ENTITY_SOURCE,
        configuration: {
          nodeType: AppConstants.NODE_TYPE.ENTITY_SOURCE,
          configId: selectedSynapse,
          connectorId: selectedSynapse,
          entityDefinition: entity?.[0]?.id,
          isRealtimeWebhookSource: true,
        },
        // Stack the source nodes with 3 box height spaces between and 3 box width to the left
        location: {
          x: 350 + coreNode.model.x - coreNode.bbox.width * 3,
          y: coreNode.model.y - coreNode.bbox.height * 3 * (sourceNodes.length || 1),
        },
      };

      updateNodes = [...nodes, webhookNode];

      newEdges.push({
        id: ObjectID.generate(),
        source: {
          nodeId: sourceId,
          port: {
            portType: 'OUTPUT',
            datatype: 'object',
            maxConnections: 2147483647,
          },
          anchor: '1',
        },
        destination: {
          nodeId: coreNode.id,
          port: {
            portType: 'INPUT',
            datatype: 'object',
            maxConnections: 1,
          },
          anchor: '3',
        },
      });
    }

    saveChanges(updateNodes, newEdges, undefined, {
      realtimePipeline: true,
      realtimeIpWhitelist: ipWhitelist,
    }).then(() => {
      dispatch(getEntityPipeline(entityId, version));
    });

    setEnabled(true);
    onClose();
  }, [
    connectorEntities,
    connectors,
    dispatch,
    edges,
    editor,
    entityId,
    ipWhitelist,
    nodes,
    onClose,
    saveChanges,
    selectedSynapse,
    setEnabled,
    version,
  ]);

  return (
    <Modal
      title={tn('title')}
      className={cx('realtime-pipeline-modal')}
      centered
      visible={visible}
      footer={
        webhookSynapse.length ? (
          <>
            <Button key="cancel" onClick={onClose}>
              {tc('cancel')}
            </Button>
            <Button key="ok" type="primary" onClick={onSave} disabled={version !== AppConstants.GRAPH_STATUS.NEW}>
              {tc('save')}
            </Button>
          </>
        ) : (
          <Button key="ok" type="primary" onClick={onClose}>
            {tc('close')}
          </Button>
        )
      }
      onOk={() => onClose()}
      onCancel={() => onClose()}
      destroyOnClose>
      <div className="realtime-pipeline-modal__content">
        {visible && webhookSynapse?.length ? (
          <Stack spacing="md">
            <InlineMessage allowMultiline initallyExpanded type="info">
              {tn('description')}
            </InlineMessage>
            <InputWithLabel
              name="webhookSynapse"
              label={tn('webhook_entity')}
              input={
                <Form.Item
                  validateStatus={showValidation && !selectedSynapse ? 'error' : undefined}
                  help={showValidation && !selectedSynapse ? tn('webhook_entity_required') : undefined}>
                  <Select
                    style={{ width: '100%' }}
                    onChange={(synapse: string) => {
                      setSelectedSynapse(synapse);
                    }}
                    value={selectedSynapse}
                    showSearch
                    disabled={version !== AppConstants.GRAPH_STATUS.NEW}
                    dropdownMatchSelectWidth
                    filterOption={filterOption}
                    optionFilterProp="title">
                    {connectorOptions}
                  </Select>
                </Form.Item>
              }
            />
            <InputWithLabel
              label={tn('endpoint')}
              name="endpoint"
              input={
                <CopyableInput
                  value={
                    Boolean(settings?.realtimeEndpointBase && settings?.realtimeEndpointSuffix)
                      ? `${settings?.realtimeEndpointBase}${settings?.realtimeEndpointSuffix}`
                      : ''
                  }
                />
              }
            />
            <InputWithLabel
              label={tn('ip_whitelist')}
              name="ipWhitelist"
              datatype="textarea"
              defaultValue={ipWhitelist}
              onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
                setIpWhitelist(evt.target.value);
              }}
            />
          </Stack>
        ) : (
          <EmptyPanelContent body={tn('empty_webhook_synapse')} />
        )}
      </div>
    </Modal>
  );
};

export default RealtimePipelineModal;
