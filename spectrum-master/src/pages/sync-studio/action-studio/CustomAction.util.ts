//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomAction } from 'components/custom-action/types';
import { HeadersPayload } from 'store/custom-action/types';

export const makeCustomActionPayload = (params: CustomAction, id?: string) => {
  const { actionConfiguration } = params;
  // we send headers as a map to backend but use it as an array on the frontend
  const headers = actionConfiguration?.headers?.reduce((acc, header) => {
    if (header.key) {
      // @ts-ignore
      acc[header.key] = header.value;
    }
    return acc;
  }, {}) as HeadersPayload;
  return {
    id,
    displayName: params.displayName,
    description: params.description,
    apiName: params.apiName,
    iconPath: params.iconPath,
    basicHelpText: params.basicHelpText,
    helpLink: params.helpLink,
    isBatch: actionConfiguration?.body?.isBatch,
    batchSize: actionConfiguration?.body?.batchSize,
    body: actionConfiguration?.body?.bodyValue,
    endpoint: actionConfiguration?.endpoint?.textValue,
    method: actionConfiguration?.endpoint?.selectValue,
    credentialId: actionConfiguration?.authentication?.credentialId,
    metadataId: actionConfiguration?.authentication?.metadataId,
    headers,
    variables: actionConfiguration?.variables,
  };
};
