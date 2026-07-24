import produce from 'immer';
import { cloneDeep, isBoolean, isNumber } from 'lodash';

import { EMPTY_OBJECT } from 'store/constants';

import { ConfigAction, ConfigActionTypes, ConfigReducerState, SkullInput } from './skull.types';
import { getDefaultValuesFromConfig, getNextStep, getStepInputs, validateValues } from './skullConfigReducer.utils';

const skullConfigReducer = (state: ConfigReducerState, action: ConfigAction) => {
  switch (action.type) {
    case ConfigActionTypes.NEXT_PAGE: {
      const { isValid, inputs } = validateValues(
        state.currentStep,
        state.configSteps,
        state.configInputs,
        state.graphNodeValue,
        state.values
      );
      if (!isValid) {
        return { ...state, inputs, errorMessage: '' };
      } else {
        const { currentStep, values } = state;
        const inputs = getStepInputs(
          getNextStep(currentStep),
          state.configSteps,
          state.configInputs,
          state.graphNodeValue,
          values
        );
        return {
          ...state,
          values: {
            ...values,
            ...getDefaultValuesFromConfig(inputs, values),
          },
          errorMessage: '',
          currentStep: getNextStep(currentStep),
          inputs,
        };
      }
    }
    case ConfigActionTypes.PREV_PAGE:
      const currentStep = state.currentStep - 1;
      return {
        ...state,
        errorMessage: '',
        currentStep,
        inputs: getStepInputs(currentStep, state.configSteps, state.configInputs, state.graphNodeValue, state.values),
      };
    case ConfigActionTypes.CHANGE_CONFIG_INPUTS: {
      return {
        ...state,
        configInputs: action.configInputs,
        inputs: getStepInputs(
          state.currentStep,
          state.configSteps,
          action.configInputs,
          state.graphNodeValue,
          state.values
        ),
      };
    }
    case ConfigActionTypes.CHANGE_CONFIG_STEPS: {
      return {
        ...state,
        configSteps: action.configSteps,
        // The steps may have different inputs so the inputs need to be updated as well
        inputs: getStepInputs(
          state.currentStep,
          action.configSteps,
          state.configInputs,
          state.graphNodeValue,
          state.values
        ),
      };
    }
    case ConfigActionTypes.CHANGE: {
      const { configId, value, reset } = action;
      const newValues = { ...state.values };
      if (reset) {
        delete newValues[value.name];
      } else {
        newValues[value.name] = {
          configId,
          ...cloneDeep(value),
        };
      }
      return {
        ...state,
        values: newValues,
      };
    }
    case ConfigActionTypes.NAVIGATE_TO_STEP: {
      const { values } = state;
      const inputs = getStepInputs(
        action.stepNumber,
        state.configSteps,
        state.configInputs,
        state.graphNodeValue,
        values
      );
      return {
        ...state,
        values: {
          ...values,
          ...getDefaultValuesFromConfig(inputs, values),
        },
        errorMessage: '',
        currentStep: action.stepNumber,
        inputs,
      };
    }
    case ConfigActionTypes.INPUT_ERROR:
      return {
        ...state,
        inputs: action.inputs,
      };
    case ConfigActionTypes.LOAD_DYNAMIC_STEP:
      return produce(state, (draft) => {
        if (isNumber(action.stepNumber) && isBoolean(action.loadingDynamicData)) {
          draft.stepsLoadingData[action.stepNumber] = action.loadingDynamicData;
        }
        draft.errorMessage = '';
      });

    case ConfigActionTypes.SET_ERROR_MESSAGE:
      return {
        ...state,
        stepsLoadingData: EMPTY_OBJECT,
        errorMessage: action.errorMessage,
      };
    case ConfigActionTypes.UPDATED_VALUES: {
      const newConfigInputs = (state.configInputs as SkullInput[])?.map((input) => {
        if (Object.keys(action.values)?.length && input.dependsOn) {
          // @ts-ignore
          const dependeeFieldValue = Object.values(action.values).find(
            (value: any) => value.name === input.dependsOn?.dependantField
          );
          if (dependeeFieldValue) {
            // @ts-ignore
            const picklistId = getPicklistId(dependeeFieldValue.value, input.dependsOn.dependantType);
            if (action.picklistValues?.[picklistId]) {
              return {
                ...input,
                values: action.picklistValues?.[picklistId],
              };
            }
          }
        }
        return input;
      });

      return {
        ...state,
        configInputs: newConfigInputs,
      };
    }
    default:
      return state;
  }
};

export default skullConfigReducer;
