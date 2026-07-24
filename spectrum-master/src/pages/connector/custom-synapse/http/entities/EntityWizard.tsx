//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps, useMatch } from '@reach/router';
import { useMemo } from 'react';

import { CustomSynapse } from 'components/custom-synapse/types';
import DrawerPanel from 'components/DrawerPanel';
import { HStack } from 'components/layout';
import Spinner from 'components/Spinner';
import { useGetHttpCustomSynapseEntityItemQuery } from 'store/custom-synapse/http/api';
import { tNamespaced } from 'utils/i18nUtil';

import { entitiesItemPath } from '../../CustomSynapseBreadcrumb';
import { EntityWizardContent } from './EntityWizardContent';

// TODO: These styles are used for all new wizards. Consider moving this less
// file to a higher level and making the naming more generic (not QS specific).
import '../../../../sync-studio/entity/quick-start/QuickStartWizard.less';

export interface EntityWizardProps extends RouteComponentProps {
  customSynapse: CustomSynapse | undefined;
  close?: () => void;
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export const EntityWizard = ({ close, customSynapse }: EntityWizardProps) => {
  const entityMatch = useMatch(entitiesItemPath);
  const isDraftRouteVersion = entityMatch?.version === 'draft';
  const isPublishedRouteVersion = entityMatch?.version === 'published';
  const isNewEntity = entityMatch?.entityId === 'new';
  const metaDataId = isDraftRouteVersion ? customSynapse?.id : customSynapse?.parentId || customSynapse?.id;

  const { data: entity, isFetching } = useGetHttpCustomSynapseEntityItemQuery(
    { entityId: entityMatch?.entityId!, metadataId: metaDataId! },
    {
      skip: !entityMatch?.entityId || isNewEntity,
    }
  );

  const panelTitle = useMemo(() => {
    if (isNewEntity) {
      return tn('create_entity');
    }
    if (isPublishedRouteVersion) {
      return tn('view_entity');
    }
    return tn('edit_entity');
  }, [isNewEntity, isPublishedRouteVersion]);

  const visible = !!entityMatch?.entityId || isNewEntity;

  return (
    <DrawerPanel
      className="synri-config-full-content"
      keyboard={false}
      maskClosable={false}
      noPadding
      onClose={close}
      destroyOnClose
      title={panelTitle}
      visible={visible}
      width="full">
      {isFetching || !visible ? (
        <HStack justify="center">
          <Spinner />
        </HStack>
      ) : (
        <EntityWizardContent entity={isNewEntity ? undefined : entity} close={close} />
      )}
    </DrawerPanel>
  );
};
