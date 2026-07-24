import produce from 'immer';
import { cloneDeep, filter, find, forEach, get, set } from 'lodash';

import { GraphNodeUIMetadata, PredicateConfiguration } from 'pages/sync-studio/types';
import { EMPTY_ARRAY } from 'store/constants';
import AppConstants from 'utils/AppConstants';
import { cleanupInputValue, getSaveValue } from 'utils/InputUtil';
import { findConfiguration, getGraphKey } from 'utils/NodeConfigUtil';
import { isNotUndefined } from 'utils/TypeUtils';
import { validate, VALIDATION_STATUS } from 'utils/ValidationUtil';

import {
  ConfigFormValues,
  ConfigReducerState,
  ConfigValue,
  SerializedValues,
  SkullInput,
  SkullStep,
} from './skull.types';

export const getDefaultValuesFromConfig = (inputs?: SkullInput[], currentValues: Record<string, any> = {}) => {
  const values: Record<string, any> = {};
  inputs?.forEach((input) => {
    const { name, defaultValue, defaultChecked } = input;
    if ((defaultValue ?? defaultChecked) && !currentValues[name]) {
      values[name] = {
        name,
        value: defaultValue ?? defaultChecked,
      };
    }
  });
  return values;
};

const getValuesFromFormData = (inputs?: SkullInput[], formValues?: Record<string, ConfigValue>) => {
  const values: ConfigFormValues = {};
  if (formValues?.['dataFormatType'] !== 'form') {
    return values;
  }
  inputs?.forEach((input) => {
    if (formValues[input.name]) {
      const { name } = input;
      values[name] = {
        name,
        value: formValues[input.name],
      };
    }
  });
  return values;
};

export const getDependantField = (name: string, inputs: SkullInput[]) => {
  // @ts-ignore
  return inputs.find((input) => input?.dependsOn && input.dependsOn?.dependantField === name);
};

export const getPicklistId = (value: string, dependantType: string) => {
  return `${value}/${dependantType}`;
};

/**
 * Filters all rows from the table component that have a value of `false` for
 * the `rowSelectionField` column
 */
export const removeUnselectedTableRows = (values: SerializedValues, inputs: SkullInput[]) =>
  produce(values, (draft) => {
    forEach(draft, (value, key) => {
      const inputForValue = find(inputs, { name: key });
      if (inputForValue?.datatype === AppConstants.INPUT_TYPE.TABLE) {
        const definedSelectRow = find(inputForValue.columnDefs, { rowSelectionField: true });
        if (definedSelectRow) {
          draft[key] = filter(value as any[], { [definedSelectRow.field]: true });
        }
      }
    });
  });

export const skullConfigReducerInit = (initial: ConfigReducerState) => {
  return {
    ...initial,
    errorMessage: '',
    inputs: getStepInputs(
      initial.currentStep,
      initial.configSteps,
      initial.configInputs,
      initial.graphNodeValue,
      initial.values
    ),
    values: {
      ...initial.values,
      ...getDefaultValuesFromConfig(initial.configInputs, initial.values),
      ...getValuesFromFormData(
        initial.configInputs,
        (initial.graphNodeValue as unknown) as Record<string, ConfigValue>
      ),
    },
  };
};

export const getNextStep = (currentStep: number) => currentStep + 1;

export const getSerializedValues = (formValues?: ConfigFormValues) => {
  const values: SerializedValues = {};
  if (formValues) {
    Object.keys(formValues).forEach((k) => {
      values[k] = formValues[k].value;
    });
  }
  return values;
};

export const getInputValue = (config: SkullInput, graphNodeValue: any = {}, values: Partial<ConfigValue> = {}) => {
  // In memory form values
  if (get(values, config.name)?.value) {
    return get(values, config.name)?.value;
  }

  // Form values coming from the server
  if (!config || !graphNodeValue) {
    return;
  }
  if (graphNodeValue?.dataFormatType === 'form') {
    return graphNodeValue[config.name];
  }

  // Graph values from the server
  const graphKey = getGraphKey(config);
  return get(values, config.name) || get(graphNodeValue, graphKey);
};

export const getStepInputs = (
  stepNumber: number,
  steps: SkullStep[] | null,
  inputs?: SkullInput[],
  graphNodeValue?: GraphNodeUIMetadata<PredicateConfiguration>,
  values?: ConfigFormValues
) => {
  const currentStep = steps?.[stepNumber];
  // const fSteps: SkullInput[] | undefined = currentStep?.fields
  const fSteps: any = currentStep?.fields
    .map((fieldName: string) => {
      const iConfig = find(inputs, (input) => input.name === fieldName);
      if (iConfig) {
        const inputValue = getInputValue(iConfig, graphNodeValue, values);
        const localConfig = cloneDeep(iConfig);
        return {
          // TODO: Fix downstream mutation and switch to produce
          ...localConfig,
          // We're deprecating the defaultValue prop. Keep it here
          // for backward compatiblity
          defaultValue:
            iConfig.name === inputValue?.name && inputValue?.value
              ? inputValue?.value
              : inputValue || iConfig.defaultValue,
          defaultChecked:
            iConfig.name === inputValue?.name && typeof inputValue?.value === 'boolean'
              ? inputValue?.value
              : inputValue ?? iConfig.defaultChecked,
          displayMode: currentStep.preview ? AppConstants.INPUT_DISPLAY_MODE.READONLY : undefined,
          disabled: currentStep.preview || iConfig.disabled,
          labelPosition: currentStep.preview ? 'left' : 'top',
          dependantValues: getSerializedValues(values),
          formValues: iConfig.includeFormValues ? values : undefined,
        };
      }
      return undefined;
    })
    .filter((f: SkullInput | undefined) => isNotUndefined(f));

  if (!fSteps) {
    return EMPTY_ARRAY;
  }

  const { PREDICATE, COMPOSITE } = AppConstants.INPUT_TYPE;
  // Temporary get the group fields for our only predicate
  Object.keys(fSteps).forEach((key) => {
    if (fSteps[key].datatype === PREDICATE) {
      fSteps[key].inputConfigs = inputs?.filter(
        (input) => input.fieldSet === fSteps[key].fieldSet && input.datatype !== PREDICATE
      );
    } else if (fSteps[key].datatype === COMPOSITE) {
      const c = fSteps[key].configuration;
      Object.keys(c).forEach((key) => {
        if (c[key].datatype === PREDICATE) {
          c[key].inputConfigs = inputs?.filter(
            (input) => input.fieldSet === c[key].fieldSet && input.datatype !== PREDICATE
          );
        }
      });
    }
  });
  return fSteps;
};

export const validateValues = (
  stepNumber: number,
  steps: SkullStep[] | null,
  inputs: SkullInput[],
  graphNodeValue: GraphNodeUIMetadata<PredicateConfiguration>,
  values: ConfigFormValues
) => {
  const inpts = getStepInputs(stepNumber, steps, inputs, graphNodeValue, values);
  let isValid = true;
  inpts.map((input: SkullInput) => {
    if (input.validation) {
      try {
        validate({ ...input.validation, label: input.label || input.name }, input.defaultValue);
        input.validateStatus = VALIDATION_STATUS.SUCCESS;
        input.help = '';
      } catch (e) {
        isValid = false;
        input.validateStatus = VALIDATION_STATUS.ERROR;
        input.help = (e as Error).message;
      }
    }
    return input;
  });
  return { isValid, inputs: inpts };
};

export const transformGraphNodeValues = (
  values: Record<string, any>,
  configInputs: SkullInput[] | undefined,
  originalGraphNodeValue: any
) => {
  const newGraphNodeValue = cloneDeep(originalGraphNodeValue);

  Object.keys(values).forEach((k) => {
    const config = findConfiguration(values[k].name, { configuration: configInputs });
    const graphKey = getGraphKey(config);
    const v = cleanupInputValue(config, getSaveValue(values[k]));
    set(newGraphNodeValue, graphKey, cloneDeep(v));
  });

  return newGraphNodeValue;
};
