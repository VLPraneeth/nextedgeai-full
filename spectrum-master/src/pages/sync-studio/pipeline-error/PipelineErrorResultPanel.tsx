import { Empty } from 'antd';
import { ChangeEvent } from 'react';

import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputFilter from 'components/InputFilter';
import { useEnhancedDispatch } from 'hooks/redux';
import { showPipelineErrorResultsPanel } from 'store/pipeline-error/slice';
import { ENTITY_DRAWER_HEIGHT_OFFSET, FIELD_DRAWER_HEIGHT_OFFSET } from 'styles/style.constants';

import { getErrorText, usePipelineError } from './PipelineError.hooks';
import { PipelineErrorResultsItem } from './PipelineErrorResultsItem';
import { PipelineErrorResultsSection } from './PipelineErrorResultsSection';

export const PipelineErrorResultPanel = withI18n(() => {
  const dispatch = useEnhancedDispatch();

  const {
    resultsPanelVisible: visible,
    filteredErrors,
    warnings,
    errors,
    fieldId,
    hasWarningError,
    setFilterText,
    pipelineErrors,
  } = usePipelineError({});
  const { tn } = useI18nContext();

  return (
    <DrawerPanel
      visible={visible && hasWarningError}
      additionalHeightOffset={fieldId ? FIELD_DRAWER_HEIGHT_OFFSET : ENTITY_DRAWER_HEIGHT_OFFSET}
      title={tn('panel_title')}
      onClose={() => dispatch(showPipelineErrorResultsPanel(false))}>
      <div className="pipeline-error-result">
        <InputFilter
          placeholder={tn('filter_results')}
          onChange={(evt: ChangeEvent<HTMLInputElement>) => setFilterText(evt.target.value)}
        />

        {!filteredErrors?.length ? (
          <Empty description={tn('no_results')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <PipelineErrorResultsSection
            title={getErrorText({ errorCount: errors?.length ?? 0, warningCount: warnings?.length ?? 0 })}>
            {filteredErrors?.map((result, index) => (
              <PipelineErrorResultsItem key={index} result={result} pipelineErrors={pipelineErrors} />
            ))}
          </PipelineErrorResultsSection>
        )}
      </div>
    </DrawerPanel>
  );
}, 'PipelineErrorResultPanel');
