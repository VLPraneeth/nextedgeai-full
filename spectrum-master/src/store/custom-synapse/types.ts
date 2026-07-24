//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { RcFile } from 'antd/lib/upload';

import { Variable } from 'components/custom-action/ActionVariable';
import { CustomSynapse } from 'components/custom-synapse/types';
import { InputDataType } from 'components/inputs/types';
import { PageInfo } from 'hooks/pagination';

export interface SDKCustomSynapseState {
  customSynapseSharePanel: {
    visible: boolean;
    customSynapse: CustomSynapse | null;
  };
  customSdkSynapseSharePanel: {
    customSynapse: CustomSynapse | null;
    visible: boolean;
  };
  customSynapseApprovalModal: {
    visible: boolean;
    customSynapse: CustomSynapse | null;
  };
}

export interface CustomSynapsePayload extends CustomSynapse {
  synapseName: string;
}

export enum SDKCustomSynapseFunctionDeployStatuses {
  ACTIVE = 'ACTIVE',
  DEPLOY_IN_PROGRESS = 'DEPLOY_IN_PROGRESS',
  DELETE_IN_PROGRESS = 'DELETE_IN_PROGRESS',
  ERROR = 'ERROR',
}

export enum CustomSynapseDraftStatuses {
  NEW = 'NEW',
  SUBMIT_FOR_APPROVAL = 'SUBMIT_FOR_APPROVAL',
  APPROVED = 'APPROVED',
  APPROVAL_IN_PROGRESS = 'APPROVAL_IN_PROGRESS',
  ARCHIVED = 'ARCHIVED',
}

export interface SDKCustomSynapseCreatePayload {
  connectorMetaDefinitionId?: string;
  connectorMetaName: string;
  connectorMetaDisplayName: string;
  publishToGlobal: boolean;
  synapseFile?: RcFile;
  requirementsFile?: RcFile;
  iconFile?: RcFile;
}

export type UpdateCustomSynapseResponse = any;
export type CustomSynapseCreateResponse = CustomSynapse;
export interface SaveCustomSynapseRejected {
  message?: string;
}

export interface TestConnectionResponse {
  message: string;
  code: string;
  errors: string[];
  authConfig: null;
  metaConfig: {};
  success: boolean;
}

export interface HTTPCustomSynapseEntityMeta {
  apiName: string;
  displayName: string;
  endpoint: string;
  id: string;
  method: string;
  updatedAt: string;
  updatedBy: string;
  usedInPipeline: { id: string; name: string }[];
  usedInPublishedPipeline?: { id: string; name: string }[];
  metaId?: string;
}

export type EntityPaginationType = 'NO_PAGINATION' | 'LIMIT_OFFSET' | 'PAGE_NUMBER' | 'CURSOR';

export type EntityRouteVersion = 'draft' | 'published';

export interface EntityPaginationItem {
  name: EntityPaginationType;
  displayName: string;
  fields: PaginationFields[];
}
export interface PaginationFields {
  dataType: InputDataType;
  helpSummary?: string;
  label: string;
  name: keyof HTTPCustomSynapseEntity;
  required: boolean;
  defaultValue?: any;
  options?: { value: string; label: string }[];
  visibilityCondition?: {
    field: keyof HTTPCustomSynapseEntity;
    value: string | number;
  };
}

export type HTTPCustomSynapseEntityTestingPayload = {
  metadataId: string;
  endpoint: string;
  method: string;
  body: string;
  headers: Record<string, string>;
  variables: Variable[];
  variableValues: {
    name?: string;
    value?: string;
  }[];
};

export type HTTPCustomSynapseEntity = Partial<{
  apiName: string;
  body: string;
  createdAtSelector: string;
  createdBySelector: string;
  cursorType: string;
  deletedFlagSelector: string;
  description: string;
  displayName: string;
  endpoint: string;
  headers: Record<string, string>;
  id: string;
  idSelector: string;
  limitParam: string;
  limitValue: number;
  metaId: string;
  method: string;
  modifiedBySelector: string;
  nextCursorParam: string;
  nextCursorSelector: string;
  offsetParam: string;
  offsetValue: number;
  pageNumberParam: string;
  pageNumberValue: number;
  pageSize: number;
  pageSizeParam: string;
  recordSelector: string;
  schema: string;
  startValue: string;
  tags: string[];
  type: EntityPaginationType;
  variables: Variable[];
  wmSelector: string;
}>;

export interface WebhookTestingResponse {
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
  records: Record<string, any>[];
}

export interface WebhookLogsParams {
  cursor: string;
  direction: string;
  count: string;
  connectorId: string;
}

export interface WebhookLogsResponse {
  records: any[];
  pageInfo: PageInfo;
}

export type CustomSynapseShareScopeType = 'PRIVATE' | 'SUBSCRIPTION' | 'SELECTED_INSTANCES' | 'GLOBAL';
export type CustomSynapseShareStatus = 'PENDING' | 'APPROVED' | 'DENIED';

export interface CustomSynapseShareScope {
  id: CustomSynapseShareScopeType;
  name: string;
  helpText?: string;
}

export interface CustomSynapseShare {
  scope: CustomSynapseShareScopeType;
  instances?: string[];
}
