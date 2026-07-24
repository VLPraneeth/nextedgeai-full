import { Dropdown, Menu, message, Tooltip } from 'antd';
import Text from 'antd/lib/typography/Text';

import { ReactComponent as KebabIcon } from 'assets/icons/kebab.svg';
import { ReactComponent as GearIcon } from 'assets/icons/settings.svg';
import { IconButton } from 'components/Button';
import Can from 'components/Can';
import { HStack } from 'components/layout';
import Spinner from 'components/Spinner';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { useSynapseRefreshingStatus } from 'store/connectors';
import { post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import { tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import { makeUrl } from 'utils/UrlUtil';

interface Props {
  isSyncariConnector: boolean;
  toggleColumnsConfigureModal: () => void;
  connectorId?: string;
}

const tn = tNamespaced('SchemaStudio.EntityTable');

const EntitySchemaTableOptions = ({ isSyncariConnector, connectorId, toggleColumnsConfigureModal }: Props) => {
  const { isRefreshing, unableToUpdate } = useSynapseRefreshingStatus(connectorId);
  const { userHasPermission } = useUserHasPermission();

  if (isSyncariConnector) {
    return (
      <IconButton
        icon={GearIcon}
        onClick={toggleColumnsConfigureModal}
        disabled={!userHasPermission(AllPermissions.WRITE_STUDIO)}
      />
    );
  }

  const getRefreshMenuItem = () => {
    if (isRefreshing) {
      return (
        <Menu.Item disabled>
          <HStack justify="space-between">
            <span>{tn('schema_refreshing')}</span>
            {isRefreshing && <Spinner iconProps={{ style: { fontSize: 18, color: 'gray' } }} />}
          </HStack>
        </Menu.Item>
      );
    }

    if (unableToUpdate) {
      return (
        <Menu.Item disabled>
          <Tooltip placement="left" title={tn('cannot_refresh_until_active')}>
            <Text disabled>{tn('refresh_schema')}</Text>
          </Tooltip>
        </Menu.Item>
      );
    }

    return <Menu.Item key="refresh_schema">{tn('refresh_schema')}</Menu.Item>;
  };

  return (
    <Dropdown
      overlay={
        <Menu
          onClick={({ key }) => {
            if (connectorId && key === 'refresh_schema' && !isRefreshing) {
              // This is a fire and forget situation since all
              // updates are sent back via websockets.
              // Final success/failure messages are posted to the
              // notification drawer
              post(makeUrl(DataUrlConstants.REFRESH_SCHEMA, { connectorId }))
                .then(() => {
                  message.success(tn('refresh_schema_began'));
                })
                .catch(() => {
                  message.error(tn('refresh_schema_failed'));
                });
            } else if (key === 'configure_columns') {
              toggleColumnsConfigureModal();
            }
          }}>
          {getRefreshMenuItem()}
          <Can key="configure_columns" permission={AllPermissions.WRITE_STUDIO}>
            <Menu.Item>{tn('configure_columns')}</Menu.Item>
          </Can>
        </Menu>
      }
      trigger={['click']}>
      <IconButton className="synri-synapse-list-menu" icon={() => <KebabIcon />} />
    </Dropdown>
  );
};

export default EntitySchemaTableOptions;
