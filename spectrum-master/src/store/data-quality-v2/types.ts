export type ScopeTypes = 'system' | 'attribute';
export interface DfiV2RulePayload {
  id: string;
  name: string;
  scope: string[];
  scopeType: ScopeTypes;
  category: string;
  policy: string;
  ruleConfig: any;
  passed?: number;
  failed?: number;
  total?: number;
  lastRunTime?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface DfiV2Rule extends Partial<DfiV2RulePayload> {}

export type CategoryTypes = 'custom' | 'system';
export interface DfiV2CategoryPayload {
  id: string;
  name: string;
  type: CategoryTypes;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  updatedBy: string;
}

export interface DfiV2Category extends Partial<DfiV2CategoryPayload> {
  id: string;
  isNew: boolean;
  updated: boolean;
}

export interface DfiV2CategoryUpdate extends Pick<DfiV2CategoryPayload, 'name'> {
  id: string | null;
  type: CategoryTypes;
}

export interface RulesMetadata {
  policies: Record<string, string>[];
  scopes: Record<string, string>[];
  predicate: any;
}
export interface DfiProvisionStatus {
  status: 'enabled' | 'disabled' | 'inProgress';
}
