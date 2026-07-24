//
// Copyright (c) 2019-Present Syncari All rights reserved.
//
import { SelectTextValue } from 'components/inputs/select-text/SelectText';

import { ActionAuthenticationValue } from './ActionAuthentication';
import { ActionBodyValue } from './ActionBody';
import { Header } from './ActionHeader';
import { ActionTestingValue } from './ActionTesting';
import { Variable } from './ActionVariable';

export interface ActionConfiguration {
  body?: {
    batchSize?: string;
    isBatch?: string;
    bodyValue?: string;
  };
  method?: string;
  endpoint?: {
    textValue?: string;
    selectValue?: string;
  };
  authentication?: {
    credentialId?: string;
    metadataId?: string;
  };
  variables?: Variable[];
  headers?: Header[];
}

export interface CustomAction {
  id?: string;
  displayName: string;
  description?: string;
  apiName: string;
  iconPath?: File;
  tags?: string[];
  basicHelpText?: string;
  helpLink?: string;
  actionConfiguration: ActionConfiguration;
}

export interface ActionSetupValue {
  endpoint?: SelectTextValue;
  body?: ActionBodyValue;
  authentication?: ActionAuthenticationValue;
  headers?: Header[];
  variables?: Variable[];
  testingValue?: ActionTestingValue;
}

export enum ActionTabName {
  AUTHENTICATION = 'authentication',
  HEADERS = 'headers',
  BODY = 'body',
  VARIABLES = 'variables',
  TESTING = 'testing',
}
