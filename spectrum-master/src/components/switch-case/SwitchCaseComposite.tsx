import ObjectID from 'bson-objectid';
import cx from 'classnames';
import { useCallback, useEffect, useMemo, useState } from 'react';

import Composite from 'components/inputs/composite';
import { Stack } from 'components/layout';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { CaseConfig, CaseValue, DefaultCaseValue } from './SwitchCase.types';
import { SwitchCaseCompositeInputValue } from './SwitchCaseCompositeInput';
import { SwitchCaseInput } from './SwitchCaseInput';
import './SwitchCaseComposite.scss';

const { READONLY: DISPLAY_READ_ONLY } = AppConstants.INPUT_DISPLAY_MODE;

const tn = tNamespaced('SwitchCaseInput');

export interface SwitchCaseValue {
  cases?: CaseValue[];
  defaultCaseValue?: DefaultCaseValue;
}

export interface SwitchCaseCompositeProps {
  className?: string;
  value: SwitchCaseValue;
  defaultValue: SwitchCaseValue;
  onChange?: (value: SwitchCaseValue) => void;
  cases: CaseConfig;
  displayMode?: string;
}

export interface CompositeCaseValue {
  repeatId: string;
  switchCase: SwitchCaseCompositeInputValue;
}

export interface CompositeCaseProps {
  compositeValues: CompositeCaseValue[];
  name: string;
}

const transformToCases = (compositeCases: CompositeCaseProps): CaseValue[] => {
  const caseValues: CaseValue[] = [];

  compositeCases?.compositeValues?.forEach((compositeCase) => {
    if (compositeCase.switchCase) {
      const { caseId, caseName, value, datatype, multivalued } = compositeCase?.switchCase?.switchCase || {};
      caseValues.push({
        caseId,
        caseName,
        value,
        datatype,
        multivalued,
        predicate: compositeCase?.switchCase?.switchCasePredicate,
        doNotMatchBlank: compositeCase?.switchCase?.switchCaseDoNotMatchBlank,
      });
    }
  });

  return caseValues;
};

interface TransformToCompositeResult {
  caseComposite?: CompositeCaseProps;
  defaultCaseValue?: DefaultCaseValue;
}

const transformToCompositeCases = (switchCaseValue: SwitchCaseValue): TransformToCompositeResult => {
  const compositeValues: CompositeCaseValue[] = [];
  switchCaseValue?.cases?.forEach(({ predicate: switchCasePredicate, doNotMatchBlank, ...switchCase }) => {
    compositeValues.push({
      repeatId: ObjectID.generate(),
      switchCase: {
        switchCase,
        switchCasePredicate,
        switchCaseDoNotMatchBlank: doNotMatchBlank,
      },
    });
  });
  return {
    caseComposite: {
      name: 'caseComposite',
      compositeValues,
    },
    defaultCaseValue: switchCaseValue.defaultCaseValue || {},
  };
};

export const SwitchCaseComposite = ({
  className,
  onChange,
  defaultValue,
  displayMode,
  ...restProps
}: SwitchCaseCompositeProps) => {
  const [defaultCaseValue, setDefaultCaseValue] = useState<SwitchCaseValue['defaultCaseValue']>({});
  const [switchCaseCompositeValue, setSwitchCaseCompositeValue] = useState<CompositeCaseProps>();
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    if (!initialized && defaultValue && !switchCaseCompositeValue) {
      const { caseComposite, defaultCaseValue } = transformToCompositeCases(defaultValue);
      setSwitchCaseCompositeValue(caseComposite);
      setDefaultCaseValue(defaultCaseValue);
      setInitialized(true);
    }
    if (defaultValue === null && !initialized) {
      setInitialized(true);
    }
  }, [defaultValue, initialized, switchCaseCompositeValue]);

  const onDefaultChangeHandler = useCallback((defaultCaseValue: DefaultCaseValue) => {
    setDefaultCaseValue(defaultCaseValue);
  }, []);

  const onCompositeChangeHandler = useCallback((cases: CompositeCaseProps) => {
    setSwitchCaseCompositeValue(cases);
  }, []);

  const onChangeHandler = useCallback(
    (value: SwitchCaseValue) => {
      onChange?.(value);
    },
    // Ignoring on change due to onChange handler keep changing from the composite
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  useEffect(() => {
    if (switchCaseCompositeValue && Object.keys(switchCaseCompositeValue).length) {
      const transformedCases = transformToCases(switchCaseCompositeValue);
      const value = {
        defaultCaseValue,
        cases: transformedCases,
      };
      onChangeHandler?.(value);
    }
  }, [defaultCaseValue, onChangeHandler, switchCaseCompositeValue]);

  // @ts-ignore
  const { value: _, displayValue: __, ...myRestProps } = restProps;
  const switchConfig = useMemo(
    () => [
      {
        ...myRestProps,
        displayValue: switchCaseCompositeValue,
        name: 'switchCase',
        datatype: AppConstants.INPUT_TYPE.CASE_PREDICATE,
        displayMode,
        label: null,
      },
    ],
    [displayMode, myRestProps, switchCaseCompositeValue]
  );

  return (
    <Stack
      className={cx(
        'switch-case-composite',
        className,
        displayMode === DISPLAY_READ_ONLY && 'switch-case-composite--read-only'
      )}>
      {initialized && (
        <Composite
          displayMode={displayMode}
          addText={tn('add_case')}
          key="caseComposite"
          name="caseComposite"
          onChange={onCompositeChangeHandler}
          configuration={switchConfig}
          defaultValue={switchCaseCompositeValue}
          value={switchCaseCompositeValue}
          repeatable
        />
      )}
      {initialized && (
        <SwitchCaseInput
          displayMode={displayMode}
          isDefault
          datatypes={restProps.cases.datatypes}
          defaultValue={defaultCaseValue || {}}
          onChange={onDefaultChangeHandler}
        />
      )}
    </Stack>
  );
};
