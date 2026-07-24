import { SkullInput } from 'components/skull';

// what we get from Arcade
export interface ArcadePipelineFunction {
  id: string;
  hidden: boolean;
  description: string;
  displayName: string;
  helpPath: string | null;
  iconPath: string;
  name: string;
}

export interface BaseFunctionConfiguration {
  datatype: string;
  helpSummary: string;
  implicit: boolean;
  name: string;
  renderType: string;
  value: string;
}

export interface EdgeOptions {
  datatype: 'complex';
  defaultValue: any;
  helpSummary: null | string;
  implicit: boolean;
  isDynamic: boolean;
  label: null | string;
  name: 'edgeOptions';
  options: Record<string, string | boolean>;
  readOnly: boolean;
  required: boolean;
  supported: boolean;
  edgeType?: string;
}

// what we use in Spectrum
export interface PipelineFunction extends ArcadePipelineFunction {
  title: ArcadePipelineFunction['name'];
  key: ArcadePipelineFunction['id'];
  iconAlt: ArcadePipelineFunction['name'];
  icon: ArcadePipelineFunction['iconPath'];
  configuration?: (BaseFunctionConfiguration | EdgeOptions | SkullInput)[];
}

export type PipelineFunctionsState = {
  entityPipelineFunctions: PipelineFunction[];
  entityPipelineFunctionsError: undefined | Error;
  entityPipelineFunctionsFetching: boolean;
  fieldPipelineFunctions: PipelineFunction[];
  fieldPipelineFunctionsError: undefined | Error;
  fieldPipelineFunctionsFetching: boolean;
};
