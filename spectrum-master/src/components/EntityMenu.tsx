import { navigate } from '@reach/router';
import { Menu, message, Modal, Tooltip } from 'antd';

import { useEnhancedDispatch as useDispatch } from 'hooks/redux';
import { useCurrentSyncStudioRootTab } from 'pages/sync-studio/entity/SyncStudioRootTabs';
import { deleteEntity } from 'store/entity/actions';
import { useEntity } from 'store/entity/selectors';
import { showFastMapper } from 'store/fast-mapper/slice';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { navigateToEntity } from './Entity.utils';
import KebabMenu from './KebabMenu';
import { Text } from './typography';

const tn = tNamespaced('EntityEditor');

interface EntityMenuProps {
  entityId: string;
  onEntityCardClick: () => void;
}

const EntityMenu = ({ entityId, onEntityCardClick }: EntityMenuProps) => {
  const entity = useEntity(entityId);
  const dispatch = useDispatch();

  const { currentTab } = useCurrentSyncStudioRootTab();

  if (!entity) {
    return null;
  }

  const removeDisabled = entity?.pipelineStatus !== AppConstants.SYNCARI_NODE_STATUS.UNMAPPED;

  return (
    <div
      onClick={(evt) => {
        evt.preventDefault();
        evt.stopPropagation();
      }}>
      <KebabMenu
        menuItems={[
          <Menu.Item key={`map${entityId}`} onClick={() => navigateToEntity(entityId, entity?.pipelineStatus)}>
            <Text>{tn('edit_pipeline')}</Text>
          </Menu.Item>,
          <Menu.Item
            key={`mapUnmappedFields${entityId}`}
            onClick={() => {
              onEntityCardClick();
              dispatch(showFastMapper({ visible: true, entityId }));
            }}>
            <Text>{tn('manage_field_mappings')}</Text>
          </Menu.Item>,
          <Menu.Item
            key={`remove${entityId}`}
            disabled={removeDisabled}
            onClick={() => {
              Modal.confirm({
                title: tn('confirm_entity_delete_title'),
                content: tn('confirm_entity_delete_description', { entityName: entity.displayName }),
                okText: tc('remove'),
                cancelText: tc('cancel'),
                onOk: () => {
                  (dispatch(deleteEntity(entityId, true)) as any).then((resp: any) => {
                    if (resp.success) {
                      navigate(makeUrl(RouteConstants.ENTITIES, { tabId: currentTab }));
                    } else {
                      message.error(
                        <>
                          {tn('delete_entity_failed', { entityName: entity.displayName })}
                          <br />
                          {resp?.error?.errorMessage}
                        </>
                      );
                    }
                  });
                },
              });
            }}>
            <Tooltip placement="right" title={removeDisabled && tn('cannot_remove')}>
              <Text>{tn('remove')}</Text>
            </Tooltip>
          </Menu.Item>,
        ]}
      />
    </div>
  );
};

export default EntityMenu;
