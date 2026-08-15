import RouteConstants from 'utils/RouteConstants';

export const START_PRODUCT_TOUR_EVENT = 'nextedge:start-product-tour';
export const GUIDED_DEMO_EMAIL = 'demo@nextedge.ai';

export type PublicDemoAccount = {
  id: 'guided' | 'admin';
  label: string;
  description: string;
  email: string;
  password: string;
};

export const getPublicDemoAccounts = (): PublicDemoAccount[] => {
  const root = document.getElementById('root');
  const guided = {
    id: 'guided' as const,
    label: 'Guided demo account',
    description: 'Read-only · Working features only · Includes product tour',
    email: root?.dataset.demoGuidedEmail || '',
    password: root?.dataset.demoGuidedPassword || '',
  };
  const admin = {
    id: 'admin' as const,
    label: 'Admin account',
    description: 'Full workspace · All available features and demo data',
    email: root?.dataset.demoAdminEmail || '',
    password: root?.dataset.demoAdminPassword || '',
  };

  return [guided, admin].filter((account) => account.email && account.password);
};

export const isGuidedDemoAccount = (email?: string | null) =>
  Boolean(email && email.trim().toLowerCase() === GUIDED_DEMO_EMAIL);

const GUIDED_ROUTE_PREFIXES = [
  RouteConstants.V1_WORKSPACE,
  RouteConstants.SYNAPSES_CONNECTIONS,
  RouteConstants.SCHEMA_STUDIO_ROOT,
  RouteConstants.SYNC_STUDIO,
  RouteConstants.DATA_STUDIO_ROOT,
  RouteConstants.DATA_QUALITY_STUDIO_ROOT,
  RouteConstants.IMPORTED_FILES,
  RouteConstants.LOGS,
  RouteConstants.PROFILE,
  RouteConstants.NOTIFICATION,
];

export const isGuidedDemoRoute = (pathname: string) =>
  pathname === RouteConstants.SYNAPSES ||
  GUIDED_ROUTE_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));

const GUIDED_CONNECTORS = new Set([
  'amazon s3',
  'file',
  'file data',
  'file/csv',
  'filedata',
  'mongodb',
  'mysql',
  'postgres',
  'postgresql',
  's3',
]);

export const isGuidedDemoConnector = (metadata: Record<string, unknown>) => {
  const candidates = [metadata.name, metadata.displayName, metadata.connectorName, metadata.apiName];
  return candidates.some((candidate) =>
    GUIDED_CONNECTORS.has(
      String(candidate || '')
        .trim()
        .toLowerCase()
    )
  );
};
