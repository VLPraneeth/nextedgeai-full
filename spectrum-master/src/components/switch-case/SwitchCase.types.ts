import { FilterProps } from 'components/inputs/filter';
import { FilterValue } from 'components/inputs/types';

import { SwitchCaseValue } from './SwitchCaseComposite';

export interface SupportedDatatype {
  label: string;
  value: string;
}

export interface CaseConfig {
  datatypes: SupportedDatatype[];
  predicate: {
    fieldValues: FilterProps['fieldValues'];
    defaultValue?: FilterValue['predicates'];
  };
}

export interface CaseValue {
  caseId?: string;
  caseName?: string;
  value?: string;
  datatype?: string;
  multivalued?: boolean;
  predicate?: FilterValue['predicates'];
  doNotMatchBlank?: boolean;
}

export type DefaultCaseValue = Omit<CaseValue, 'caseName' | 'predicate'>;

export type SwitchCaseInputValue = Omit<CaseValue, 'predicate'>;

export interface SwitchCaseConfigurationValue {
  case?: {
    cases?: SwitchCaseValue['cases'];
  };
}
