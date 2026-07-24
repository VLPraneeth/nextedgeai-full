import { SerializedError } from '@reduxjs/toolkit';

import { Entity, EntityField } from 'store/entity/types';
import { FetchStatus } from 'store/types';

export enum RuleImpact {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
}

export enum RuleConditionType {
  BOOLEAN = 'BOOLEAN',
  STRING = 'STRING',
  INTEGER = 'INTEGER',
  INT_RANGE = 'INT_RANGE',
  DATE_RANGE = 'DATE_RANGE',
  REGEX = 'REGEX',
  // Filter and lookup have been descoped for v1
  // LOOKUP = 'LOOKUP',
  // FILTER_CONDITION = 'FILTER_CONDITION',
}

export const ruleImpactKeys = Object.values(RuleImpact);

export interface DfiRuleRaw {
  id: string;
  seeded: boolean;
  modified: boolean;
  disabled: boolean;
  name: string;
  selectedFields: string[];
  conditions: DfiRuleCondition[];
}

export interface DfiRulePostData extends Partial<DfiRuleRaw> {}

export interface DfiRuleDefinition {
  name: string;
  label: string;
  description: string;
  type: RuleConditionType;
  defaultImpact: RuleImpact;
}

export interface DfiRule extends DfiRuleRaw {}

export interface EntityDfiRulesResponse {
  entityId: string;
  id: string;
  lastPublished: string | null;
  // deletedRuleIds is combined with the count of modified rules to determine
  // the number of modified rules and if the user can publish
  deletedRuleIds: string[];
  entityApiName: string;
  entityDef: Entity;
  published: boolean;
  ruleDefinitions: DfiRuleDefinition[];
  rules: DfiRuleRaw[];
  fields: EntityField[];
}

export interface EntityDfiRulesPostData
  extends Omit<
    EntityDfiRulesResponse,
    'entityDef' | 'rules' | 'ruleDefinitions' | 'fields' | 'published' | 'lastPublished'
  > {
  rules: DfiRulePostData[];
}

export interface EntityDfiRules extends Omit<EntityDfiRulesResponse, 'entityDef'> {
  rules: DfiRule[];
}

export interface DfiRecalcuatingStatus {
  completed: boolean;
  progressPercentage: number;
}

export interface DataQualityState {
  dfiRuleDetailsOpen: boolean;
  dfiRuleDetailsRuleId?: string;
  dfiRulesByEntity: Record<string, EntityDfiRules | null>;
  dfiRulesStatusByEntity: Record<string, FetchStatus>;
  dfiRulesErrorByEntity: Record<string, SerializedError | null>;
  dfiRulesRecalculatingByEntity: Record<string, DfiRecalcuatingStatus>;
}

export interface DfiRuleCondition {
  name: string;
  conditionMatches: boolean;
  impact: RuleImpact;
  type: RuleConditionType;
  conditionValues: string[] | null;
}

export type DfiRuleFormValues = Pick<DfiRuleRaw, 'name' | 'selectedFields' | 'conditions'>;
