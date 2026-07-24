import { isNumber } from 'lodash';
import { useCallback, useEffect, useReducer } from 'react';

import { setNodeConfig, showNodeConfigModal } from 'actions/entityPipelineActions';
import { fetchPicklistValues } from 'actions/picklistActions';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { EMPTY_ARRAY, EMPTY_OBJECT } from 'store/constants';
import { FetchPicklistValuesParams } from 'store/picklists/thunks';
import { selectConfigRenderer, selectNodeConfigName, selectSelectedGraphNode } from 'store/pipeline/selectors';
import {
  useExecuteQuickStartMutation,
  useGetDynamicStepsMutation,
  useGetQuickStartsLegacyQuery,
} from 'store/quick-start-legacy/api';
import { RequestExceptionType } from 'utils/AjaxUtil';

import { useConfirmQuickStartExecuteModal } from './skull.hooks';
import {
  ConfigAction,
  ConfigActionTypes,
  ConfigRenderer,
  SkullConfig,
  SkullInput,
  SkullReactContext,
  SkullStep,
} from './skull.types';
import skullConfigReducer from './skullConfigReducer';
import {
  getDependantField,
  getNextStep,
  getPicklistId,
  getSerializedValues,
  removeUnselectedTableRows,
  skullConfigReducerInit,
  transformGraphNodeValues,
  validateValues,
} from './skullConfigReducer.utils';

import { SkullConfigMetadata } from '.';

/**
 * Create a skull context based on the metadata provided. Generic T type defined
 * the config value (i.e. QuickStart, CustomAction, etc.)
 */
const useSkullConfig = <T>(metadata: SkullConfigMetadata<T>): SkullReactContext => {
  let {
    configInputs,
    configSteps,
    configTitle,
    configValue,
    fetchDynamicSteps,
    groupConfiguration,
    nodeConfig,
  } = metadata;

  let selectedGraphNode = useSelector(selectSelectedGraphNode);

  const picklistValues = useSelector((state) => state.picklist.picklistValues);
  const configRenderer = useSelector(selectConfigRenderer);
  const configName = useSelector(selectNodeConfigName);

  let [fetchDynamicStepsLegacyQuickStart] = useGetDynamicStepsMutation();
  fetchDynamicSteps = fetchDynamicSteps ?? fetchDynamicStepsLegacyQuickStart;

  let [executeLegacyQuickStart] = useExecuteQuickStartMutation();
  const executeApplyStep = metadata?.executeApplyStep || executeLegacyQuickStart;

  const confirmExecute = useConfirmQuickStartExecuteModal();

  const isLegacyQuickStart = configRenderer === ConfigRenderer.QUICK_START_WIZARD;
  const { data: quickStarts } = useGetQuickStartsLegacyQuery(undefined, { skip: !isLegacyQuickStart });

  if (isLegacyQuickStart) {
    const quickStart = (quickStarts as SkullConfig[])?.find(
      (quickStart: SkullConfig) => quickStart.name === configName
    );
    if (quickStart) {
      nodeConfig = quickStart;
      configSteps = quickStart.renderer.steps;
      configTitle = quickStart.renderer.title;
      configInputs = quickStart.configuration;
    }
    selectedGraphNode = { nodeType: '', metadata: {} };
    groupConfiguration = {};
  }

  const reduxDispatch = useDispatch();

  const validationState = {};

  const [state, dispatch] = useReducer(
    skullConfigReducer,
    {
      configInputs,
      configSteps,
      currentStep: 0,
      // TODO: We should rename graphNodeValue to configValue since the value
      // may be a quick start, custom action, etc.
      graphNodeValue: configValue as any,
      picklistValues,
      stepsLoadingData: EMPTY_OBJECT,
    },
    skullConfigReducerInit
  );
  const configId = nodeConfig?.id;
  const { inputs, currentStep, values } = state;

  useEffect(
    () =>
      dispatch({
        type: ConfigActionTypes.UPDATED_VALUES,
        picklistValues,
        values,
      }),
    [picklistValues, values]
  );

  const onChange = useCallback(
    (value: any) => {
      dispatch({ type: ConfigActionTypes.CHANGE, value, configId });
      // Trigger an picklist query when one of the fields is depending on this
      const dependantField = getDependantField(value.name, configInputs);
      if (dependantField && value.value && dependantField.dependsOn?.dependantType) {
        const id = getPicklistId(value.value, dependantField.dependsOn.dependantType);
        if (!picklistValues[id]) {
          reduxDispatch(
            fetchPicklistValues({
              id,
              dependantId: value.value,
              dependantType: dependantField.dependsOn?.dependantType,
            })
          );
        }
      }
    },
    [configId, configInputs, picklistValues, reduxDispatch]
  );

  const close = useCallback(() => {
    if (metadata?.close) {
      metadata.close();
    } else {
      reduxDispatch(showNodeConfigModal(false));
    }
  }, [reduxDispatch, metadata]);

  const previous = useCallback(() => dispatch({ type: ConfigActionTypes.PREV_PAGE }), [dispatch]);
  const validate = useCallback(() => dispatch({ type: ConfigActionTypes.VALIDATE }), [dispatch]);

  const finish = () => {
    if (configSteps?.[currentStep]?.applyStep) {
      applyStep();
    }
    if (configSteps?.[currentStep]?.closeStep) {
      close();
      return;
    }
    const { isValid, inputs } = validateValues(
      state.currentStep,
      state.configSteps,
      state.configInputs,
      state.graphNodeValue,
      state.values
    );
    if (!isValid) {
      dispatch({ type: ConfigActionTypes.INPUT_ERROR, inputs });
      return;
    }

    if (values) {
      // TODO: The graphNodeValue may not exist if we're not tied to the graph
      // (i.e. quickstarts). We may need to add a different way to store data
      if (state.graphNodeValue) {
        // Transform to graph values before sending it over to save
        const graphNodeValue = transformGraphNodeValues(values, configInputs, state.graphNodeValue);

        // TODO: Save the label as well.
        const { configuration, label } = graphNodeValue;
        reduxDispatch(
          setNodeConfig({
            nodeType: selectedGraphNode?.nodeType,
            configuration,
            label,
          })
        );
      }
    }
    close();
  };

  const applyStep = useCallback(
    async (stayInCurrentStep?: boolean) => {
      const { isValid, inputs } = validateValues(
        state.currentStep,
        state.configSteps,
        state.configInputs,
        state.graphNodeValue,
        state.values
      );
      if (!isValid) {
        dispatch({ type: ConfigActionTypes.INPUT_ERROR, inputs });
        return;
      }

      const nextStep = state.currentStep + 1;

      if (stayInCurrentStep !== true) {
        dispatch({
          type: ConfigActionTypes.LOAD_DYNAMIC_STEP,
          stepNumber: nextStep,
          loadingDynamicData: true,
        });
      }

      const rawParams = getSerializedValues(state.values);
      const params = removeUnselectedTableRows(rawParams, state.configInputs);
      if (Object.keys(params).length) {
        try {
          await executeApplyStep({ ...params, quickStartName: nodeConfig.name } as any);

          if (stayInCurrentStep === true) {
            return;
          }
          dispatch({ type: ConfigActionTypes.NEXT_PAGE });

          dispatch({
            type: ConfigActionTypes.LOAD_DYNAMIC_STEP,
            stepNumber: nextStep,
            loadingDynamicData: false,
          });
        } catch (exception) {
          dispatch({
            type: ConfigActionTypes.SET_ERROR_MESSAGE,
            errorMessage: (exception as RequestExceptionType).data.message,
          });
        }
      }
    },
    [
      executeApplyStep,
      nodeConfig?.name,
      state.configInputs,
      state.configSteps,
      state.currentStep,
      state.graphNodeValue,
      state.values,
    ]
  );

  const getDynamicStep = useCallback(
    async (stepNumber?: number, skipNext?: boolean) => {
      const params = getSerializedValues(state.values);
      if (Object.keys(params).length) {
        try {
          const newStepNumber = stepNumber ?? getNextStep(currentStep);

          dispatch({
            type: ConfigActionTypes.LOAD_DYNAMIC_STEP,
            stepNumber: newStepNumber,
            loadingDynamicData: true,
          });

          if (fetchDynamicSteps) {
            const result = await fetchDynamicSteps({
              ...params,
              quickStartName: nodeConfig.name,
              stepNumber: newStepNumber,
            }).unwrap();

            if (result?.configuration?.length) {
              const actions: ConfigAction[] = [];
              const configInputs = (state.configInputs as SkullInput[]).map((config) => {
                let foundIdx = -1;
                const hasUpdate = (result.configuration as SkullInput[]).find((newInput, idx) => {
                  if (newInput.name === config.name) {
                    foundIdx = idx;
                  }
                  return newInput.name === config.name;
                });
                if (hasUpdate && foundIdx > -1) {
                  // Remove the value of the replaced input
                  const foundConfig = (result.configuration as SkullInput[])[foundIdx];
                  actions.push({
                    type: ConfigActionTypes.CHANGE,
                    value: {
                      name: foundConfig.name,
                      value: foundConfig.defaultValue,
                    },
                    // Reset the value of the input if the default value is blank
                    reset: !foundConfig.defaultValue,
                    configId: undefined,
                  });
                  return foundConfig;
                }
                return config;
              });
              [
                {
                  type: ConfigActionTypes.CHANGE_CONFIG_INPUTS,
                  configInputs,
                },
                ...actions,
              ].forEach((action) => {
                dispatch(action);
              });
            }
            if (result?.steps?.length) {
              const configSteps = (state.configSteps as SkullStep[]).map((step) => {
                let foundIdx = -1;
                const hasUpdate = (result.steps as SkullStep[]).find((newInput, idx) => {
                  if (newInput.stepName === step.stepName) {
                    foundIdx = idx;
                  }
                  return newInput.stepName === step.stepName;
                });
                if (hasUpdate && foundIdx > -1) {
                  return (result.steps as SkullStep[])[foundIdx];
                }
                return step;
              });
              dispatch({
                type: ConfigActionTypes.CHANGE_CONFIG_STEPS,
                configSteps,
              });
            }
          }
          if (!skipNext) {
            dispatch({ type: ConfigActionTypes.NEXT_PAGE });
          }
          dispatch({
            type: ConfigActionTypes.LOAD_DYNAMIC_STEP,
            stepNumber: newStepNumber,
            loadingDynamicData: false,
          });
        } catch (exception) {
          // TODO: Type error
          // @ts-ignore
          dispatch({ type: ConfigActionTypes.SET_ERROR_MESSAGE, errorMessage: exception.data.message });
        }
      }
    },
    [currentStep, nodeConfig?.name, state.values, fetchDynamicSteps, state.configInputs, state.configSteps]
  );

  const refreshStep = useCallback(() => {
    getDynamicStep(currentStep, true);
  }, [currentStep, getDynamicStep]);

  const next = useCallback(() => {
    const currentConfigStep = state.configSteps?.[currentStep];
    const nextConfirmed = () => {
      if (currentConfigStep?.applyStep) {
        applyStep();
      } else {
        dispatch({ type: ConfigActionTypes.NEXT_PAGE });
      }
    };

    if (currentConfigStep?.confirm) {
      confirmExecute(
        currentConfigStep.confirm.title,
        currentConfigStep.confirm.message,
        currentConfigStep.confirm.okButtonText,
        () => nextConfirmed()
      );
    } else if (state.configSteps?.[getNextStep(currentStep)]?.dynamicSteps) {
      getDynamicStep();
    } else {
      nextConfirmed();
    }
  }, [confirmExecute, applyStep, state.configSteps, currentStep, getDynamicStep]);

  const navigateToStep = useCallback(
    (stepNumber?: number) => {
      // When navigateToStep is advancing to the next step, use the next()
      // function to get the same logic and loading experience
      if (!isNumber(stepNumber)) {
        return next();
      }

      // Don't bother loading dynamic step data if we're reviewing a previous screen
      if (stepNumber > currentStep) {
        const newStepIsDynamic = state.configSteps?.[stepNumber]?.dynamicSteps;
        newStepIsDynamic && getDynamicStep(stepNumber, true);
      }

      dispatch({
        type: ConfigActionTypes.NAVIGATE_TO_STEP,
        stepNumber,
      });
    },
    [currentStep, getDynamicStep, next, state.configSteps]
  );

  return {
    values,
    inputs: inputs.map((input: SkullConfig) => ({ ...input, refreshStep, navigateToStep })),
    steps: state.configSteps || EMPTY_ARRAY,
    stepsLoadingData: state.stepsLoadingData,
    currentStep,
    loadingNextStep: !!(state.stepsLoadingData as Record<string, boolean>)[currentStep + 1],
    nodeConfig,
    validationState,
    validate,
    onChange,
    errorMessage: state.errorMessage,
    close,
    previous,
    next,
    finish,
    applyStep,
    fetchPicklistValues: (params: FetchPicklistValuesParams) => reduxDispatch(fetchPicklistValues(params)),
    picklistValues,
    groupConfiguration,
    configTitle,
    graphNodeValue: state.graphNodeValue,
    configInputs,
  };
};

export default useSkullConfig;
