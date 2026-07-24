import { useMatch } from '@reach/router';
import { Empty, Spin } from 'antd';
import { some } from 'lodash';
import { ChangeEvent, useEffect, useMemo, useState } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputFilter from 'components/InputFilter';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { selectValidationResultsPanelState } from 'store/validation/selectors';
import { showValidationResultsPanel } from 'store/validation/slice';
import { ValidationErrorLevel, ValidationMode, ValidationResult } from 'store/validation/types';
import { countValidationResults, getEntity, getFieldName, getNodeName } from 'store/validation/utils';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';

import { ValidationResultsItem } from '../ValidationResultsItem/ValidationResultsItem';
import { ValidationResultsSection } from '../ValidationResultsSection';

import './ValidationResultsPanel.less';

// Currently (6/24/22), the backend returns 3 different types of pipeline errors:
// global, entity, and atribute. This function sorts errors & warnings into
// these three categories for display by the UI.
const validationSorter = (results: ValidationResult[], validationMode: ValidationMode) => {
  const global: ValidationResult[] = [];
  const field: ValidationResult[] = [];
  const node: ValidationResult[] = [];

  results.forEach((result: ValidationResult) => {
    switch (result.level) {
      // For entity & field pipelines, errors/warnings with a level of "GLOBAL"
      // will be sorted into the `global` category.
      case ValidationErrorLevel.GLOBAL:
        global.push(result);
        break;
      // For entity & field pipelines, errors/warnings with a level of "ENTITY"
      // will be sorted into the `node` category.
      case ValidationErrorLevel.ENTITY:
        node.push(result);
        break;
      // For entity pipelines, errors/warings with a level of "ATTRIBUTE"
      // will be sorted into the `field` catetory.
      // For field pipelines, errors/warings with a level of "ATTRIBUTE"
      // will be sorted into the `node` category.
      case ValidationErrorLevel.ATTRIBUTE:
        validationMode === ValidationMode.ENTITY ? field.push(result) : node.push(result);
        break;
    }
  });

  return { global, field, node };
};

const validationFilter = (results: ValidationResult[], filter: string) => {
  return results.filter((result) => {
    return result.message.toLowerCase().indexOf(filter.toLowerCase()) >= 0;
  });
};

const sortValidationResults = (
  errors: ValidationResult[],
  warnings: ValidationResult[],
  validationMode: ValidationMode
) => {
  const errorResults = validationSorter(errors, validationMode);
  const warningsResults = validationSorter(warnings, validationMode);

  return {
    globalResults: [...errorResults.global, ...warningsResults.global],
    fieldResults: [...errorResults.field, ...warningsResults.field],
    nodeResults: [...errorResults.node, ...warningsResults.node],
  };
};

export const ValidationResultsPanel = withI18n(() => {
  const dispatch = useEnhancedDispatch();

  const {
    currentGraph,
    entities,
    entityPipeline,
    entityPipelineDraft,
    entityPipelineValidating,
    errors,
    fieldPipeline,
    fieldPipelineDraft,
    fieldPipelineValidating,
    validationMode,
    visible,
    warnings,
  } = useEnhancedSelector(selectValidationResultsPanelState);

  const { tn } = useI18nContext();

  const entityIdMatch = useMatch('/sync-studio/entity/:entityId/*');
  const entity = getEntity(entityIdMatch?.entityId ?? '', entities ?? EMPTY_ARRAY);

  const [globalResults, setGlobalResults] = useState<ValidationResult[]>(EMPTY_ARRAY);
  const [fieldResults, setFieldResults] = useState<ValidationResult[]>(EMPTY_ARRAY);
  const [nodeResults, setNodeResults] = useState<ValidationResult[]>(EMPTY_ARRAY);
  const [filterText, setFilterText] = useState('');

  const loading = entityPipelineValidating || fieldPipelineValidating;

  useEffect(() => {
    loading && setFilterText('');
  }, [loading]);

  const handleNodeResultItemSubtitle = (result: ValidationResult) => {
    const nodeId = result?.nodeId ?? '';

    switch (validationMode) {
      case ValidationMode.ENTITY: {
        const subtitleCandidate = getNodeName(
          nodeId,
          entityPipelineDraft ? entityPipelineDraft.nodes : entityPipeline?.nodes
        );
        return subtitleCandidate
          ? subtitleCandidate
          : getNodeName(nodeId, entityPipelineDraft ? currentGraph?.draft?.nodes : currentGraph?.nodes);
      }

      case ValidationMode.FIELD: {
        const subtitleCandidate = getNodeName(
          nodeId,
          fieldPipelineDraft ? fieldPipelineDraft.nodes : fieldPipeline?.nodes
        );

        return subtitleCandidate
          ? subtitleCandidate
          : getNodeName(nodeId, fieldPipelineDraft ? currentGraph?.draft?.nodes : currentGraph?.nodes);
      }
    }
  };

  useEffect(() => {
    if (validationMode) {
      const { globalResults, fieldResults, nodeResults } = sortValidationResults(errors, warnings, validationMode);
      setGlobalResults(globalResults);
      setFieldResults(fieldResults);
      setNodeResults(nodeResults);
    }
  }, [errors, warnings, validationMode]);

  const filteredResults = useMemo(() => {
    if (filterText) {
      return {
        globalResults: validationFilter(globalResults, filterText),
        fieldResults: validationFilter(fieldResults, filterText),
        nodeResults: validationFilter(nodeResults, filterText),
      };
    }

    return {
      globalResults,
      fieldResults,
      nodeResults,
    };
  }, [fieldResults, filterText, globalResults, nodeResults]);

  const activeFilterWithoutResults = Boolean(filterText) && !some(filteredResults, (val) => val.length > 0);

  return (
    <DrawerPanel
      visible={visible}
      additionalHeightOffset={validationMode === 'FIELD' ? FIELD_DRAWER_HEIGHT_OFFSET : ENTITY_DRAWER_HEIGHT_OFFSET}
      title={tn('panel_title')}
      onClose={() => {
        dispatch(showValidationResultsPanel(false));
      }}>
      {loading ? (
        <div className="validation-results-pending">
          <Spin spinning />
          <span>{tn('validation_in_progress')}</span>
        </div>
      ) : errors.length === 0 && warnings.length === 0 ? (
        <div className="validation-results-successful">
          <span>{tn('success')}</span>
          <span>{tn('success_message')}</span>
        </div>
      ) : (
        <div className="validation-results-container">
          <InputFilter
            placeholder={tn('filter_results')}
            onChange={(evt: ChangeEvent<HTMLInputElement>) => setFilterText(evt.target.value)}
          />

          {activeFilterWithoutResults && <Empty description={tn('no_results')} image={Empty.PRESENTED_IMAGE_SIMPLE} />}

          {filteredResults.globalResults.length > 0 && (
            <ValidationResultsSection title={tn('global_panel_section', countValidationResults(globalResults))}>
              {filteredResults.globalResults.map((result: ValidationResult, index) => (
                <ValidationResultsItem key={index} result={result} subtitle={tn('global_subtitle')} />
              ))}
            </ValidationResultsSection>
          )}

          {filteredResults.fieldResults.length > 0 && (
            <ValidationResultsSection title={tn('field_panel_section', countValidationResults(fieldResults))}>
              {filteredResults.fieldResults.map((result: ValidationResult, index) => (
                <ValidationResultsItem
                  key={index}
                  result={result}
                  subtitle={getFieldName(result?.targetId ?? '', entity?.fields ?? EMPTY_ARRAY)}
                />
              ))}
            </ValidationResultsSection>
          )}

          {filteredResults.nodeResults.length > 0 && (
            <ValidationResultsSection title={tn('node_panel_section', countValidationResults(nodeResults))}>
              {filteredResults.nodeResults.map((result: ValidationResult, index) => (
                <ValidationResultsItem
                  key={index}
                  entityId={entityIdMatch?.entityId}
                  entityPipelineId={entityPipelineDraft ? entityPipelineDraft.id : entityPipeline?.id}
                  result={result}
                  subtitle={handleNodeResultItemSubtitle(result)}
                />
              ))}
            </ValidationResultsSection>
          )}
        </div>
      )}
    </DrawerPanel>
  );
}, 'ValidationResultsPanel');
