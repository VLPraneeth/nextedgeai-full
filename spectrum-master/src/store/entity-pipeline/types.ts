//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import entityPipeline from 'reducers/entityPipelineReducer';
import { NodeConfiguration } from 'store/pipeline/types';
import AppConstants from 'utils/AppConstants';

export type SyncStatusKeys = keyof typeof AppConstants.SYNC_STATUS;
export type SyncStatusValues = typeof AppConstants.SYNC_STATUS[SyncStatusKeys];

export interface SyncStatusModel {
  errorCount: number;
  errorDetails?: string;
  lagTimeInSeconds?: number;
  lastSyncTime: string;
  status: SyncStatusValues;
}

export interface ConnectorSyncStatusModel {
  connectorName: string;
  connectorType: string;
  connectorId: string;
  entityId: string;
  entityName: string;
  historicalSync: boolean;
  iconPath?: string;
  connectorTypeDisplayName?: string;
  processedUpTo?: string; // Date
}

export type EntityPipelineState = ReturnType<typeof entityPipeline>;

export interface ConnectorEntityNode {
  id: string;
  name: string;
  coreNode: boolean;
  iconPath: string;
  configuration: ConnectorEntityNodeConfiguration[];
}

export interface ConnectorEntityNodeConfiguration extends NodeConfiguration {
  value: string;
  connectorId: string;
}
