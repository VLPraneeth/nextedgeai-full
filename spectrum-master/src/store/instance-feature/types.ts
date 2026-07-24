//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

export type InstanceFeatureStage = 'internal' | 'beta' | 'GA';
export type InstanceFeatureStatus = 'active' | 'inactive' | 'activating';
export interface InstanceFeature {
  name: string;
  displayName: string;
  description: string;
  stage: InstanceFeatureStage;
  status: InstanceFeatureStatus;
  hidden: boolean;
  enabled: boolean;
}
