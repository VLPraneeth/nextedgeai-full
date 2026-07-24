import { EntityType, NodeConfiguration, Scope } from '../pipeline/types';

export type PipelineAction = {
  configuration: NodeConfiguration[];
  description: string | null;
  displayName: string;
  dynamicConfig: boolean;
  engineType: string | null;
  helpPath: string;
  helpSummary: string;
  hidden: boolean;
  icon: string;
  iconAlt: string;
  iconPath: string;
  id: string;
  key: string;
  name: string;
  outputType: unknown;
  positionalParams: unknown[];
  renderer: unknown;
  scope: Scope;
  title: string;
  type: EntityType;
};

export type PipelineActionsState = {
  fieldPipelineActions: PipelineAction[];
  fieldPipelineActionsError: undefined | Error;
  fieldPipelineActionsFetching: boolean;
  entityPipelineActions: PipelineAction[];
  entityPipelineActionsError: undefined | Error;
  entityPipelineActionsFetching: boolean;
};
