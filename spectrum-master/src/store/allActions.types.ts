import { AppAction } from './app/appActions.types';
import { DataStudioActionType } from './data-studio';
import { EntityAction } from './entity/types';
import { LogsAction } from './logs/types';
import { OrganizationAction } from './organization/types';
import { SchemaAction } from './schema/types';
import { TagAction } from './tags/types';
import { TokensAction } from './tokens/types';
import { UserAction } from './user/types';

// Slices of state with missing action types are commented out
export type AllActionTypes =
  | AppAction
  // connector
  // credential
  // dashboard
  // datascore
  | DataStudioActionType
  // datastore
  | EntityAction
  // entityPipeline
  // fieldPipeline
  // instance
  // newDashboard
  // notification
  | OrganizationAction
  // picklist
  // pipeline
  // pipelineAction
  // pipelineFunction
  // referenceData
  // report
  | LogsAction
  | SchemaAction
  // schemaSlice
  // specter
  // subscription
  | TagAction
  | TokensAction
  | UserAction;
// fragment
// test
