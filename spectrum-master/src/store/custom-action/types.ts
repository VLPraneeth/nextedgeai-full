//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Variable } from 'components/custom-action/ActionVariable';
import { CustomAction } from 'components/custom-action/types';

export interface CustomActionState {
  customActionWizardVisible: boolean;
  customActionSharing: {
    visible: boolean;
    customActionId?: string;
  };
}

export type HeadersPayload = Record<string, string>;

export interface VariableServerPayload extends Omit<Variable, 'required'> {
  required?: boolean;
}

export interface VariablePayload extends Omit<Variable, 'required'> {
  required?: string;
}

export interface VariableValue extends Variable {
  value?: string;
}

export interface CustomActionPayload<TVariable = VariablePayload> extends Omit<CustomAction, 'actionConfiguration'> {
  body?: string;
  isBatch?: string;
  batchSize?: string;
  method?: string;
  endpoint?: string;
  credentialId?: string;
  metadataId?: string;
  shareWithOrg?: boolean;
  shareGlobally?: boolean;
  variables?: TVariable[];
  headers?: HeadersPayload;
  variableValues?: VariableValue[];
}

export interface VariableValue extends Pick<Variable, 'name'> {
  value?: string;
}

export interface CustomActionTestingPayload extends CustomActionPayload<VariablePayload> {
  variableValues?: VariableValue[];
}

export interface CustomActionTestingResponse {
  request: {
    httpStatusCode?: string;
    requestHeaders?: Record<string, string>;
    body: string;
    method: string;
    url: string;
  };
  response: {
    httpStatusCode?: string;
    responseHeaders?: Record<string, string>;
    body: string;
  };
}

export type SaveCustomActionResponse = any;
export interface SaveCustomActionRejected {
  message?: string;
}
