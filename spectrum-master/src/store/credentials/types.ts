export enum ServiceCredentialTypeOptionsEnum {
  CLEARBIT = 'clearbit',
  SLACK = 'slack',
  ZOOMINFO = 'zoominfo',
  SALESINTEL = 'salesintel',
  SIMILARWEB = 'similarweb',
  INSIDEVIEW = 'insideview',
  APEXANALYTIX = 'apexanalytix',
  AIDENTIFIED = 'aidentified',
  APIKEY = 'genericApiKey',
  OAUTH = 'genericSimpleOAuth',
  BEARERTOKEN = 'genericBearerToken',
  MSTEAMS = 'msteams',
}

interface BaseServiceCredential {
  id: string;
  name: string;
}

export interface ClearBitServiceCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.CLEARBIT;
  key: string;
}

export interface SimilarWebServiceCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.SIMILARWEB;
  key: string;
}

export interface SalesIntelServiceCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.SALESINTEL;
  key: string;
}

export interface ZoomInfoServiceCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.ZOOMINFO;
  password: string | null;
  username: string | null;
}

export interface SlackServiceCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.SLACK;
}

export interface InsideViewCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.INSIDEVIEW;
  clientId: string | null;
  clientSecret: string | null;
}

export interface ApexAnalytixCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.APEXANALYTIX;
  key: string;
}

export interface AidentifiedCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.AIDENTIFIED;
  key: string;
}

export interface ApiKeyCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.APIKEY;
  key: string;
}

export interface BearerTokenCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.BEARERTOKEN;
  key: string;
}

export interface MsTeamsCredential extends BaseServiceCredential {
  type: ServiceCredentialTypeOptionsEnum.MSTEAMS;
  key: string;
}

export interface OAuthCredential extends Omit<InsideViewCredential, 'type'> {
  type: ServiceCredentialTypeOptionsEnum.OAUTH;
  endPoint: string | null;
}

export type ServiceCredential =
  | ClearBitServiceCredential
  | ZoomInfoServiceCredential
  | SalesIntelServiceCredential
  | SimilarWebServiceCredential
  | SlackServiceCredential
  | InsideViewCredential
  | ApexAnalytixCredential
  | AidentifiedCredential
  | ApiKeyCredential
  | MsTeamsCredential
  | BearerTokenCredential
  | OAuthCredential;
