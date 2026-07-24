//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export interface QuickStarts {}

export type QuickStartHistoryParams = Record<'quickStartName', string>;

export interface QuickStartHistoryRun {
  name: null | string;
  details: string;
  executedAt: null | string;
  executedBy: string;
  executedByName: string;
  status: QuickStartRunStatus;
  errorMsg: null | string;
  inputs: [];
  qsType: string;
}

export enum QuickStartRunStatus {
  QUEUED = 'QUEUED',
  PROCESSING = 'PROCESSING',
  SUCCESS = 'SUCCESS',
  ERROR = 'ERROR',
}

export interface QuickStartHistory {
  displayName: string;
  name: string;
  runs: QuickStartHistoryRun[];
}

// TODO: Type value. This are outputs for each input components
export type ExecuteQuickStart = Record<string, any>;
