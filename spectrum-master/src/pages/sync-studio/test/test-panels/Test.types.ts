import { AuthTypes } from 'store/credential/types';

export enum TestType {
  DATE = 'date',
  ID = 'id',
  PAYLOAD = 'payload',
}

export enum LiveTestDateOptions {
  start = 'start',
  end = 'end',
}

export interface LiveTestExternalIds {
  external_id: string;
  source_entity_id: string;
}

export interface TestRunLivePanelState {
  start: null | string;
  end: null | string;
  limit: number;
  testType: TestType;
  externalIds: LiveTestExternalIds[];
  validationMessage: string;
  loading: boolean;
  sourceId?: string;
  webhookSynapse?: {
    id?: string;
    authType?: AuthTypes;
    authConfig?: Record<string, any>;
    body?: string;
  };
}
