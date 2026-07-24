import { useMatch } from '@reach/router';
import { Button } from 'antd';
import { useEffect } from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { TextTag } from 'components/text-tag';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useEffectOnValueChange from 'hooks/useEffectOnValueChange';
import usePreviousValue from 'hooks/usePreviousValue';
import { setTestPanelView } from 'store/test/actions';
import { TestPanelView } from 'store/test/types';
import { selectValidationToolbarState } from 'store/validation/selectors';
import {
  setErrors,
  setIsGotoBetweenFieldPipelines,
  setValidationMode,
  setWarnings,
  showValidationResultsPanel,
  showValidationToolbar,
} from 'store/validation/slice';
import { ValidationMode, ValidationResult } from 'store/validation/types';
import { getEntityName } from 'utils/EntityUtil';

import './ValidationToolbar.less';

const filterValidationResults = (validationResults: ValidationResult[]) => {
  return {
    errors: validationResults.filter((error: ValidationResult) => error.type === 'ERROR'),
    warnings: validationResults.filter((error: ValidationResult) => error.type === 'WARNING'),
  };
};

export interface ValidationToolbarProps {
  onValidate?: () => void;
  updateNodes: () => void;
}

export const ValidationToolbar = withI18n(({ onValidate, updateNodes }: ValidationToolbarProps) => {
  const dispatch = useEnhancedDispatch();

  const {
    entities,
    entityPipelineValidating,
    entityValidationErrors,
    errors,
    fieldPipeline,
    fieldPipelineValidating,
    fieldValidationErrors,
    selectedEntityNode,
    testPanelView,
    validationMode,
    validationResultsPanelVisible,
    visible,
    warnings,
  } = useEnhancedSelector(selectValidationToolbarState);

  const { tn } = useI18nContext();

  const entityIdMatch = useMatch('/sync-studio/entity/:entityId/*');
  const entityPipelineName = getEntityName(entityIdMatch?.entityId, entities);

  const handleClose = () => {
    dispatch(showValidationToolbar(false));
    dispatch(showValidationResultsPanel(false));
  };

  // cleanup
  useEffect(() => {
    return () => {
      // Without this check, navigating to one FP to another FP via the "Goto"
      // link
      if (!selectedEntityNode) {
        dispatch(setIsGotoBetweenFieldPipelines(false));
      }
    };
  }, [dispatch, selectedEntityNode]);

  // draw error labels on nodes when errors / warnings update
  useEffect(() => {
    if (visible) {
      updateNodes();
    }
  }, [errors, updateNodes, warnings, visible]);

  const previousSelectedEntityNode = usePreviousValue(selectedEntityNode);

  // close result panel when clicking on a node
  useEffectOnValueChange(() => {
    if (validationResultsPanelVisible) {
      if (selectedEntityNode !== previousSelectedEntityNode) {
        dispatch(showValidationResultsPanel(false));
      }
    }
  }, [dispatch, selectedEntityNode, validationResultsPanelVisible]);

  // switch validation modes
  useEffect(() => {
    if (entityPipelineValidating) {
      dispatch(setValidationMode(ValidationMode.ENTITY));
    } else if (fieldPipelineValidating) {
      dispatch(setValidationMode(ValidationMode.FIELD));
    }
  }, [entityPipelineValidating, fieldPipelineValidating, dispatch]);

  // Run validation anytime the toolbar becomes visible
  useEffectOnValueChange(() => {
    if (visible) {
      onValidate?.();
    }
  }, [visible]);

  // change the error & warning list depending on what validation mode we are in
  useEffect(() => {
    const errorsToSort =
      fieldValidationErrors && validationMode === ValidationMode.FIELD ? fieldValidationErrors : entityValidationErrors;

    if (!errorsToSort) {
      return;
    }

    const { errors, warnings } = filterValidationResults(errorsToSort);

    dispatch(setErrors(errors));
    dispatch(setWarnings(warnings));
  }, [entityValidationErrors, fieldValidationErrors, validationMode, dispatch]);

  const isValidatingFieldPipeline = validationMode === ValidationMode.FIELD && fieldPipeline;

  return visible && onValidate ? (
    <div className="validation-toolbar-container">
      <div className="validation-toolbar-state">
        {fieldPipelineValidating || entityPipelineValidating ? (
          <p>{tn('validation_in_progress')}</p>
        ) : (
          <p>
            {tn('validation_results')}
            <span>{isValidatingFieldPipeline ? fieldPipeline.name : entityPipelineName}</span>
          </p>
        )}
        {!entityPipelineValidating && !fieldPipelineValidating && (
          <>
            <TextTag
              color={errors.length === 0 ? 'green' : 'red'}
              text={tn('error_count', { numberOfErrors: errors.length })}
              size="sm"
            />
            {warnings.length > 0 && (
              <TextTag color="orange" text={tn('warning_count', { numberOfWarnings: warnings.length })} size="sm" />
            )}
          </>
        )}
      </div>

      <div className="right-group">
        <Button
          onClick={() => {
            // Close the test panel if we are showing the validation results panel.
            if (testPanelView !== TestPanelView.CLOSED && validationResultsPanelVisible === false) {
              dispatch(setTestPanelView(TestPanelView.CLOSED));
            }

            dispatch(showValidationResultsPanel(!validationResultsPanelVisible));
          }}>
          {validationResultsPanelVisible ? tn('hide_results') : tn('show_results')}
        </Button>
        <Button onClick={handleClose}>{tn('close')}</Button>
      </div>
    </div>
  ) : null;
}, 'ValidationToolbar');
