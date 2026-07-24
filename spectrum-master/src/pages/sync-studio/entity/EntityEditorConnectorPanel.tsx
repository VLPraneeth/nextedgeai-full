//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Icon } from 'antd';
import { matchSorter } from 'match-sorter';
import * as React from 'react';
import { useCallback, useMemo, useState } from 'react';

import Fieldset from 'components/Fieldset';
import GraphItems from 'components/GraphItems';
import { getIconFromPath } from 'components/icons/Icons';
import PanelFilter from 'components/PanelFilter';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { useEnhancedDispatch } from 'hooks/redux';
import { Connector } from 'reducers/connectorReducer';
import { useConnectorMetadataMap } from 'store/connectors';
import { showConnectorEntityModal } from 'store/entity/actions';
import { tNamespaced } from 'utils/i18nUtil';

import './EntityEditorConnectorPanel.less';

const tn = tNamespaced('EntityEditorConnectorPanel');

export interface ExtendedConnector extends Connector {
  name: string;
  typeDisplayName: string;
  typeName: string;
  icon?: string;
  iconAlt: string;
  backgroundColor: string;
}

export interface EntityEditorConnectorPanelProps {
  connectors: ExtendedConnector[];
}

const EntityEditorConnectorPanel = ({ connectors }: EntityEditorConnectorPanelProps) => {
  const [filterText, setFilterText] = useState('');

  const dispatch = useEnhancedDispatch();
  const connectorsMetadataMap = useConnectorMetadataMap();

  const onSearch = useCallback((evt: React.ChangeEvent<HTMLInputElement>) => {
    setFilterText(evt.currentTarget.value);
  }, []);

  const onSettingsClick = useCallback(
    (item: ExtendedConnector) => {
      dispatch(
        showConnectorEntityModal(true, {
          connectorId: item.connectorId,
          name: item.name,
        })
      );
    },
    [dispatch]
  );

  const enhancedConnectors = useMemo(() => {
    return connectors.map((item) => {
      const suffix = (
        <>
          <div className="flow-item-suffix">
            <Icon
              type="setting"
              theme="filled"
              onClick={(evt) => {
                evt.stopPropagation();
                onSettingsClick(item);
              }}
            />
          </div>
        </>
      );

      const metadata = connectorsMetadataMap[item.metadataId];

      return {
        ...item,
        suffix,
        icon: item.icon ? getIconFromPath(item.icon) : undefined,
        custom: metadata?.custom,
        draftStatus: metadata?.draftStatus,
      };
    });
  }, [connectors, connectorsMetadataMap, onSettingsClick]);

  const filteredConnectors: any = matchSorter(enhancedConnectors, filterText, { keys: ['title'] });
  const updatedFilteredConnectors: any = filteredConnectors?.map((item: any) => {
    const icon = item?.iconUri ? item.iconUri : undefined;
    return {
      ...item,
      ...(item?.name?.toLowerCase() === 'syncari' ? { title: item?.displayName } : {}),
      icon: icon ? getIconFromPath(icon) : item.icon,
    };
  });
  return (
    <>
      <PanelFilter onChange={onSearch} value={filterText} />
      <Fieldset title={tn('active_synapses', { connectorLength: connectors.length })}>
        <ScrollableArea>
          <GraphItems items={updatedFilteredConnectors} selectable={false} />
        </ScrollableArea>
      </Fieldset>
    </>
  );
};

export default EntityEditorConnectorPanel;
