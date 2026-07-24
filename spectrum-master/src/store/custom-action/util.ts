//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import AppConstants from 'utils/AppConstants';

import { CustomActionPayload, VariablePayload, VariableServerPayload } from './types';

export const transformToCustomAction = (
  data: CustomActionPayload<VariableServerPayload>
): CustomActionPayload<VariablePayload> => ({
  ...data,
  isBatch: data?.isBatch ? AppConstants.TRUE : AppConstants.FALSE,
  variables: data?.variables?.map((variable) => {
    return {
      ...variable,
      multivalued: variable.multivalued ? AppConstants.TRUE : AppConstants.FALSE,
      required: variable.required ? AppConstants.TRUE : AppConstants.FALSE,
    };
  }),
});

export const transformToCustomActions = (
  data?: CustomActionPayload<VariableServerPayload>[]
): CustomActionPayload<VariablePayload>[] | undefined => data?.map(transformToCustomAction);
