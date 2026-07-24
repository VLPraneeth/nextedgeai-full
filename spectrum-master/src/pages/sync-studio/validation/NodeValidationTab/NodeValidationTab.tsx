import { useI18nContext, withI18n } from 'components/I18nProvider';
import { ScrollableArea } from 'components/scrollable-area/ScrollableArea';
import { ValidationResult } from 'store/validation/types';

import { NodeValidationItem } from '../NodeValidationItem';
import './NodeValidationTab.less';

export interface NodeValidationTabProps {
  errors: ValidationResult[];
  warnings: ValidationResult[];
}

export const NodeValidationTab = withI18n(({ errors, warnings }: NodeValidationTabProps) => {
  const { tn } = useI18nContext();

  const isNodeInvalid = errors.length + warnings.length > 0;

  return isNodeInvalid ? (
    <div className="node-validation-tab-container">
      <ScrollableArea>
        <div className="node-validation-tab-header">
          <span>
            {tn('validation_error_count', {
              numberOfErrors: errors.length,
              numberOfWarnings: warnings.length,
            })}
          </span>
          <div className="node-validation-tab-divider" />
        </div>
        {[...errors, ...warnings].map((result: ValidationResult) => (
          <NodeValidationItem result={result} />
        ))}
      </ScrollableArea>
    </div>
  ) : (
    <div className="node-validation-tab-empty-state">
      <span>{tn('success')}</span>
    </div>
  );
}, 'NodeValidationTab');
