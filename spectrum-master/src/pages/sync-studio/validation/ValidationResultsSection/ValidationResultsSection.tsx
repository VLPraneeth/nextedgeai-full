import { ReactNode } from 'react';

import './ValidationResultsSection.less';

interface ValidationResultsSectionProps {
  title: string;
  children?: ReactNode;
}

export const ValidationResultsSection = ({ title, children }: ValidationResultsSectionProps) => {
  return (
    <div className="validation-results-section">
      <div className="validation-results-header">
        <span>{title}</span>
        <div className="validation-results-divider" />
      </div>
      <div className="validation-results">{children}</div>
    </div>
  );
};
