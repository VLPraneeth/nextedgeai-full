//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export type EntityMetricStatuses = 'COMPLETED' | 'IN_PROGRESS' | 'NOT_STARTED';

export interface EntityMetricStageDetail {
  connectorId: string;
  connectorEntityName: string;
  connectorName: string;
  createdCount: number | null;
  deletedCount: number | null;
  duration: number;
  lastProcessed: string | null;
  mergedCount: number | null;
  readCount: number | null;
  skippedCount: number | null;
  totalProcessedRecordsCount: number | null;
  updatedCount: number | null;
  durationUnit: string;
}

export interface EntityMetricStage {
  details: Record<string, EntityMetricStageDetail> | null;
  durationUnit: string;
  lastProcessed: string | null;
  recordCountSuffix: string;
  status: EntityMetricStatuses;
  subtitle: string | null;
  title: string;
  duration: number | null;
  durationWithoutConversion: number | null;
  totalProcessedRecordsCount: number | null;
}

export interface EntityMetricsPayload {
  entityName: string;
  apiName: string;
  syncariEntityId: string;
  emptyLastSync: boolean;
  lastProcessed: string | null;
  lastSyncTime: string | null;
  duration: number | null;
  durationUnit: string;
  title: string | null;
  allStages: EntityMetricStage[] | null;
  warningCount?: number;
  errorCount?: number;
}
