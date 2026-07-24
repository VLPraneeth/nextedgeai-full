//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Icon, Modal } from 'antd';
import cx from 'classnames';
import { each, first, sortBy } from 'lodash';
import * as React from 'react';
import { useCallback, useMemo, useState } from 'react';

import GraphItems from 'components/GraphItems';
import InputFilter from 'components/InputFilter';
import { HStack } from 'components/layout';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { filterItems } from 'utils/StringUtil';

import { GraphItemModel } from './GraphItems';

import './GraphItemFilter.less';

const tn = tNamespaced('GraphItemFilter');

// TODO: Extend this for the pipeline editors
export interface EntityPipelineModel {
  draft: EntityPipelineModel;
  id: string;
  nodes: NodeModel[];
}

export interface ConnectorConfigModel {
  entityDefinition: string;
  connectorId: string;
  webhook?: boolean;
}

export interface ConnectorModel {
  id: string;
  name: string;
  nodeType: typeof AppConstants.NODE_TYPE.CONNECTOR_ENTITY;
  configId: string;
  connectorEntityName: string;
  suffix?: React.ReactNode;
}

const isConnectorModel = (variableToCheck: any): variableToCheck is ConnectorModel => {
  return variableToCheck?.nodeType === AppConstants.NODE_TYPE.CONNECTOR_ENTITY;
};

export interface ConnectorFieldModel {
  id: string;
  name: string;
}

export interface NodeMetadataModel {
  name: string;
  configuration?: any;
  [k: string]: string;
}

export interface NodeModel {
  id: string;
  nodeType: string;
  name: string;
  label?: string;
  subLabel?: string;
  apiName?: string;
  configuration: ConnectorConfigModel;
  metadata: NodeMetadataModel;
  connectorEntityName?: string;
}

export interface GraphItemFilterProps {
  className?: string;
  items: NodeModel[] | GraphItemModel[];
  filterPlaceHolder?: string;
  entityPipeline?: EntityPipelineModel;
  connectors?: ConnectorModel[];
  showConnectorFieldModal?: (visible: boolean, params: {}) => void;
  createHandler?: () => void;
  graphItemClassName?: string;
  filterChildren?: React.ReactChildren | React.ReactChild;
  graphItemType?: string;
}

const GraphItemFilter = ({
  className,
  items,
  filterPlaceHolder,
  entityPipeline,
  connectors,
  showConnectorFieldModal,
  createHandler,
  graphItemClassName,
  filterChildren,
  graphItemType,
}: GraphItemFilterProps) => {
  const [filterText, setFilterText] = useState('');

  const _onSearch = (evt: React.ChangeEvent<HTMLInputElement>) => {
    setFilterText(evt.currentTarget.value);
  };

  const onSettingsClick = useCallback(
    (item: ConnectorModel, evt: React.MouseEvent<HTMLElement>) => {
      const connectorId = item.configId;
      const connectorName = item.connectorEntityName;

      var graphDraftId = '';
      let nodes: NodeModel[] = [];

      if (entityPipeline) {
        if (entityPipeline.draft) {
          graphDraftId = entityPipeline.draft.id;
          nodes = entityPipeline.draft.nodes;
        } else {
          graphDraftId = entityPipeline.id;
          nodes = entityPipeline.nodes;
        }
      }

      const isSynapseNode = (node: NodeModel) =>
        node.nodeType === AppConstants.NODE_TYPE.ENTITY_SOURCE && node.configuration.connectorId === connectorId;

      const synapseNode = nodes.find(isSynapseNode);
      const synapseNodes = nodes.filter(isSynapseNode);

      const syncariNode = nodes.find((node: NodeModel) => node.nodeType === AppConstants.NODE_TYPE.CORE_ENTITY);
      const connectorList: ConnectorFieldModel[] = [];

      each(connectors, (conn) => {
        const { configId, connectorEntityName } = conn;
        if (connectorId !== configId) {
          connectorList.push({
            id: configId,
            name: connectorEntityName,
          });
        }
      });

      if (synapseNode && syncariNode && connectorName) {
        const entityName = synapseNode.name;

        showConnectorFieldModal &&
          showConnectorFieldModal(true, {
            syncariEntityId: syncariNode.configuration.entityDefinition,
            synapseEntityId: synapseNode.configuration.entityDefinition,
            synapseNodes,
            connectors: connectorList,
            graphDraftId,
            connectorName,
            entityName,
          });
      } else {
        Modal.info({
          cancelText: tc('cancel'),
          content: tn('synapse_not_used'),
          centered: true,
          okText: tc('ok'),
        });
      }

      evt.stopPropagation();
    },
    [connectors, entityPipeline, showConnectorFieldModal]
  );

  // TODO: Refactor this to require graph item filter users
  // to provide the item suffixes and other content of graph items.
  const filteredItems = useMemo(() => {
    const filteredItems = filterItems<NodeModel | GraphItemModel>(items, filterText);
    const firstItem = first(items as ArrayLike<NodeModel>);

    // This is naiively assuming that the `items` array is homogenous
    if (firstItem && isConnectorModel(firstItem)) {
      // force cast the items array to ConnectorModel[].
      //
      // TODO: `_getFilteredItems` and `filterItems` need to be updated to accept a more generic
      // interface
      each(filteredItems as ConnectorModel[], (item) => {
        !item.suffix &&
          (item.suffix = (
            <div className="flow-item-suffix">
              <Icon type="setting" theme="filled" onClick={(evt) => onSettingsClick(item, evt)} />
            </div>
          ));
      });
    }
    return sortBy(filteredItems, 'title');
  }, [filterText, onSettingsClick, items]);

  return (
    <div className={cx('synri-graph-item-filter', className)}>
      <HStack className="synri-graph-item-filter__input-wrapper" align="center">
        <InputFilter onChange={_onSearch} placeholder={filterPlaceHolder} filterChildren={filterChildren} />
        {createHandler && (
          <Button type="primary" onClick={createHandler}>
            {tn('new')}
          </Button>
        )}
      </HStack>

      <ScrollableArea>
        <GraphItems className={graphItemClassName} items={filteredItems} itemType={graphItemType} />
      </ScrollableArea>
    </div>
  );
};

export default GraphItemFilter;
