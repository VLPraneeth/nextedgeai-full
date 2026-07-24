//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { capitalize } from 'lodash';
import { useMemo } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { NavigateToStepHandler } from 'components/JumpToStepLabel/JumpToStepLabel';
import { ConfigFormValues } from 'components/skull';
import { SkullColumns, SkullColumnsType } from 'components/skull-columns';
import { useGetCredentialListQuery } from 'store/credential/api';
import AppConstants from 'utils/AppConstants';

export interface ActionReviewProps {
  formValues?: ConfigFormValues;
  navigateToStep: NavigateToStepHandler;
}

const { INPUT_DISPLAY_MODE } = AppConstants;

export const ActionReview = ({ formValues, navigateToStep }: ActionReviewProps) => {
  const { data: credentials } = useGetCredentialListQuery();

  const { tn, tc } = useI18nContext();
  const columns = useMemo(() => {
    const actionConfiguration = formValues?.['actionConfiguration']?.['value'];
    // @ts-ignore
    const endpoint = actionConfiguration?.['endpoint'];
    // @ts-ignore
    const authentication = actionConfiguration?.['authentication'];

    const hasTags = Boolean(formValues?.['tags']?.value);

    const hasBatch =
      // @ts-ignore
      actionConfiguration?.['body']?.['isBatch'] &&
      // @ts-ignore
      `${actionConfiguration?.['body']?.['isBatch']}` === AppConstants.TRUE;

    const actionBodyPreview =
      // @ts-ignore
      actionConfiguration?.['body'] && actionConfiguration?.['body']?.['bodyValue']
        ? // @ts-ignore
          actionConfiguration?.['body']
        : { bodyValue: tn('not_provided', { name: tn('body') }) };

    const columns = [
      {
        span: 14,
        items: [
          {
            id: 'settingsHeader',
            name: 'settingsHeader',
            renderType: 'jumpToStepLabel',
            navigateToStep,
            text: tn('basic_setttings'),
            buttonText: 'Edit',
            stepNumber: 0,
          },
          {
            id: 'displayNamePreview',
            name: 'displayNamePreview',
            datatype: 'string',
            label: tn('display_name'),
            value: formValues?.['displayName']?.value || tn('not_provided', { name: tn('display_name') }),
            tooltip: tn('display_name_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'descriptionPreview',
            name: 'descriptionPreview',
            datatype: 'richtext',
            label: tn('description'),
            beDangerous: true,
            value: formValues?.['description']?.value || tn('not_provided', { name: tn('description') }),
            tooltip: tn('description_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'apiNamePreview',
            name: 'apiNamePreview',
            datatype: 'string',
            label: tn('api_name'),
            beDangerous: true,
            value: formValues?.['apiName']?.value || tn('not_provided', { name: tn('api_name') }),
            tooltip: tn('api_name_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'tagPreview',
            name: 'tagPreview',
            datatype: hasTags ? 'tag' : 'string',
            label: tn('tags'),
            tooltip: tn('tags_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
            value: hasTags ? formValues?.['tags']?.value : tn('not_provided', { name: tn('tags') }),
          },
          {
            id: 'basicHelpTextPreview',
            name: 'basicHelpTextPreview',
            datatype: 'string',
            label: tn('basic_help_text'),
            beDangerous: true,
            value: formValues?.['basicHelpText']?.value || tn('not_provided', { name: tn('basic_help_text') }),
            tooltip: tn('basic_help_text_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'helpLinkPreview',
            name: 'helpLinkPreview',
            datatype: 'string',
            label: tn('help_link'),
            beDangerous: true,
            value: formValues?.['helpLink']?.value || tn('not_provided', { name: tn('help_link') }),
            tooltip: tn('help_link_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
        ],
      },
      {
        span: 10,
        items: [
          {
            id: 'actionSetupHeader',
            name: 'actionSetupHeader',
            renderType: 'jumpToStepLabel',
            navigateToStep,
            text: tn('action_setup'),
            buttonText: tc('edit'),
            stepNumber: 1,
          },
          {
            id: 'endpointPreview',
            name: 'endpointPreview',
            datatype: 'string',
            label: tn('endpoint'),
            // @ts-ignore
            value:
              endpoint?.['selectValue'] || endpoint?.['textValue']
                ? `${endpoint?.['selectValue']} ${endpoint?.['textValue']}`
                : tn('not_provided', { name: tn('endpoint') }),
            tooltip: tn('endpoint_description'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'authenticationPreview',
            name: 'authenticationPreview',
            datatype: 'string',
            label: tn('authentication'),
            value:
              credentials?.find(
                (credential) =>
                  // @ts-ignore
                  credential.id === authentication?.['credentialId']
              )?.name || tn('not_provided', { name: tn('authentication') }),
            tooltip: 'Authentication used for the custom action',
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'actionBatchingEnabled',
            name: 'actionBatchingEnabled',
            datatype: 'string',
            label: tn('enable_batching'),
            // @ts-ignore
            value: capitalize(actionConfiguration?.['body']?.['isBatch'] || AppConstants.FALSE),
            tooltip: tn('batch_enable_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'actionBatchSize',
            name: 'actionBatchSize',
            label: tn('batch_size'),
            datatype: 'string',
            // @ts-ignore
            value: actionConfiguration?.['body']?.['batchSize'] || tn('not_provided', { name: tn('batch_size') }),
            tooltip: tn('batch_size_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
          {
            id: 'actionBodyPreview',
            name: 'actionBodyPreview',
            renderType: 'actionBody',
            label: tn('body'),
            defaultValue: actionBodyPreview,
            tooltip: tn('body_tooltip'),
            displayMode: INPUT_DISPLAY_MODE.READONLY,
          },
        ],
      },
    ];

    // Filter out the Batch Size group in the review if batching is disabled.
    if (!hasBatch) {
      // @ts-ignore
      columns[1].items = columns[1].items.filter((item: any) => item.id !== 'actionBatchSize');
    }

    return columns;
  }, [credentials, formValues, navigateToStep, tc, tn]);

  return <SkullColumns columns={columns as SkullColumnsType} />;
};

export default withI18n(ActionReview, 'ActionSetup');
