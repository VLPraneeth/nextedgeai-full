import { useI18nContext, withI18n } from 'components/I18nProvider';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';

import { usePipelineError } from './PipelineError.hooks';
import { PipelineErrorNodeItem } from './PipelineErrorNodeItem';

import './PipelineErrorTabResults.scss';

export interface PipelineErrorTabResultsProps {
  nodeId?: string;
}

export const PipelineErrorTabResults = withI18n(({ nodeId }: PipelineErrorTabResultsProps) => {
  const { t, tn } = useI18nContext();
  const { errors, warnings } = usePipelineError({ nodeId });

  const isNodeInvalid = errors.length + warnings.length > 0;

  return isNodeInvalid ? (
    <div className="pipeline-error-tab">
      <ScrollableArea>
        <div className="pipeline-error-tab--header">
          <span>
            {errors.length ? t('PipelineErrorState.count_error', { count: errors.length }) : ''}
            {warnings.length ? t('PipelineErrorState.count_warning', { count: warnings.length }) : ''}
          </span>
          <div className="pipeline-error-tab--divider" />
        </div>
        {errors.map((result) => result && <PipelineErrorNodeItem result={result} />)}
        {warnings.map((result) => result && <PipelineErrorNodeItem result={result} />)}
      </ScrollableArea>
    </div>
  ) : (
    <div className="pipeline-error-tab__empty-state">
      <span>{tn('no_error_warnings')}</span>
    </div>
  );
}, 'PipelineErrorTabResults');
