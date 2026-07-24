import { navigate, useMatch } from '@reach/router';
import { Button, message } from 'antd';
import { useCallback, useMemo, useState } from 'react';

import Can from 'components/Can';
import { HStack } from 'components/layout';
import Spinner from 'components/Spinner';
import { Dropdown, Toolbar } from 'components/toolbar';
import { Text } from 'components/typography';
import {
  useCreateDraftHttpCustomSynapseMutation,
  useDiscardDraftHttpCustomSynapseMutation,
  usePublishHttpCustomSynapseMutation,
} from 'store/custom-synapse/http/api';
import { useDeleteCustomSynapseMutation, useGetCustomSynapseItemQuery } from 'store/custom-synapse/sdk/api';
import { CustomSynapseDraftStatuses, EntityRouteVersion } from 'store/custom-synapse/types';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { entitiesBasePath } from '../../CustomSynapseBreadcrumb';
import { HTTPSynapseConfigDrawer } from './HTTPSynapseConfigDrawer';

interface DropdownOption {
  id: EntityRouteVersion;
  name: string;
}

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export function EntitiesToolbar() {
  const entitiesMatch = useMatch(entitiesBasePath);

  const { data: customSynapse, isLoading: customSynapseLoading } = useGetCustomSynapseItemQuery(
    { connectorMetaDefinitionId: entitiesMatch?.synapseId },
    {
      skip: !entitiesMatch?.synapseId,
    }
  );

  const [createHTTPCustomSynapseDraft, { isLoading: creatingDraft }] = useCreateDraftHttpCustomSynapseMutation();

  const [publishHTTPSynapse, { isLoading: isPublishing }] = usePublishHttpCustomSynapseMutation();
  const [deleteHTTPSynapse, { isLoading: isDeleting }] = useDeleteCustomSynapseMutation();
  const [discardHTTPSynapseDraft, { isLoading: isDiscarding }] = useDiscardDraftHttpCustomSynapseMutation();

  const isPublished = customSynapse?.draftStatus === CustomSynapseDraftStatuses.APPROVED || !!customSynapse?.parentId;
  const isDraft = customSynapse?.draftStatus === CustomSynapseDraftStatuses.NEW;
  const isDraftRouteVersion = entitiesMatch?.version === 'draft';
  const [synapseConfigVisible, setSynapseConfigVisible] = useState(false);

  const handleOpenSynapseConfig = useCallback(() => {
    if (isDraftRouteVersion) {
      const url = makeUrl(
        RouteConstants.SYNAPSES_CUSTOM_ITEM,
        {
          synapseId: customSynapse?.id,
          synapseType: 'http',
        },
        {
          referrer: 'entities',
        }
      );
      navigate(url);
    } else {
      setSynapseConfigVisible(true);
    }
  }, [customSynapse?.id, isDraftRouteVersion]);

  const handleCreateDraft = useCallback(() => {
    if (customSynapse?.id) {
      createHTTPCustomSynapseDraft(customSynapse.id)
        .unwrap()
        .then((data) => {
          const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITIES, {
            synapseId: data.id,
            version: 'draft',
          });

          navigate(url);
        });
    }
  }, [createHTTPCustomSynapseDraft, customSynapse?.id]);

  const handlePublishSynapse = useCallback(() => {
    if (customSynapse?.id) {
      publishHTTPSynapse(customSynapse?.id)
        .unwrap()
        .then(() => {
          message.success(tn('publish_success'));
          const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM);

          navigate(url);
        });
    }
  }, [customSynapse?.id, publishHTTPSynapse]);

  const handleDiscardDraftSynapse = useCallback(() => {
    if (customSynapse?.id) {
      discardHTTPSynapseDraft(customSynapse?.id)
        .unwrap()
        .then(() => {
          const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM);

          navigate(url);
        });
    }
  }, [customSynapse?.id, discardHTTPSynapseDraft]);

  const handleDeleteSynapse = useCallback(() => {
    if (customSynapse?.id) {
      deleteHTTPSynapse({ connectorMetaDefinitionId: customSynapse?.id })
        .unwrap()
        .then(() => {
          const url = makeUrl(RouteConstants.SYNAPSES_CUSTOM);

          navigate(url);
        });
    }
  }, [customSynapse?.id, deleteHTTPSynapse]);

  const handleVersionChange = useCallback(
    (option: DropdownOption) => {
      let url = makeUrl(RouteConstants.SYNAPSES_CUSTOM_ENTITIES, {
        synapseId: entitiesMatch?.synapseId,
        version: option.id,
      });
      navigate(url);
    },
    [entitiesMatch?.synapseId]
  );

  const renderPublishOrDraft = useCallback(() => {
    if (isDraft && isDraftRouteVersion) {
      return (
        <Can key="publish" permission={AllPermissions.WRITE_CONNECTOR}>
          <Button loading={isPublishing} onClick={handlePublishSynapse} type="primary">
            {tc('publish')}
          </Button>
        </Can>
      );
    }

    if (!isDraft && !isDraftRouteVersion) {
      return (
        <Can key="create_draft" permission={AllPermissions.WRITE_CONNECTOR}>
          <Button loading={creatingDraft} onClick={handleCreateDraft} type="primary">
            {tc('create_draft')}
          </Button>
        </Can>
      );
    }
    return null;
  }, [creatingDraft, handleCreateDraft, handlePublishSynapse, isDraft, isDraftRouteVersion, isPublishing]);

  const renderDeleteButton = useCallback(() => {
    if (isDraft && isDraftRouteVersion) {
      return (
        <Can key="delete_draft" permission={AllPermissions.WRITE_CONNECTOR}>
          <Button loading={isDiscarding} onClick={handleDiscardDraftSynapse} type="link" className="delete-button">
            {tc('delete_draft')}
          </Button>
        </Can>
      );
    }
    if (!isDraftRouteVersion) {
      return (
        <Can key="delete_synapse" permission={AllPermissions.WRITE_CONNECTOR}>
          <Button loading={isDeleting} onClick={handleDeleteSynapse} type="link" className="delete-button">
            {tc('delete')}
          </Button>
        </Can>
      );
    }

    return null;
  }, [handleDeleteSynapse, handleDiscardDraftSynapse, isDeleting, isDiscarding, isDraft, isDraftRouteVersion]);

  const versionDropdownOptions: DropdownOption[] = useMemo(
    () => [
      {
        id: 'draft',
        name: tc('draft'),
      },
      {
        id: 'published',
        name: tc('published'),
      },
    ],
    []
  );

  const activeVersion = isDraftRouteVersion ? versionDropdownOptions[0] : versionDropdownOptions[1];

  if (customSynapseLoading) {
    return <Spinner />;
  }

  return (
    <Toolbar
      backToName={tn('custom_synapses')}
      onRequestBack={() => navigate(RouteConstants.SYNAPSES_CUSTOM)}
      className={isDraftRouteVersion ? 'draft entities-toolbar' : 'entities-toolbar'}
      leftChildren={
        <HStack spacing="md">
          <Text color="black" size="md" weight="regular">
            {customSynapse?.displayName}
          </Text>
          <Text size="md" color="gray-700">
            {tn('authentication_with_type', { type: customSynapse?.authType })}
          </Text>

          {isDraft && isPublished && (
            <Can key="version_dropdown" permission={AllPermissions.WRITE_CONNECTOR}>
              <Dropdown onChange={handleVersionChange} options={versionDropdownOptions} selected={activeVersion} />
            </Can>
          )}
        </HStack>
      }>
      <HStack spacing="md">
        {renderPublishOrDraft()}

        {isDraftRouteVersion ? (
          <Can key="edit_config" permission={AllPermissions.WRITE_CONNECTOR}>
            <Button onClick={handleOpenSynapseConfig}>{tc('edit_configuration')}</Button>
          </Can>
        ) : (
          <Button onClick={handleOpenSynapseConfig}>{tc('view_configuration')}</Button>
        )}

        {renderDeleteButton()}
      </HStack>

      <HTTPSynapseConfigDrawer
        synapse={customSynapse}
        visible={synapseConfigVisible}
        handleVisibleChange={setSynapseConfigVisible}
      />
    </Toolbar>
  );
}
