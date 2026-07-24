import { ReactNode } from 'react';

import './PipelineErrorResultsSection.less';

interface PipelineErrorResultsSectionProps {
  title: string;
  children?: ReactNode;
}

export const PipelineErrorResultsSection = ({ title, children }: PipelineErrorResultsSectionProps) => {
  return (
    <div className="pipeline-error-results-section">
      <div className="pipeline-error-results-section--header">
        <span>{title}</span>
        <div className="pipeline-error-results-section--divider" />
      </div>
      <div className="pipeline-error-results-section--list">{children}</div>
    </div>
  );
};
