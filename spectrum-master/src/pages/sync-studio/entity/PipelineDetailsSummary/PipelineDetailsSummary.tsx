import cx from 'classnames';
import { orderBy } from 'lodash';

import StatBlock from 'components/StatBlock';
import { tNamespaced } from 'utils/i18nUtil';

import { usePipelineDetailsSummary } from './PipelineDetailsSummary.hooks';

import './PipelineDetailsSummary.scss';

const tn = tNamespaced('PipelineDetails');
const ts = tNamespaced('SyncStudio');

export interface PipelineSummaryProps {
  className?: string;
}

export const PipelineDetailsSummary = ({ className }: PipelineSummaryProps) => {
  const { stats, total } = usePipelineDetailsSummary();

  return (
    <div className={cx(className, 'pipeline-details-summary')}>
      <span className="pipeline-details-summary__header">{ts('pipeline_summary')}</span>
      <div className="pipeline-details-summary__stats">
        <StatBlock label="Total" value={total} />
        {orderBy(Object.keys(stats)).map((status) => (
          <div key={status}>
            <StatBlock value={stats[status]} label={tn(`filter_${status}`)} />
          </div>
        ))}
      </div>
    </div>
  );
};
