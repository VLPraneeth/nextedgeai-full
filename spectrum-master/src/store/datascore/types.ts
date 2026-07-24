import { SerializedError } from '@reduxjs/toolkit';

import { ConditionValue } from 'components/inputs/types';
import { Predicate } from 'store/data-studio/types';
import { FetchStatus } from 'store/types';

export type DataScoreStatus = 'na' | 'unpublished' | 'available';
export type DataScoreFactoryCategory = 'top' | 'bottom';

export interface DataScoreFactor {
  averageScore: number;
  category: DataScoreFactoryCategory;
  description: string;
  label: string;
  entityId: string;
  fieldName: string;
  ruleId: string;
  filterCondition: ConditionValue;
}

// date as a MM/DD string
export type MMDDString = string;

export interface DataScoreTrend {
  rangeInDays: number;
  deltaPercent: number;
  dataPoints: Record<MMDDString, number>;
}

export interface EntityDataScore {
  factors: DataScoreFactor[];
  label: string;
  percentIncrease: number;
  score: number;
  sourceScore: null | 'na' | number; // TODO: confirm with API change
  trend: DataScoreTrend;
}

export interface EntityDataScoreResponse<T extends EntityDataScore = EntityDataScore> {
  status: DataScoreStatus;
  data: T;
}

export interface EnhancedDataScoreFactor extends DataScoreFactor {
  /** composition, base64 encoded, of entityId:fieldName:ruleId to uniquely identify this factor */
  factorId: string;
}

export interface EnhancedEntityDataScore extends Omit<EntityDataScore, 'factors'> {
  factors: EnhancedDataScoreFactor[];
}

export interface DataScoreState {
  /** we're going to enhance the contributing factors with factorId */
  dataScoreByEntity: Record<string, EntityDataScoreResponse<EnhancedEntityDataScore> | null>;
  dataScoreStatusByEntity: Record<string, FetchStatus>;
  dataScoreErrorByEntity: Record<string, SerializedError | null>;
}

export interface GetDataScoreForEntityArgs {
  entityId: string;
  predicate?: Predicate;
}

export interface SegmentConfig {
  color: string;
  label?: string;
  min: number;
  max: number;
}
