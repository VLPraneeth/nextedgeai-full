//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

import { AuthTypes, SupportedAuthType } from 'store/credential/types';
import { CustomSynapseDraftStatuses, SDKCustomSynapseFunctionDeployStatuses } from 'store/custom-synapse/types';
import AppConstants from 'utils/AppConstants';
import { ValuesOf } from 'utils/TypeUtils';

export type CustomSynapseDraftStatus = ValuesOf<typeof AppConstants.GRAPH_STATUS> | 'SUBMIT_FOR_APPROVAL';

export interface HttpSynapseMetadata {
  id?: string;
  name?: string;
  value?: string;
  dataType?: string;
}
export interface CustomSynapse {
  id: string;
  name: string;
  displayName: string;
  deploymentStatus: SDKCustomSynapseFunctionDeployStatuses;
  iconUri: string;
  updatedAt: string;
  description?: string;
  tags?: string[];
  basicHelpText?: string;
  helpLink?: string;
  draftStatus: CustomSynapseDraftStatuses;
  parentId?: string | null;
  publishToGlobal?: boolean;
  isGlobal?: boolean;
  supportedAuthTypes: SupportedAuthType[];
  customSynapseType?: 'SDK' | 'HTTP' | 'WEBHOOK';
  authType?: AuthTypes;
  body?: string;
  endpoint?: string;
  httpSource?: boolean;
  variableValues?: { name?: string; value?: string }[];
  variables?: HttpSynapseMetadata[];
  method?: string;
  headers?: Record<string, string>;
  authConfig?: Record<string, any>;
  recordSelector?: string;
  idSelector?: string;
  schema?: string;
  responseTemplate?: string;
  responseCode?: number;
}
