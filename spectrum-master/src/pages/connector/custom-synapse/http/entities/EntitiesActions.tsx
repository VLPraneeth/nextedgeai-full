import { navigate } from '@reach/router';
import { Button, Icon, Menu } from 'antd';
import { useCallback, useState } from 'react';

import { ReactComponent as OpenArrowIcon } from 'assets/icons/open-arrow.svg';
import Can from 'components/Can';
import KebabMenu from 'components/KebabMenu';
import Modal from 'components/Modal';
import Spinner from 'components/Spinner';
import { Text } from 'components/typography';
import { useDeleteCustomSynapseEntityMutation } from 'store/custom-synapse/http/api';
import { EntityRouteVersion, HTTPCustomSynapseEntityMeta } from 'store/custom-synapse/types';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { wrapIcon } from 'utils/IconUtils';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

interface EntitiesActionsProps {
  entity: (HTTPCustomSynapseEntityMeta & { routeVersion?: EntityRouteVersion; routeSynapseId?: string }) | undefined;
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export function EntitiesActions({ entity }: EntitiesActionsProps) {
  const [actionKebabOpen, setActionKebabOpen] = useState(false);
  const [deleteEntity, { isLoading: deletingEntity }] = useDeleteCustomSynapseEntityMutation();

  const isPublishedVersion = entity?.routeVersion === 'published';

  const confirmDeleteEntity = useCallback(() => {
    let content = tn('delete_confirmation', { name: entity?.displayName });
    if (entity?.usedInPublishedPipeline?.length) {
      content = tn('delete_confirmation_published_in_usage', { name: entity?.displayName });
    }
    if (entity?.usedInPipeline.length) {
      content = tn('delete_confirmation_in_usage', { name: entity?.displayName });
    }
    Modal.confirm({
      title: tn('delete_entity'),
      content,
      onOk: async () => {
        if (entity?.metaId && entity?.id) {
          deleteEntity({ entityId: entity?.id, metadataId: entity?.metaId });
        }
      },
      okText: tc('delete'),
      okType: 'danger',
      okButtonProps: { type: 'danger' },
    });
    setActionKebabOpen(false);
  }, [deleteEntity, entity]);

  const handleOpenEntity = useCallback(() => {
    if (entity?.routeSynapseId) {
      const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITY, {
        synapseId: entity?.routeSynapseId,
        entityId: entity?.id,
        version: entity?.routeVersion,
      });
      navigate(url);
      setActionKebabOpen(false);
    }
  }, [entity?.id, entity?.routeSynapseId, entity?.routeVersion]);

  return (
    <div className="custom-synapse__table-actions">
      {deletingEntity ? (
        <Spinner />
      ) : isPublishedVersion ? (
        <Button type="default" size="small" onClick={handleOpenEntity}>
          <Icon component={wrapIcon(OpenArrowIcon)} />
          {tc('view')}
        </Button>
      ) : (
        <KebabMenu
          menuItems={[
            <Can key="edit_entity" permission={AllPermissions.WRITE_CONNECTOR}>
              <Menu.Item key="edit_entity" onClick={handleOpenEntity}>
                <Text>{tc('edit')}</Text>
              </Menu.Item>
            </Can>,
            <Can key="delete_entity" permission={AllPermissions.WRITE_CONNECTOR}>
              <Menu.Item key="delete_entity" onClick={confirmDeleteEntity}>
                <Text color="red-300">{tc('delete')}</Text>
              </Menu.Item>
            </Can>,
          ]}
          visible={actionKebabOpen}
          onVisibleChange={setActionKebabOpen}
          onClick={() => setActionKebabOpen(false)}
          size="large"
        />
      )}
    </div>
  );
}
