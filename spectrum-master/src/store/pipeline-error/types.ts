export type PipelineSyncLevel = 'ENTITY' | 'ATTRIBUTE';
export type PipelineSyncErrorType = 'SYNC' | 'ACTION';

export interface PipelineSyncError {
  level: PipelineSyncLevel;
  errorMessage: string;
  errorDetail: string;
  nodeId: string;
  targetId: string;
  retryCount?: string;
}

export interface PipelineSyncWarning extends PipelineSyncError {
  errorType: PipelineSyncErrorType;
  errorCount: number;
  totalCount: number;
}

export interface PipelineError {
  syncariEntityId: string;
  syncCycleId: string;
  error?: PipelineSyncError;
  warnings: PipelineSyncWarning[];
}
