//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import AppConstants from 'utils/AppConstants';

export enum FeatureFlagName {
  QUICK_START = 'QUICK_START',
  ABAC = 'ABAC',
  INSIGHTS_GPT = 'INSIGHTS_GPT',
}

interface FeatureMetaData {
  name: FeatureFlagName;
  displayName: string;
  description: string;
}

export const FEATURE_METADATA: Record<FeatureFlagName, FeatureMetaData> = {
  [FeatureFlagName.QUICK_START]: {
    name: FeatureFlagName.QUICK_START,
    displayName: 'Enable legacy quick starts',
    description: 'Enable legacy quick starts within Sync Studio.',
  },
  [FeatureFlagName.ABAC]: {
    name: FeatureFlagName.ABAC,
    displayName: 'Attribute based access control',
    description: 'Enables attribute based access control in Settings',
  },
  [FeatureFlagName.INSIGHTS_GPT]: {
    name: FeatureFlagName.INSIGHTS_GPT,
    displayName: 'Insights GPT',
    description: 'Insights GPT Demo',
  },
};

// These flags can be set directly in the devtools by setting
// LocalStorage.setItem("FLAG_NAME") in the console. All flags are disabled for
// production.
export function isFeatureEnabled(featureName: FeatureFlagName) {
  return process.env.ENV_NAME !== 'production' && localStorage[featureName] === AppConstants.TRUE;
}
