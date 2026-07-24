import { OptionsStackingValue } from 'highcharts';
import ReactGridLayout from 'react-grid-layout';

import { CursorPageInfo, PaginationDirection } from 'components/AgTable/Pagination';
import { FilterValue } from 'components/inputs/types';
import { SkullConfig } from 'components/skull';
import { FieldDataType } from 'components/types';
import { VizerDisplayFormat } from 'components/vizer/types';
import { CalculatedField } from 'pages/insights-studio/dataset/configuration/sections/CalculatedFields.types';
import { FieldModel } from 'store/schema/types';
import { DeepPartial, OptionalExceptFor } from 'utils/TypeUtils';

export interface InsightsDashboard {
  dataCards?: DashListDataCard[]; // array of card IDs. <DataCard /> will request the specific configuration details
  description: string;
  displayName: string;
  id: string;
  isExample?: boolean;
  draftStatus: 'NEW' | 'APPROVED';
  tags?: string[];
  draft?: null | InsightsDashboard;
  name?: string;
  seeded?: boolean;
  parentId?: string;
}

export interface DashListDataCard extends OptionalExceptFor<DataCardWithData, 'id' | 'layout'> {}

export type Position = 'TOP' | 'BOTTOM' | 'LEFT' | 'RIGHT';

export interface DataCardSkullConfig
  extends Omit<SkullConfig, 'datatype' | 'renderType' | 'mapping' | 'configuration' | 'component' | 'renderer'> {
  component: string;
}

export type DataCardSettingsValue = Record<string, VariableValue>;

export interface DataCardWithData {
  configuration?: DataCardSettingsValue;
  configurationMeta?: DataCardSkullConfig[];
  contents: DataCardContent;
  description: string;
  displayName: string;
  hidden: boolean;
  id: string;
  layout: DataCardLayout;
  name: string;
}

// START within DataCard

export interface DataCardLayout extends OptionalExceptFor<ReactGridLayout.Layout, 'w' | 'h' | 'x' | 'y'> {}

export interface DataCardContent {
  configuration: DataCardVizConfig;
  configurationMeta?: DataCardSkullConfig[];
  contents?: DataCardContent | null;
  data: DataCardData;
  id?: string;
}

//// START within DataCardContent
export interface DataCardVizConfig {
  colorTheme?: string;
  columns: DataColumn[] | null;
  datasetId?: string;
  legendPosition?: Position;
  series?: SeriesConfig[];
  stacking?: OptionsStackingValue;
  vizType?: VizType;
  vizLabel?: string;
  vizLabelVisible?: boolean;
  vizLabelPosition?: Position;
  xaxis: FieldConfig;
  yaxis: FieldConfig[];
  category?: FieldConfig;
  subCategory?: FieldConfig;
  value?: FieldConfig;
  minimumValue?: {
    value?: number;
    label?: string;
    applyToSubCategories?: boolean;
  };
  ranges?: Range[];
  legendVisible?: boolean;
  labelVisible?: boolean;
  displayAdditional?: 'labels' | 'legends' | 'step_to_step_ratio';
  labelPosition?: 'INSIDE' | 'RIGHT';
  measure?: FieldConfig;
  dataField?: FieldConfig;
  stages?: string[];
  sortBy?: 'stage' | 'value';
  ascending?: boolean;
  categoryValues?: CategoryValue[];

  variablesMap?: Record<string, DatasetVariable>;
}

////// START within DataCardVizConfig

export type VizType = 'BAR' | 'COLUMN' | 'LINE' | 'METRIC' | 'TABLE' | 'PIE' | 'GAUGE' | 'FUNNEL';

export interface SeriesConfig {
  column: string;
  displayFormat: string;
  displayName: string;
  name: string;
}

export interface FieldConfig {
  column: string;
  displayFormat?: VizerDisplayFormat;
  displayName: string;
  name: string;
  color?: string;
}

export interface CategoryValue {
  name?: string;
  color?: string;
}

export interface Range {
  name: string;
  minimumValue: number;
  maximumValue: number;
  color: string;
  isSystemGenerated?: boolean;
}

////// END within DataCardVizConfig

export interface DataCardErrorMessage {
  title: string;
  body: string;
}

export interface DataCardData {
  columns: DataColumn[];
  error?: DataCardErrorMessage;
  rows: DataRow[];
  series: DataSeries[];
  pageInfo?: CursorPageInfo;
}

////// START within DataCardData

export interface DataColumn {
  displayFormat?: VizerDisplayFormat;
  displayName: string;
  name: string;
  color?: string;
}

export interface DataRow {
  [key: string]: any;
}

export interface DataSeries {
  color: string;
  displayName: string;
  yaxis?: number;
}

////// END within DataCardData
//// END within DataCardContent
// END within DataCard

// Request Types
export interface DataCardRequestParams {
  dashboardId: string;
  dataCardId: string;
  configuration?: Record<string, any>;
}

export interface DataCardRequestPageParams extends DataCardRequestParams {
  pageCursor: DatasetPagination;
  previousTotalCount?: number;
}

export interface DataCardUpdateRequestParams extends DataCardRequestParams {
  dataCard: DeepPartial<DataCardWithData>;
}

export type DraftStatuses = 'NEW' | 'APPROVED';

export interface DataCard {
  id: string;
  name: string;
  displayName: string;
  description: string;
  tags: string[];

  seeded?: boolean;

  // draft?: DataCard | null;
  // draftStatus: DraftStatuses;

  createdBy: string;
  createdAt: string;

  contents: {
    configuration: DataCardVizConfig;
  } | null;
}

export type UnsavedDataCard = Omit<DataCard, 'createdAt' | 'createdBy' | 'id'>;

// Note: BE has 'LITERAL' dataset type. It will be refactored later on since
// its not really a dataset type. Its only used in the functions
export type DatasetTypes = 'ENTITY' | 'DATASET' | 'LITERAL';

export interface DatasetFields {
  apiName: string;
  dataType?: FieldDataType;
  datasetId: string;
  displayName?: string;
  datasetType: DatasetTypes;
  fieldId: string;
  alias?: string;
  datasourceAlias?: string;
  type: 'variable'; // Leaking BE property to the UI. BE needs to remove this.
  fieldAlias?: string;
}

export interface DatasetConfigProjections {
  aliasName?: string;
  apiName: string;
  dataType: FieldModel['dataType'];
  aggFunctions?: string;
  datasetFields: DatasetFields[];
}

export type JoinTypes = 'Inner' | 'LeftOuter' | 'RightOuter' | 'Full';
export interface Joins {
  joinId?: string; // UI fabricated id. Add / remove from and to the server
  field1?: DatasetFields;
  field2?: DatasetFields;
  joinType: JoinTypes;
}

export interface Group {
  groupId?: string;
  datasetField?: DatasetFields;
}

export interface DatasetSort {
  sortId?: string;
  ascending?: boolean;
  field?: DatasetFields;
}

export type FromEntityWithAlias = Record<string, string>;

export type ConfigMode = 'SQL' | 'BASIC';
export interface DatasetConfig {
  fromDataset: DataSource[];
  calculatedFields?: CalculatedField[];
  joins?: Joins[];
  filter?: FilterValue;
  sort?: DatasetSort[];
  group: boolean;
  groupBy?: Group[];
  selectedFields?: DatasetFields[];
  limit?: string;
  configMode?: ConfigMode;
}

export interface Dataset {
  id: string;
  name: string;
  displayName: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
  // draftStatus?: DraftStatuses;
  sharedWithOrg?: boolean;
  shareWithInstance?: string[];
  publishedToLibrary?: boolean;
  hidden?: boolean;
  seeded?: boolean;
  tags?: string[];
  iconPath?: string;
  datasetConfig: DatasetConfig;
  variablesMap?: Record<string, DatasetVariable>;
  sql?: string;
}

export interface DatasetRecordColumn {
  displayName: string;
  apiName: string;
}

export interface DatasetRecordFieldData {
  columnDisplayName: string;
  value: string;
}

export type DatasetRecordData = DatasetRecordFieldData[];
export interface DatasetRecord {
  columns: DatasetRecordColumn[];
  data: DatasetRecordData[];
  pageInfo: CursorPageInfo;
}

export interface DatasetRecordTable {
  data: Record<string, string>[];
  columns: Record<string, string | boolean>[];
  errorMessage: string;
  isLoading: boolean;
  lastRefreshDate?: Date;
}

export interface DatasetRecordTotal {}

export interface DataSource {
  datasetId: string;
  apiName: string;
  displayName?: string;
  description?: string;
  datasetType: DatasetTypes;
  alias?: string;
}

export interface DatasetFunction {
  name: string;
  displayName: string;
  description: string;
  aggregate: boolean;
  dataType: FieldDataType;
  functionInputDataTypes: FieldDataType[];
}

export interface VariableValue {
  datasetId?: string;
  datasetName?: string; // api name of the dataset / entity
  datatype: string;
  defaultValue: string;
  defaultValueType: DatasetTypes;
  additionalParamForDefaultVal?: Record<string, string>;
}

export interface DatasetVariable {
  apiName?: string;
  displayName: string;
  datatype: string;
  datasetId?: string;
  required?: boolean;
  multiValueField?: boolean;
  updatable?: boolean;
  variableDefaultValue: VariableValue;
}

export enum FunctionParameterType {
  field = 'field',
  string = 'string',
}

export interface DataSourceFields extends DatasetFields {
  fieldId: string;
}

export interface DataCardWithDataset {
  datacard: DeepPartial<DataCard>;
  dataset: DeepPartial<Dataset>;
}

export type UnifiedDataCardAuthoringMode = 'DATASET_ONLY' | 'DATACARD_WITH_DATASET';

export type UsedByType = 'DASHBOARD' | 'DATASET' | 'DATACARD' | 'AIASSISTED' | 'THOUGHT_SPOT_DATASET';

export interface UsedByItem {
  id: string;
  name: string;
  type: UsedByType;
  author: string;
  nestedDraft?: boolean;
  draftStatus?: DraftStatuses;
}

export interface LastVisitedDashboard {
  lastVisitedDashboardId: string;
  useNestedDraft: boolean;
}

export interface DatasetExportJob {
  userName: string;
  requestedTime: string;
  expiredTime: string;
  numberOfRecords: number;
  status: 'PENDING' | 'INPROGRESS' | 'COMPLETED' | 'ERROR' | 'CANCELLED';
  exportJobId: string;
  expiryStatus: boolean;
}

export interface DatasetReadData {
  dataset: Dataset;
  pageCursor: DatasetPagination;
  previousTotalCount?: number;
}

export interface DatasetPagination {
  cursor: string | undefined;
  pageSize: number;
  direction: PaginationDirection;
}

export interface SharingDashboardPayload {
  dashboardId: string;
  emails: string[];
  expiryDate?: string;
  message?: string;
}

export interface SharingDashboardResponse {
  errorMessage: string | null;
  recipientEmailId: string;
}

export interface AllowedDomains {
  domains: string[];
}

export interface SharingDetailsPayload {
  cursor?: string;
  dashboardId: string;
  direction: string;
  pageSize: number;
  predicate?: string;
}

export interface SharingDetailsResponse {
  pageInfo: {
    end: string;
    hasMore: boolean;
    hasPrevious: boolean;
    start: string;
    totalCount: number;
  };
  shareDetailsRecords: ShareDetailsRecord[];
}

export interface ShareDetailsRecord {
  emailId: string;
  expiryDate: string;
  lastVisitedDate: string;
  sharedItemId: string;
  status: 'PENDING' | 'OPENED' | 'NOT_OPENED' | 'EXPIRED';
}

export interface UpdateExpiryPayload {
  expiryDate: string;
  sharedItemId: string;
}

export interface ReshareResponse {
  recipientEmailId: string;
  sharedItem: {
    id: string;
    recipientsUserId: string;
    recipientsEmailId: string;
  };
  errorMessage: string;
}

export interface AllSharedDashboard {
  dashboardId: string;
  dashboardDiplayName: string;
  dashboardDescription?: string;
  expiredTime?: string;
  dashboardInstanceId?: string;
}

export type DashboardVariablePreferences = Record<string, Record<string, VariableValue>>;
