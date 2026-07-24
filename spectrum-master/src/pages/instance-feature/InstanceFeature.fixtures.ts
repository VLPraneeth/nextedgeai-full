export const getFeatures = () => {
  return [
    {
      name: 'Insights',
      displayName: 'Insights',
      description: 'Enables Insights Studio',
      stage: 'GA',
      status: 'active',
      params: null,
      hidden: false,
      enabled: true,
    },
    {
      name: 'Datastore',
      displayName: 'Data Store',
      description: 'Makes a PostgreSQL data warehouse available',
      stage: 'GA',
      status: 'active',
      params: null,
      hidden: false,
      enabled: true,
    },
    {
      name: 'InsightsAdvanceDataset',
      displayName: 'InsightsAdvanceDataset',
      description: '',
      stage: 'GA',
      status: 'active',
      params: null,
      hidden: false,
      enabled: false,
    },
  ];
};
