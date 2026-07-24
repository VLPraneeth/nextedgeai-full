//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Tooltip } from 'antd';
import cx from 'classnames';
import { isUndefined } from 'lodash';
import * as React from 'react';
import { useMemo, useRef, useState } from 'react';

import Icon from 'components/icons/Icon';
import { usePipelineEditorV2Enabled } from 'pages/sync-studio/utils/usePipelineEditorV2Enabled';
import { ConnectorMetadata } from 'reducers/connectorReducer';
import { useConnectorIdToMetadataMap } from 'store/connectors';
import { DraftStatuses } from 'store/insights-studio/types';
import { connectorIsCustomDraftOrPublished } from 'utils/ConnectorUtil';

import { TextTag } from './text-tag';

import './GraphItems.less';

// TODO: Add stronger types
// refactor the pipeline editors to consolidate this
export interface GraphItemModel {
  className?: string;
  title?: string;
  name?: string;
  description?: string;
  entityDirection?: string;
  draggable?: boolean;
  attributeDirection?: string;
  entityName?: string;
  entityId?: string;
  key?: string;
  attributeId?: string;
  attributeName?: string;
  connectorType?: string;
  connectorEntityName?: string;
  connectorEntityId?: string;
  nodeId?: string;
  configId?: string;
  id?: string;
  functionId?: string;
  iconUrl?: string;
  hideLeftStrip?: string;
  fragment?: any;
  disabledMessage?: string;
  blankDraggable?: boolean;
  status?: string;
  shape?: string;
  label?: string;
  subLabel?: string;
  typeColor?: string;
  tooltipMessage?: string;
  nodeType?: string;
  selectable?: string;
  noicon?: boolean;
  suffix?: React.ReactElement | null;
  icon?: React.ReactElement | string;
  iconAlt?: string;
  custom?: boolean;
  draftStatus?: DraftStatuses;
}

export interface GraphItemsProps {
  items?: GraphItemModel[];
  selectable?: boolean;
  className?: string;
  itemType?: string;
  hasPermissionToDrag?: boolean;
}

const GraphItems = ({ items, selectable, className, itemType, hasPermissionToDrag }: GraphItemsProps) => {
  const itemRef = useRef(null);
  const [selectedKey, setSelectedKey] = useState('');

  const connectorIdMap = useConnectorIdToMetadataMap();

  const pipelineV2Enabled = usePipelineEditorV2Enabled();

  const onDragStart = React.useCallback(
    (evt: React.DragEvent<HTMLDivElement>, item: GraphItemModel) => {
      if (pipelineV2Enabled && itemType !== 'synapse') {
        evt.currentTarget.classList.add('synri-node-dragging-v2');

        // Clone the node to create drag image
        const dragImage = evt?.currentTarget?.cloneNode(true) as HTMLElement;

        if (dragImage) {
          dragImage.style.background = 'white';
          dragImage.style.position = 'absolute';
          dragImage.style.top = '-9999px';

          document.body.appendChild(dragImage);

          evt.dataTransfer.setDragImage(dragImage, 158, 25); // Half of the item's width and height

          evt.currentTarget.classList.remove('synri-node-dragging-v2');
        }

        evt.dataTransfer.setData(
          'graph-node',
          JSON.stringify({
            displayName: item.title,
            name: item.name,
            nodeType: item.nodeType,
            iconUrl: item.iconUrl,
            id: item.id,
            configId: item.configId,
          })
        );
        evt.dataTransfer.effectAllowed = 'move';
        if (evt.currentTarget?.parentElement?.dataset) {
          evt.dataTransfer.setData('graph-object', JSON.stringify(evt.currentTarget.parentElement.dataset));
        }
      } else {
        evt.currentTarget.classList.add('synri-node-dragging');
        evt.dataTransfer.setData('graph-node', 'graph-node');
        if (evt.currentTarget?.parentElement?.dataset) {
          evt.dataTransfer.setData('graph-object', JSON.stringify(evt.currentTarget.parentElement.dataset));
        }
      }
    },
    [pipelineV2Enabled, itemType]
  );

  const onDragEnd = (evt: React.DragEvent<HTMLDivElement>) => {
    evt.currentTarget.classList.remove('synri-node-dragging');
  };

  return useMemo(() => {
    const flowItemSelected = (evt: React.MouseEvent<HTMLDivElement>) => {
      if (!selectable) {
        return;
      }
      const itemKey = evt.currentTarget.getAttribute('data-key');
      itemKey && setSelectedKey(itemKey);
    };

    return (
      <>
        {items?.map((item) => {
          const connectorMetadata = (connectorIdMap[(item as any).connectorId] || item) as ConnectorMetadata;
          const customDraftOrPublished = connectorIsCustomDraftOrPublished(connectorMetadata);
          let customDraftStatusTag = null;
          if (customDraftOrPublished) {
            if (customDraftOrPublished === 'DRAFT') {
              customDraftStatusTag = <TextTag text="Draft" color="orange" />;
            } else {
              customDraftStatusTag = <TextTag text="Published" color="blue" />;
            }
          }

          const flowItemContainerClassNames = cx('flow-item-container', className, item?.className, {
            selected: selectable && String(selectedKey) === String(item.key),
          });

          let { draggable } = item;
          if (isUndefined(draggable)) {
            draggable = true;
          }

          if (!isUndefined(hasPermissionToDrag)) {
            draggable = hasPermissionToDrag;
          }

          const description = item.description || '';
          const data: Record<string, string | undefined | boolean> = {};
          if (item.entityDirection) {
            data['data-entity-direction'] = item.entityDirection;
          }
          if (item.attributeDirection) {
            data['data-attribute-direction'] = item.attributeDirection;
          }
          if (item.entityId) {
            data['data-entity-id'] = item.entityId;
            data['data-id'] = item.key;
          }
          if (item.attributeId) {
            data['data-attribute-id'] = item.attributeId;
            data['data-id'] = item.key;
          }
          if (item.attributeName) {
            data['data-attribute-name'] = item.attributeName;
          }
          if (item.connectorType) {
            data['data-connector-type'] = item.connectorType;
          }
          if (item.entityName) {
            data['data-entity-name'] = item.entityName;
          }
          if (item.connectorEntityName) {
            data['data-connector-entity-name'] = item.connectorEntityName;
          }
          if (item.connectorEntityId) {
            data['data-connector-entity-id'] = item.connectorEntityId;
          }
          if (item.nodeType) {
            data['data-node-type'] = item.nodeType;
          }
          if (item.nodeId) {
            data['data-node-id'] = item.nodeId;
          }
          if (item.configId) {
            data['data-config-id'] = item.configId;
          }
          if (item.id) {
            data['data-id'] = item.id;
          }
          if (item.functionId) {
            data['data-function-id'] = item.functionId;
          }
          if (item.iconUrl) {
            data['data-icon-url'] = item.iconUrl;
          }
          if (item.hideLeftStrip) {
            data['data-hide-left-strip'] = item.hideLeftStrip;
          }
          if (item.fragment) {
            data['data-fragment'] = true;
          }
          if (item.status) {
            data['data-status'] = item.status;
          }
          const shape = item.shape || 'standard-entity';
          if (draggable) {
            let blankDraggable = item.blankDraggable;

            draggable = item.disabledMessage ? false : draggable;
            blankDraggable = item.disabledMessage ? false : item.blankDraggable;

            const cls = cx('flow-item', {
              getItem: draggable,
              'synri-graph-item-disabled': !draggable,
            });

            let itemContent;
            if (item.subLabel) {
              itemContent = (
                <div className="synri-graph-item-content-container">
                  <span className="flow-item-title" title={item.title || ''}>
                    {item.title}
                  </span>
                  <span className="flow-item-sub-label">{item.subLabel}</span>
                </div>
              );
            } else {
              itemContent = (
                <span className="flow-item-title" title={item.title || ''}>
                  {item.title}
                </span>
              );
            }

            return (
              <div
                className={cls}
                key={item.key || item.id}
                ref={itemRef}
                data-key={item.key}
                data-shape={shape}
                data-type="node"
                data-size="220*52"
                data-label={item.title}
                data-description={description}
                data-type-color={item.typeColor}
                data-noicon={item.noicon}
                data-selectable={item.selectable}
                onDragStart={(evt) => onDragStart(evt, item)}
                draggable={(pipelineV2Enabled && itemType !== 'synapse') || blankDraggable}
                onDragEnd={onDragEnd}
                {...data}>
                <Tooltip title={item.disabledMessage || item.tooltipMessage}>
                  <div className={flowItemContainerClassNames}>
                    {item.icon}
                    {itemContent}
                    {customDraftStatusTag}
                    {item.suffix}
                  </div>
                </Tooltip>
              </div>
            );
          } else if (selectable) {
            return (
              <div
                className="flow-item"
                key={item.key || item.id}
                onClick={flowItemSelected}
                ref={itemRef}
                data-key={item.key}>
                <div className={flowItemContainerClassNames}>
                  <Icon className="flow-item-prefix" src={item.icon as string} alt={item.iconAlt} />
                  <span className="flow-item-title" title={item.title || ''}>
                    {item.title}
                  </span>
                  {customDraftStatusTag}
                  {item.suffix}
                </div>
              </div>
            );
          } else {
            return (
              <div className="flow-item" key={item.key || item.id} ref={itemRef} data-key={item.key}>
                <div className={flowItemContainerClassNames}>
                  {item.icon}
                  <span className="flow-item-title" title={item.title || ''}>
                    {item.title}
                  </span>
                  {customDraftStatusTag}
                  {item.suffix}
                </div>
              </div>
            );
          }
        })}
      </>
    );
  }, [
    items,
    selectable,
    connectorIdMap,
    className,
    selectedKey,
    hasPermissionToDrag,
    onDragStart,
    pipelineV2Enabled,
    itemType,
  ]);
};

export default GraphItems;
