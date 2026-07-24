// map from UX graphVersions to the backend schema status
export const SCHEMA_STATUSES_MAP = new Proxy(
  {
    APPROVED: 'APPROVED',
    ARCHIVED: 'ARCHIVED',
    DRAFT: 'NEW',
    NEW: 'NEW',
  },
  {
    // ensure we're always comparing properly
    get: (target: any, propName: string) => {
      const key = propName.toUpperCase();
      return key in target ? target?.[key] : key;
    },
  }
);

export const getGraphVersionKey = (graphVersion: string) => SCHEMA_STATUSES_MAP[graphVersion];
export const makeSchemaKey = ({ entityId, graphVersion }: { entityId: string; graphVersion: string }) =>
  `${entityId}-${getGraphVersionKey(graphVersion)}`;
