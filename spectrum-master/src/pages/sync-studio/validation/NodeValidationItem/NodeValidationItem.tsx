import { capitalize } from 'lodash';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { TextTag } from 'components/text-tag';
import { ValidationResultType, ValidationResult } from 'store/validation/types';

import './NodeValidationItem.less';

export interface NodeValidationItemProps {
  result: ValidationResult;
}

export const NodeValidationItem = withI18n(({ result }: NodeValidationItemProps) => {
  const { tn } = useI18nContext();

  return (
    <div className="node-validation-item-container">
      <div className="node-validation-item-header">
        <TextTag
          text={capitalize(result.type)}
          color={result.type === ValidationResultType.ERROR ? 'red' : 'orange'}
          size="md"
        />
        {result.type === ValidationResultType.WARNING && (
          <span className="node-validation-item-navigation">{tn('dismiss')}</span>
        )}
      </div>
      <div className="node-validation-item-result">{result.message}</div>
    </div>
  );
}, 'NodeValidationItem');
