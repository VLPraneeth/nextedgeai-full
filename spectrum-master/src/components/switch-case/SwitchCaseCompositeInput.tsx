import cx from 'classnames';
import { useCallback } from 'react';

import { fetchPicklistValues } from 'actions/picklistActions';
import { FetchPicklistParams } from 'components/inputs/condition';
import Filter from 'components/inputs/filter';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { FilterValue } from 'components/inputs/types';
import { Spacer, Stack } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { FetchPicklistValuesParams } from 'store/picklists/thunks';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { CaseConfig, CaseValue, SwitchCaseInputValue } from './SwitchCase.types';
import { SwitchCaseInput } from './SwitchCaseInput';

import './SwitchCaseCompositeInput.scss';

const tn = tNamespaced('SwitchCaseInput');

const INPUT_TYPE = AppConstants.INPUT_TYPE;

const { READONLY: DISPLAY_READ_ONLY } = AppConstants.INPUT_DISPLAY_MODE;

export interface SwitchCaseCompositeInputValue {
  name?: string;
  switchCase?: CaseValue;
  switchCasePredicate?: FilterValue['predicates'];
  switchCaseDoNotMatchBlank?: boolean;
}

export interface SwitchCaseCompositeInputProps {
  name: string;
  cases: CaseConfig;
  caseValue: CaseValue;
  value?: SwitchCaseCompositeInputValue;
  defaultValue?: SwitchCaseCompositeInputValue;
  predicate: FilterValue['predicates'];
  displayMode?: string;
  onChange: (caseValue: SwitchCaseCompositeInputValue) => {};
}

export const SwitchCaseCompositeInput = ({
  name,
  cases,
  caseValue,
  value,
  defaultValue,
  onChange,
  displayMode,
  ...rest
}: SwitchCaseCompositeInputProps) => {
  const dispatch = useEnhancedDispatch();
  const picklistValues = useEnhancedSelector((state) => state.picklist.picklistValues);

  const onChangeHandler = useCallback(
    (value: SwitchCaseCompositeInputValue) => {
      onChange?.(value);
    },
    [onChange]
  );

  const onSwitchCaseChangeHandler = useCallback(
    (switchCaseInputValue: SwitchCaseInputValue) => {
      onChangeHandler?.({
        switchCase: switchCaseInputValue,
        switchCasePredicate: defaultValue?.switchCasePredicate,
        switchCaseDoNotMatchBlank: defaultValue?.switchCaseDoNotMatchBlank,
        name,
      });
    },
    [defaultValue?.switchCaseDoNotMatchBlank, defaultValue?.switchCasePredicate, name, onChangeHandler]
  );

  const onDoNotMatchBlankChangeHandler = useCallback(
    (checked: boolean) => {
      onChangeHandler?.({
        switchCase: defaultValue?.switchCase,
        switchCasePredicate: defaultValue?.switchCasePredicate,
        switchCaseDoNotMatchBlank: checked,
        name,
      });
    },
    [defaultValue?.switchCase, defaultValue?.switchCasePredicate, name, onChangeHandler]
  );

  const onPredicateChange = useCallback(
    (_: string, __: string, predicate: any) => {
      onChangeHandler?.({
        switchCase: defaultValue?.switchCase,
        switchCasePredicate: predicate,
        switchCaseDoNotMatchBlank: defaultValue?.switchCaseDoNotMatchBlank,
        name,
      });
    },
    [defaultValue?.switchCase, defaultValue?.switchCaseDoNotMatchBlank, name, onChangeHandler]
  );

  const { switchCasePredicate: predicate, switchCase, switchCaseDoNotMatchBlank } =
    (displayMode === AppConstants.INPUT_DISPLAY_MODE.READONLY ? value : defaultValue) || {};
  const { defaultValue: _, ...predicateProps } = cases?.predicate;

  const fetchPicklist = (param: FetchPicklistParams) =>
    dispatch(fetchPicklistValues(param as FetchPicklistValuesParams));

  return (
    <Stack spacing="z" className={cx('switch-case-composite-input')}>
      <SwitchCaseInput
        datatypes={cases.datatypes}
        defaultValue={switchCase as SwitchCaseInputValue}
        displayMode={displayMode}
        onChange={onSwitchCaseChangeHandler}
      />
      <Spacer y="xs" />
      <Filter
        name="switchCasePredicate"
        onChange={onPredicateChange}
        // @ts-ignore
        defaultValue={predicate}
        // @ts-ignore
        value={predicate}
        displayMode={displayMode}
        onDelete={() => {}}
        fetchPicklistValues={fetchPicklist}
        picklistValues={picklistValues}
        allowUserToken
        {...predicateProps}
      />
      <InputWithLabel
        className="switch-case-composite-input__blank-values"
        displayMode={displayMode}
        disabled={displayMode === DISPLAY_READ_ONLY}
        label={tn('do_not_match_blank')}
        tooltip={tn('do_not_match_blank_tooltip')}
        checked={switchCaseDoNotMatchBlank}
        onChange={(evt: React.ChangeEvent<HTMLInputElement>) => {
          onDoNotMatchBlankChangeHandler(evt.target.checked);
        }}
        datatype={INPUT_TYPE.CHECKBOX}
      />
    </Stack>
  );
};
