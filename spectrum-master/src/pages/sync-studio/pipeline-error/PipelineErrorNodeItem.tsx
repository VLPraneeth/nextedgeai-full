import { useI18nContext, withI18n } from 'components/I18nProvider';
import { TextTag } from 'components/text-tag';
import { PipelineSyncError, PipelineSyncWarning } from 'store/pipeline-error/types';

import './PipelineErrorNodeItem.scss';

export interface PipelineErrorNodeItemProps {
  result: PipelineSyncError | PipelineSyncWarning;
}

export const PipelineErrorNodeItem = withI18n(({ result }: PipelineErrorNodeItemProps) => {
  const { tc } = useI18nContext();

  return (
    <div className="pipeline-error-node">
      <div className="pipeline-error-node--header">
        <TextTag
          text={'errorType' in result ? tc('warning') : tc('error')}
          color={'errorType' in result ? 'orange' : 'red'}
          size="md"
        />
      </div>
      <div className="pipeline-error-node--message">{result.errorMessage}</div>
    </div>
  );
}, 'PipelineErrorNodeItem');
