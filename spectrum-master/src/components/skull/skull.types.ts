//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { CustomAction } from 'components/custom-action/types';
import { CompositeValue, CompositeValues } from 'components/inputs/composite/types';
import { ConditionProps } from 'components/inputs/condition';
import { InputContainerProps } from 'components/inputs/InputContainer';
import { InputWithLabelProps } from 'components/inputs/InputWithLabel';
import { InputDataType, OperatorValue, PicklistValue } from 'components/inputs/types';
import { HStackProps } from 'components/layout/hstack';
import { StackProps } from 'components/layout/stack';
import { FieldDataType } from 'components/types';
import { TextProps } from 'components/typography/Text';
import { FetchPicklistValuesParams } from 'store/picklists/thunks';
import { NodeConfiguration } from 'store/pipeline/types';
import { QuickStart } from 'store/quick-start/types';
import { ArrayToUnion } from 'utils/TypeUtils';

import { GraphNodeUIMetadata, PredicateConfiguration } from '../../pages/sync-studio/types';

export enum ConfigActionTypes {
  CHANGE = 'INPUT/CHANGE',
  CHANGE_CONFIG_INPUTS = 'CONFIG/CHANGE_INPUTS',
  CHANGE_CONFIG_STEPS = 'CONFIG/CHANGE_STEPS',
  INPUT_ERROR = 'INPUT/ERROR',
  LOAD_DYNAMIC_STEP = 'LOAD_DYNAMIC_STEP',
  NAVIGATE_TO_STEP = 'CONFIG/NAVIGATE_TO_STEP',
  NEXT_PAGE = 'STEP/NEXT',
  PREV_PAGE = 'STEP/PREV',
  SET_ERROR_MESSAGE = 'STEP/ERROR_MESSAGE',
  UPDATED_VALUES = 'CONFIG/UPDATED_VALUES',
  VALIDATE = 'STEP/VALIDATE',
}

export interface ConfigAction {
  type: ConfigActionTypes;
  configId?: string;
  value?: any; // TODO: Improve types
  configInputs?: any; // TODO: Improve types. This is SkullInput[] but seeing issues in the reducer
  configSteps?: any; // TODO: Improve types. This is SkullStep[] but seeing isssues in the redecuer.
  inputs?: SkullInput[];
  errorMessage?: string;
  stepNumber?: any;
  reset?: boolean;
  loadingDynamicData?: boolean;
  picklistValues?: Record<string, PicklistValue[] | OperatorValue[]>;
  values?: any;
}

export interface ConfigReducerState {
  configInputs: SkullInput[];
  configSteps: SkullStep[] | null;
  currentStep: number;
  stepsLoadingData: Record<string, boolean>;
  values?: any;
  inputs?: SkullInput[];
  graphNodeValue: GraphNodeUIMetadata<PredicateConfiguration>;
  picklistValues?: Record<string, PicklistValue[] | OperatorValue[]>;
  errorMessage?: string;
}

export interface SkullStep {
  stepName: string;
  fields: string[];
  applyStep?: boolean;
  preview?: boolean;
  closeStep?: boolean;
  customFooter?: boolean;
  next?: {
    buttonText?: string;
  };
  cancel?: {
    buttonText?: string;
  };
  finish?: {
    buttonText?: string;
  };
  layout?: ({ type: 'stack' } & StackProps) | ({ type: 'hstack' } & HStackProps);
}

export type ConfigValue = string | any[] | CompositeValues<CompositeValue<string>>;

export type ConfigFormValues = Record<string, { name: string; value: ConfigValue }>;

export type SerializedValues = Record<string, ConfigValue>;

// TODO: Change this to use AppConstants.SKULL_RENDER_TYPE instead of managing a
// separate list
export const skullRenderType = [
  'actionConfiguration',
  'columns',
  'composite',
  'confirmationInfoBox',
  'customActionReview',
  'httpCustomSynapse',
  'sdkCustomSynapse',
  'webhookCustomSynapse',
  'httpCustomSynapseEntity',
  'displayText',
  'infoBox',
  'jumpToStepLabel',
  'picklist',
  'pipelinePicker',
  'pipelinePickerPreview',
  'predicate',
  'quickStartInstallReview',
  'quickStartInstallErrorResolution',
  'skullColumns',
  'tab',
  'table',
  'datasetConfiguration',
  'variablesConfiguration',
  'fieldmergepolicyretainfield',
] as const;
export type SkullRenderType = ArrayToUnion<typeof skullRenderType>;

export interface SkullItem extends InputContainerProps {
  name: string;
  renderType: SkullRenderType;
  id: string;
  operatorType: string;
  rightType: ConditionProps;
  values?: PicklistValue[] | SkullPicklistGroup[];
}

export interface SkullPicklistGroup {
  renderType: SkullRenderType;
  picklistGroup: string;
  label: string;
  type: string;
  value: string;
}

export interface PicklistDependsOn {
  dependantField: string;
  dependantType: string;
}

export interface MetadataDependsOn extends Omit<PicklistDependsOn, 'dependantField'> {
  dependantField?: string;
  dependantFields: string[];
  dependantType: string;
  metadata: Record<string, any>;
}

export interface SkullInput
  extends Omit<NodeConfiguration, 'mapping' | 'dependsOn' | 'fieldSet' | 'label' | 'defaultValue' | 'datatype'>,
    InputWithLabelProps {
  id: string;
  name: string;
  datatype?: InputDataType | FieldDataType;
  renderType?: SkullRenderType;
  textProps?: TextProps;
  dependsOn?: MetadataDependsOn;
  implicit?: boolean;
  label?: string;
  defaultValue?: CompositeValues | string | null;
  helpSummary?: string;
  repeatable?: boolean;
  parentGroup?: string;
  layout?: 'row' | 'column';
  mapping?: {
    graphKey: string;
    configKey: string;
  };
  configuration?: SkullItem[]; // An array of InputContainerProps
  validation?: {
    required: boolean;
  };
  includeFormValues?: boolean;
}

export enum ConfigRenderer {
  QUICK_START_WIZARD = 'quickStartWizard',
  WIZARD = 'wizard',
  FORM = 'form',
  FULL_CONTENT_WIZARD = 'fullContentWizard',
  FULL_CONTENT_PANEL = 'fullContentPanel',
}

export enum ConfigContext {
  QUICK_START = 'QUICK_START',
  FUNCTION_ACTION = 'FUNCTION_ACTION',
  DEDUP_MERGE = 'DEDUP_MERGE',
}

export type SkullConfigValue = QuickStart | CustomAction | null;

export type FetchDynamicStepsParams = Record<string, string | number | undefined | null>;

export interface SkullConfigMetadata<T = SkullConfigValue> {
  nodeConfig: SkullConfig;
  configSteps: SkullStep[];
  configTitle: string;
  configInputs: SkullInput[];
  // Returning `any` here to support the useGetDynamicStepsMutation for the legacy quick start
  fetchDynamicSteps?: (params: FetchDynamicStepsParams) => Promise<Partial<SkullConfig>> | any;
  executeApplyStep?: (value: T) => Promise<any>;
  close?: () => void;
  configValue: T | null;
  groupConfiguration: {};
}

export interface SkullConfig {
  id: string;
  displayName: string;
  description: string;
  helpSummary: string;
  requirementsText?: string;
  name: string;
  configuration: SkullInput[];
  helpLink?: string;
  applyApi?: string;
  iconPath?: string | null;
  coreNode?: boolean;
  renderer: {
    renderType: ConfigRenderer;
    title: string;
    steps: SkullStep[];
  };
}

export interface SkullReactContext {
  values: Record<string, CompositeValue>;
  inputs: SkullInput[];
  steps: SkullStep[];
  currentStep: number;
  nodeConfig: SkullConfig;
  validationState: Record<string, any>;
  picklistValues: Record<string, any>;
  groupConfiguration: Partial<{
    title: string;
    implicit: boolean;
    datatype: InputDataType;
    name: string;
    label: string;
    iconPath: string;
    children: SkullItem[];
  }>;
  configTitle: string;
  loadingNextStep: boolean;
  stepsLoadingData: Record<string, boolean>;
  errorMessage?: string;
  validate: () => void;
  onChange: (value: any) => void;
  close: () => void;
  previous: () => void;
  next: () => void;
  finish: () => void;
  applyStep: (stayInCurrentStep?: boolean) => void;
  fetchPicklistValues: (params: FetchPicklistValuesParams) => void;
  graphNodeValue?: GraphNodeUIMetadata<PredicateConfiguration>;
  configInputs?: SkullInput[];
}
