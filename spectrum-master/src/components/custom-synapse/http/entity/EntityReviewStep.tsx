import { useMatch } from '@reach/router';
import { Button, message } from 'antd';
import { isNil } from 'lodash';
import { useCallback } from 'react';
import { UnControlled as CodeMirror } from 'react-codemirror2';
import { createPortal } from 'react-dom';

import Can from 'components/Can';
import ActionVariable from 'components/custom-action/ActionVariable';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { Stack } from 'components/layout';
import { SkullRenderTypeBaseProps } from 'components/quick-start-install-resolve-issue/QuickStartInstallResolveIssue.types';
import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID, useSkullConfigContext } from 'components/skull';
import { Text } from 'components/typography';
import { entitiesItemPath } from 'pages/connector/custom-synapse/CustomSynapseBreadcrumb';
import {
  useCreateHttpCustomSynapseEntityMutation,
  useGetHttpCustomSynapseEntityPaginationQuery,
  useUpdateHttpCustomSynapseEntityItemMutation,
} from 'store/custom-synapse/http/api';
import { useGetAllCustomSynapseListQuery } from 'store/custom-synapse/sdk/api';
import { HTTPCustomSynapseEntity } from 'store/custom-synapse/types';
import { getCodeMirrorOptions } from 'utils/CodeMirrorUtil';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tNamespaced, tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { getEntityRecordfields } from './EntityRecord';

const tn = tNamespaced('CustomSynapse.HttpCustomSynapse.Entities');

export interface EntityReviewStepProps extends SkullRenderTypeBaseProps {
  defaultValue: HTTPCustomSynapseEntity;
}

export const EntityReviewStep = ({ defaultValue: entity }: EntityReviewStepProps) => {
  const { close, previous } = useSkullConfigContext();

  const entityMatch = useMatch(entitiesItemPath);

  const { refetch } = useGetAllCustomSynapseListQuery();

  const { data: paginationOptions } = useGetHttpCustomSynapseEntityPaginationQuery();

  const [createEntity, { isLoading: creating }] = useCreateHttpCustomSynapseEntityMutation();
  const [updateEntity, { isLoading: updating }] = useUpdateHttpCustomSynapseEntityItemMutation();

  const handleSave = useCallback(async () => {
    try {
      if (entity.id && entity.metaId) {
        await updateEntity({
          body: entity,
          entityId: entity.id,
          metadataId: entity.metaId,
        }).unwrap();
      } else {
        await createEntity({
          body: entity,
          metadataId: entity.metaId || entityMatch?.synapseId!,
        }).unwrap();
      }
    } catch (err: any) {
      message.error(getRtkQueryErrorMessage(err) || tn('error_while_save'));
      return;
    }
    refetch();
    close();
  }, [close, createEntity, entity, entityMatch?.synapseId, refetch, updateEntity]);

  const footerRootNode = document.getElementById(SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
  if (!footerRootNode) {
    return null;
  }

  const footerPortal = createPortal(
    <>
      <Button onClick={close}>{tc('cancel')}</Button>

      <Button onClick={previous}>{tc('previous')}</Button>

      <Can key="save" permission={AllPermissions.WRITE_CONNECTOR}>
        <Button type="primary" loading={creating || updating} onClick={handleSave}>
          {tc('save')}
        </Button>
      </Can>
    </>,
    footerRootNode
  );

  const paginationOption = paginationOptions?.find((option) => option.name === entity.type);

  return (
    <Stack>
      <InputWithLabel label={tc('display_name')} input={<Text>{entity.displayName}</Text>} />
      <InputWithLabel label={tc('api_name')} input={<Text>{entity.apiName}</Text>} />
      {!!entity.description && <InputWithLabel label={tc('description')} input={<Text>{entity.description}</Text>} />}
      {!!entity?.tags?.length && <InputWithLabel label={tc('tags')} input={<Text>{entity.tags?.join(',')}</Text>} />}

      {!!entity.method && !!entity.endpoint && (
        <InputWithLabel
          label={tc('endpoint')}
          input={
            <Text>
              {entity.method}: {entity.endpoint}
            </Text>
          }
        />
      )}

      {!!entity.body && (
        <InputWithLabel
          label={tc('body')}
          input={
            <Text>
              <CodeMirror className="code-mirror-container" value={entity.body} options={getCodeMirrorOptions(true)} />
            </Text>
          }
        />
      )}
      {!!Object.keys(entity.headers || {}).length && (
        <InputWithLabel
          label={tc('headers')}
          input={
            <Text>
              {Object.keys(entity.headers || {}).map((key) => {
                return (
                  <div>
                    <Text>
                      {key}: {entity.headers?.[key]}
                    </Text>
                  </div>
                );
              })}
            </Text>
          }
        />
      )}
      {!!entity.schema && (
        <InputWithLabel
          label={tc('schema')}
          input={
            <CodeMirror className="code-mirror-container" value={entity.schema} options={getCodeMirrorOptions(true)} />
          }
        />
      )}

      {getEntityRecordfields()
        .map(
          (field) =>
            !!entity?.[field.name] && (
              <InputWithLabel label={field.label} input={<Text>{entity[field.name] as string}</Text>} />
            )
        )
        .filter(Boolean)}

      {!!paginationOption?.displayName && (
        <InputWithLabel label={tn('pagination_type')} input={<Text>{paginationOption?.displayName}</Text>} />
      )}
      {paginationOption?.fields
        ?.map(
          (field) =>
            !isNil(entity?.[field.name]) && (
              <InputWithLabel label={field.label} input={<Text>{entity[field.name] as string}</Text>} />
            )
        )
        .filter(Boolean)}

      {!!entity.variables?.length && (
        <InputWithLabel
          label={tc('variables')}
          input={<ActionVariable defaultValue={entity.variables} onChange={() => {}} readOnly />}
        />
      )}

      {footerPortal}
    </Stack>
  );
};
