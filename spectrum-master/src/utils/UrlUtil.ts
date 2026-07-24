//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { capitalize, find } from 'lodash';

import { ConnectorModel } from 'components/GraphItemFilter';
import { encodeObjectToSearchParams } from 'hooks/useQueryParams';
import { SchemaVersion } from 'pages/schema-studio/types';
import { RootState } from 'reducers';
import { ErrorNotificationConfig } from 'store/error-notifications-v2/types';
import { UploadFolder } from 'store/imported-files/types';
import { DataCard, Dataset, InsightsDashboard } from 'store/insights-studio/types';
import { NewDashboardState } from 'store/new-dashboard/slice';
import { QuickStartInstalls } from 'store/quick-start/types';
import { ReferenceDataState } from 'store/reference-data/slice';
import { LegacySchemaState } from 'store/schema/types';
import { getEntityName } from 'utils/EntityUtil';
import { tNamespacedOptional } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { UnreachableCaseError, ValuesOf } from 'utils/TypeUtils';

import AppConstants from './AppConstants';
import { replaceToken as strReplaceToken } from './StringUtil';

export const currentOrigin = window.location.origin;

export const VALID_SCHEMA_VERSIONS = ['draft', 'published'] as const;

const tn = tNamespacedOptional('UrlUtil');

export function replaceToken(url: string, tokens: Record<string, any>) {
  return strReplaceToken(url, tokens);
}

export function getPaths(pathname: string) {
  pathname = pathname.replace(/^\//, '');
  return pathname.split('/');
}

export interface UrlListMetadata {
  connectors: ConnectorModel[];
  entities: RootState['entity']['entities'];
  connectorSchemas: LegacySchemaState['connectorSchemas'];
  dashboards: NewDashboardState['dashboards'];
  insightsDashboards: InsightsDashboard[];
  referenceData: ReferenceDataState['entities'];
  importedFolders: UploadFolder[];
  quickStarts: QuickStartInstalls | undefined;
  datasets: Dataset[] | undefined;
  dataCards: DataCard[] | undefined;
  errorNotifications: ErrorNotificationConfig[] | undefined;
}

const ENTITY_ID_INDEX = 2;
const MIN_PATH_COUNT = 5;
export const SCHEMA_VERSION_INDEX = 5;

export type ListType = ValuesOf<typeof AppConstants.LIST_TYPES>;

const isListType = (variableToCheck: unknown): variableToCheck is ListType => {
  return (
    typeof variableToCheck !== 'undefined' &&
    Object.values(AppConstants.LIST_TYPES).includes(variableToCheck as ListType)
  );
};

export function getUrlListItemName(
  type: ListType | string,
  id: string,
  lists: Partial<UrlListMetadata>,
  // TODO: The url argument doesn't appear to be used. We should investigate and
  // remove it entirely
  url?: string
): string | undefined {
  if (!isListType(type)) {
    return;
  }

  // Assume that uuids are > 20. Should 25-ish characters
  // None uuids are ignored and not getting looked up
  if (
    id?.length < 20 &&
    type !== AppConstants.LIST_TYPES.DATA_QUALITY_STUDIO &&
    type !== AppConstants.LIST_TYPES.ENTITY &&
    type !== AppConstants.LIST_TYPES.QUICK_START &&
    type !== AppConstants.LIST_TYPES.INSIGHTS_DASHBOARD
  ) {
    return;
  }

  switch (type) {
    case AppConstants.LIST_TYPES.CONNECTOR:
      if (lists.connectors) {
        const connector = lists.connectors.find((connector) => connector.id === id);
        if (connector?.name === AppConstants.SYNCARI_CONNECTOR_NAME) {
          return capitalize(connector.name);
        }
        return connector?.name || id;
      }
      break;
    case AppConstants.LIST_TYPES.ENTITY:
      //handle quick starts
      if (id === AppConstants.LIST_TYPES.QUICK_START) {
        return tn('/quick-start');
      }
      if (lists.entities) {
        let displayName = getEntityName(id, lists.entities);
        if (!displayName && lists?.connectorSchemas && Object.keys(lists.connectorSchemas).length > 0 && url) {
          // Check if we are in schema studio
          // Schema studio key does not follow the key -> id convention and uses key -> apiName combination
          // Note that normally api names are case sensitive, we are burrying our head in the sand
          // here since url are case insensitive. TODO: Should move the url to id instead of apiName.
          const paths = getPaths(url);
          if (paths?.length > MIN_PATH_COUNT) {
            const selectedSchema = lists?.connectorSchemas?.[paths[ENTITY_ID_INDEX]]?.data?.find(
              (schema) => schema.apiName.toLowerCase() === id.toLowerCase()
            );
            // TS does not seem to understand that we checked the value of version against a valid
            // lists of possible values in the next line thus we have to cast it :(
            const version = paths[SCHEMA_VERSION_INDEX].toLowerCase() as SchemaVersion;
            if (VALID_SCHEMA_VERSIONS.includes(version)) {
              displayName = selectedSchema?.[version]?.fields?.displayName;
            }
          }
        }
        return displayName ? displayName : id;
      }
      break;
    case AppConstants.LIST_TYPES.QUICK_START:
      const quickStart = lists?.quickStarts?.find((item) => item.id === id);
      let displayName = quickStart?.displayName;
      return displayName ? displayName : id;

    case AppConstants.LIST_TYPES.FIELD:
      if (lists.entities) {
        let displayName = id;
        find(lists.entities, (entity) => {
          let found = false;
          const field = find(entity.fields, (field) => {
            return field.id === id;
          });
          if (field) {
            displayName = field.displayName;
            found = true;
          }
          return found;
        });
        return displayName;
      }
      break;
    case AppConstants.LIST_TYPES.DATA_QUALITY_STUDIO:
      return lists.dashboards?.[id]?.title;
    case AppConstants.LIST_TYPES.INSIGHTS_DASHBOARD:
      return lists.insightsDashboards?.find((dash) => dash.id === id)?.displayName;
    case AppConstants.LIST_TYPES.REFERENCE_DATA:
      return lists.referenceData?.[id]?.name;
    case AppConstants.LIST_TYPES.PIPELINE:
    case AppConstants.LIST_TYPES.NODE_ID:
    case AppConstants.LIST_TYPES.RECORD:
      break;
    case AppConstants.LIST_TYPES.FOLDER:
      return find(lists.importedFolders, { id })?.name || id;
    case AppConstants.LIST_TYPES.FILE:
      // TODO: optimize for performance
      const folder = lists.importedFolders?.find((folder) => folder.files.find((file) => file.id === id));
      const file = folder?.files.find((file) => file.id === id);
      return file?.name;
    case AppConstants.LIST_TYPES.DATASET:
      return lists?.datasets?.find((dataset) => dataset.id === id)?.displayName;
    case AppConstants.LIST_TYPES.DATA_CARD:
      return lists?.dataCards?.find((dataCard) => dataCard.id === id)?.displayName;
    case AppConstants.LIST_TYPES.ERROR_NOTIFICATIONS_EMAIL:
    case AppConstants.LIST_TYPES.ERROR_NOTIFICATIONS_WEBHOOK:
      return lists?.errorNotifications?.find((notification) => notification.id === id)?.name;

    default:
      if (process.env.NODE_ENV !== 'production') {
        throw new UnreachableCaseError(type);
      }
      break;
  }
}

/**
 * Extract the key from the path. This is just
 * removing the / from the front of the path.
 * @param {String} path url path
 */
export const getPathKey = (path: string) => {
  return String(path?.replace(/^\//, '')).toLowerCase();
};

const {
  SYNAPSES,
  DATA_QUALITY_STUDIO_ROOT,
  DATA_STUDIO_ROOT,
  ENTITIES,
  IMPORTED_FILES,
  NOTIFICATION,
  REFERENCEDATA,
  SCHEMA_STUDIO_ROOT,
  SETTINGS,
  SYNC_STUDIO,
  LOGS,
  INSIGHTS_STUDIO,
  QUICK_START,
} = RouteConstants;

export const ROOT_PATHS = {
  SYNAPSES,
  DATA_QUALITY_STUDIO_ROOT,
  DATA_STUDIO_ROOT,
  ENTITIES,
  IMPORTED_FILES,
  INSIGHTS_STUDIO,
  LOGS,
  REFERENCEDATA,
  SCHEMA_STUDIO_ROOT,
  SETTINGS,
  QUICK_START,
};

export const SYSTEM_ROOT_PATHS = {
  ...ROOT_PATHS,
  SYNC_STUDIO,
  NOTIFICATION,
} as const;

export const ROOT_PATH_KEYS = Object.values(ROOT_PATHS).reduce(
  (acc: Record<string, ValuesOf<typeof ROOT_PATHS>>, path) => {
    acc[path] = getPathKey(path);
    return acc;
  },
  {}
);

const SYSTEM_DEFINED_BASE_PATH = [RouteConstants.SETTINGS, RouteConstants.PROFILE];
export const systemDefinedPaths = (path: string) => SYSTEM_DEFINED_BASE_PATH.some((p) => path.startsWith(p));
export const getSystemDefinedPathDisplayName = (currentPath: string) => {
  currentPath = currentPath.toLowerCase();
  const tName = tn(currentPath);
  if (tName !== `UrlUtil.${currentPath}`) {
    return tName;
  }
};

/*
 * Translate a route object to a UI route.
 * @param {Object} routeObj route object that will be translated to a UI route. Example route object:
 * {
 *   route: 'ENTITY_PIPELINE',  // Required Key. This is a key in RouteConstants. See utils/RouteContants.
 *   entityId: '123-2345-3433', // extra keys be used to replace the route contants tokens
 *   ...
 * }
 * @returns {String} Resulting path
 */
interface RouteObject {
  route: keyof typeof RouteConstants;
  entityId: string;
}

export function getRoute(routeObj: RouteObject) {
  if (!routeObj || !routeObj.route) {
    console.error('Invalid passed route object');
    return;
  } else if (!RouteConstants[routeObj.route]) {
    console.error(`route key ${routeObj.route} not found`);
    return;
  }
  return replaceToken(RouteConstants[routeObj.route], routeObj);
}

export type MakeUrlTokens = Record<string, string | null | undefined | boolean> | null;

export interface MakeUrlOptions {
  // makeUrl will encode it to plus by default
  encodeToPlus?: boolean;
}

export const makeUrl = (url: string, tokens?: MakeUrlTokens, params?: {}, makeUrlOptions?: MakeUrlOptions): string => {
  const baseUrl = tokens ? replaceToken(url, tokens) : url;
  const filteredParams = params
    ? Object.fromEntries(
        Object.entries(params)
          .filter(([key, value]) => Boolean(value))
          .map(([key, value]) => [key.toString(), (value as any).toString()])
      )
    : {};

  const encodeToPlus = makeUrlOptions?.encodeToPlus ?? true;
  const qs = encodeToPlus ? new URLSearchParams(filteredParams).toString() : encodeObjectToSearchParams(filteredParams);
  return [baseUrl, qs].filter(Boolean).join('?');
};

export const compareRouteToPathname = (route: string, pathname: string, wildcardDelimiter: string = '{') => {
  // Compare a pathname to a route with a wildcard.
  // i.e. /sync-studio/entity/{entityId}
  if (route.includes(wildcardDelimiter)) {
    const routeTokens = route.split('/');
    const pathnameTokens = pathname.split('/');

    if (routeTokens.length === pathnameTokens.length) {
      // Compare each part of the route to each part of the pathname.
      // If the route part contains a wildcard then always return true.
      // i.e {entityId} === 626fe8ffee18af17f1ee5fd8 -> true
      return routeTokens.every((routeToken, i) => {
        return routeToken.startsWith(wildcardDelimiter) ? true : routeToken === pathnameTokens[i];
      });
    }

    return false;
  }

  return route === pathname;
};

export const makeRouteConstantToRoute = (pathTemplate: string) => pathTemplate.replaceAll('{', ':').replaceAll('}', '');
