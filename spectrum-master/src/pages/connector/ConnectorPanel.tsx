//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Dropdown, Menu } from 'antd';
import { map, sortBy } from 'lodash';
import { useState } from 'react';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import { IconButton } from 'components/Button';
import Fieldset from 'components/Fieldset';
import GraphItems, { GraphItemModel } from 'components/GraphItems';
import { getIconFromPath } from 'components/icons/Icons';
import { Stack } from 'components/layout';
import PanelFilter from 'components/PanelFilter';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { PermissionsComparisonOperator, useUserHasPermission } from 'hooks/useUserHasPermission';
import { ConnectorMetadata } from 'reducers/connectorReducer';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { filterItems } from 'utils/StringUtil';
import { UserflowTags } from 'utils/UserflowTags';

import { useConnectorDetailsContext } from './ConnectorDetailsContext';

import './ConnectorPanel.less';

const tn = tNamespaced('ConnectorPanel');

enum ConnectorPanelOptions {
  help = 'help',
  viewDetails = 'viewDetails',
  defaultMappings = 'defaultMappings',
}

const ConnectorPanel = ({ connectorMetadata }: { connectorMetadata: ConnectorMetadata[] }) => {
  const [filterText, setFilterText] = useState('');
  const { userHasPermission } = useUserHasPermission(PermissionsComparisonOperator.AND);
  const { showConnectorDetails, showDefaultMappings } = useConnectorDetailsContext();

  const getSuffix = (meta: ConnectorMetadata) => {
    if (meta.helpUrl) {
      return (
        <Dropdown
          placement="bottomRight"
          overlay={
            <Menu
              onClick={({ key }) => {
                if (key === ConnectorPanelOptions.help && typeof meta.helpUrl === 'string') {
                  window.open(meta.helpUrl);
                } else if (key === ConnectorPanelOptions.viewDetails) {
                  showConnectorDetails(true, meta.configId);
                } else if (key === ConnectorPanelOptions.defaultMappings) {
                  showDefaultMappings(true, meta.configId);
                }
              }}>
              <Menu.Item key={ConnectorPanelOptions.help}>{tn('help')}</Menu.Item>
              <Menu.Item key={ConnectorPanelOptions.viewDetails}>{tc('summary')}</Menu.Item>
              <Menu.Item key={ConnectorPanelOptions.defaultMappings}>{tn('default_mappings')}</Menu.Item>
            </Menu>
          }
          trigger={['click']}>
          <IconButton className="synri-synapse-panel-kebab" icon={() => <KebabIcon />} />
        </Dropdown>
      );
    }
    return null;
  };

  const addGraphProperties = (connectorMetadata: ConnectorMetadata[]) =>
    map(connectorMetadata, (meta) => {
      const graphItem: GraphItemModel = {
        ...meta,
        description: meta.description || '',
        disabledMessage: meta.disabledMessage || '',
        title: meta.displayName || '',
        // Using the id here since we could have a draft and published custom
        // synapse, both with the same display name
        key: meta.id || meta.displayName || '',
        shape: AppConstants.GRAPH_NODE_SHAPES.CONNECTOR,
        iconUrl: meta.iconUri || '',
        status: AppConstants.CONNECTOR_STATUS.NEW,
      };

      if (meta.iconUri) {
        graphItem.icon = getIconFromPath(meta.iconUri);
      }
      if (meta.helpUrl) {
        graphItem.suffix = getSuffix(meta);
      }

      return graphItem;
    });

  const filteredConnectors = filterItems(connectorMetadata, filterText)
    // Remove the Imported Files synapse from the ConnectorPanel to avoid users
    // trying to add it twice
    .filter(({ hideFromSynapseList }) => !hideFromSynapseList);
  const graphProperties = addGraphProperties(filteredConnectors);
  const connectors = sortBy(graphProperties, (connector) => (connector as any).displayName?.toLowerCase());

  return (
    <Stack key="synapse-library-container">
      <div data-userflow-tag={UserflowTags.SynapseStudio.List}>
        <PanelFilter key="synapse-library-filter" onChange={(evt) => setFilterText(evt.currentTarget.value)} />
        <Fieldset key="synapse-library" title={tn('synapse_library_with_count', { count: filteredConnectors.length })}>
          <ScrollableArea>
            <GraphItems
              key="synapse-list"
              items={connectors}
              selectable={false}
              hasPermissionToDrag={userHasPermission([AllPermissions.WRITE_CONNECTOR, AllPermissions.TEST_CONNECTION])}
              itemType="synapse"
            />
          </ScrollableArea>
        </Fieldset>
      </div>
    </Stack>
  );
};

export default ConnectorPanel;
