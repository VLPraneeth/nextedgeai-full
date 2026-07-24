//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
// Contains our spectrum reducers
//
import { combineReducers } from 'redux';

import { ApiState, reducer as api } from 'store/api';
import { AppState } from 'store/app/app.types';
import app from 'store/app/reducer';
import { ServiceCredentialsState, reducer as credential } from 'store/credentials/slice';
import { reducer as customAction } from 'store/custom-action/slice';
import { CustomActionState } from 'store/custom-action/types';
import { reducer as customSynapse } from 'store/custom-synapse/sdk/slice';
import { SDKCustomSynapseState } from 'store/custom-synapse/types';
import { DataQualityState, reducer as dataQuality } from 'store/data-quality';
import { DataStudioState, reducer as dataStudio } from 'store/data-studio';
import { reducer as entity } from 'store/entity';
import { EntityPipelineState } from 'store/entity-pipeline/types';
import { EntityState } from 'store/entity/types';
import { FastMapperState, reducer as fastMapper } from 'store/fast-mapper';
import { FieldPipelineState } from 'store/field-pipeline/types';
import fragment from 'store/fragment/reducer';
import { reducer as importedFiles } from 'store/imported-files/slice';
import { ImportedFilesState } from 'store/imported-files/types';
import { InsightStudioState, reducer as insightsStudio } from 'store/insights-studio/slice';
import { InstanceSlice, reducer as instance } from 'store/instances/slice';
import logs from 'store/logs/reducer';
import { LogsState } from 'store/logs/types';
import { NewDashboardState, reducer as newDashboard } from 'store/new-dashboard/slice';
import { reducer as organization, types as organizationTypes } from 'store/organization';
import { PicklistsState, reducer as picklist } from 'store/picklists';
import pipelineAction, { PipelineActionsState } from 'store/pipeline-actions';
import { PipelineErrorState, reducer as pipelineError } from 'store/pipeline-error/slice';
import pipelineFunction, { PipelineFunctionsState } from 'store/pipeline-functions';
import { reducer as pipeline } from 'store/pipeline/slice';
import { PipelineState } from 'store/pipeline/types';
import { reducer as quickStart } from 'store/quick-start/slice';
import { QuickStartState } from 'store/quick-start/types';
import { reducer as referenceData } from 'store/reference-data';
import { legacySchemaReducer, reducer as schemaSlice } from 'store/schema';
import { SchemaState } from 'store/schema/slice';
import { LegacySchemaState } from 'store/schema/types';
import { default as tag } from 'store/tags/reducer';
import { TagState } from 'store/tags/types';
import { reducer as test } from 'store/test';
import { default as user } from 'store/user/reducer';
import { UserState } from 'store/user/types';
import { reducer as validation } from 'store/validation/slice';
import { ValidationState } from 'store/validation/types';

import connector, { ConnectorState } from './connectorReducer';
import entityPipeline from './entityPipelineReducer';
import fieldPipeline from './fieldPipelineReducer';
import specter from './specterReducer';
import subscription from './subscriptionReducer';

const appReducer = combineReducers<{
  api: ApiState;
  app: AppState;
  connector: ConnectorState;
  credential: ServiceCredentialsState;
  customAction: CustomActionState;
  customSynapse: SDKCustomSynapseState;
  dataStudio: DataStudioState;
  dataQuality: DataQualityState;
  entity: EntityState;
  entityPipeline: EntityPipelineState;
  fastMapper: FastMapperState;
  fieldPipeline: FieldPipelineState;
  fragment: ReturnType<typeof fragment>;
  insightsStudio: InsightStudioState;
  instance: InstanceSlice;
  importedFiles: ImportedFilesState;
  logs: LogsState;
  newDashboard: NewDashboardState;
  organization: organizationTypes.OrganizationState;
  picklist: PicklistsState;
  pipeline: PipelineState;
  pipelineAction: PipelineActionsState;
  pipelineError: PipelineErrorState;
  pipelineFunction: PipelineFunctionsState;
  quickStart: QuickStartState;
  referenceData: ReturnType<typeof referenceData>;
  schema: LegacySchemaState;
  schemaSlice: SchemaState;
  specter: ReturnType<typeof specter>;
  subscription: ReturnType<typeof subscription>;
  tag: TagState;
  test: ReturnType<typeof test>;
  user: UserState;
  validation: ValidationState;
}>({
  api,
  app,
  connector,
  credential,
  customAction,
  customSynapse,
  dataQuality,
  dataStudio,
  entity,
  entityPipeline,
  fastMapper,
  fieldPipeline,
  fragment,
  insightsStudio,
  instance,
  importedFiles,
  logs,
  newDashboard,
  organization,
  picklist,
  pipeline,
  pipelineAction,
  pipelineError,
  pipelineFunction,
  quickStart,
  referenceData,
  schema: legacySchemaReducer,
  schemaSlice,
  specter,
  subscription,
  tag,
  test,
  user,
  validation,
} as const);

export type RootState = ReturnType<typeof appReducer>;

export default appReducer;
