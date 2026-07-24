//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { InputDataType } from 'components/inputs/types';

// export interface Credential {
//   id: string;
//   displayName: string;
// }

export type AuthTypes =
  | 'UserPassword'
  | 'UserPasswordToken'
  | 'ApiKey'
  | 'ApiSecretKey'
  | 'Oauth'
  | 'SimpleOAuth'
  | 'OneClickOAuth'
  | 'NetSuiteTokenBasedAuthentication'
  | 'Custom'
  | 'None';

export interface Credential {
  id: string;
  name: string;
  metadataId: string;
  status: string;
  errorMessage?: string;
  errorDetails?: string;
}

export type CredentialRequestAuthConfig = Record<string, string>;

export interface CredentialRequest extends Credential {
  authType?: AuthTypes;
  authConfig?: {
    name: string;
    metadataId: string;
    authType: AuthTypes;
  };
}

// export type CredentialMetadata = any;

export interface CredentialMetadata {
  id: string;
  name: string;
  displayName: string;
  type: string;
  description: string;
  supportedAuthTypes: SupportedAuthTypes;
}

type SupportedAuthTypes = SupportedAuthType[];

export interface SupportedAuthTypeField {
  dataType: InputDataType;
  helpSummary: string | null;
  label: string;
  name: string;
  required: boolean;
  hidden?: boolean;
  defaultValue?: string;
  options?: { label: string; value: string }[];
}

export interface SupportedAuthType {
  authType: AuthTypes;
  fields: SupportedAuthTypeField[];
  label: string;
  helpSummary: string;
}
