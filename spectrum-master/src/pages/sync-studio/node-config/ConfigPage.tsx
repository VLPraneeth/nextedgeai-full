//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import cx from 'classnames';
import { useCallback } from 'react';

import { useI18nContext } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack, Stack } from 'components/layout';
import { SkullInput, useSkullConfigContext } from 'components/skull';
import TabPanelSpin from 'components/TabPanelSpin';
import { getDefaultValue } from 'utils/InputUtil';
import { isNotNullOrUndefined } from 'utils/TypeUtils';
import './ConfigPage.less';

const StableInputContainer = ({ input }: { input: SkullInput }) => {
  const { fetchPicklistValues, onChange, picklistValues } = useSkullConfigContext();

  const name = input.name;

  const onChangeHandler = useCallback(
    (value: any, ...rest: any) => {
      // Predicates... :(
      if (rest?.[1]) {
        onChange.call(undefined, { value: rest[1], name });
      } else if (typeof value === 'string') {
        // Select
        onChange.call(undefined, { value, name });
      } else if (isNotNullOrUndefined(value?.currentTarget?.value)) {
        // html input
        onChange.call(undefined, { name, value: value.currentTarget.value });
      } else if (typeof value?.target?.checked === 'boolean') {
        onChange({
          name,
          value: value?.target?.checked,
        });
      } else if (value?.target?.value) {
        onChange.call(undefined, { name, value: value.target.value });
      } else {
        // Move the name and value transformation in this level instead of
        // doing it in the component level
        // Need to revisit this so it will not break dedup merge.
        onChange({
          name,
          value,
          ...value,
        });
      }
    },
    [name, onChange]
  );

  return (
    <InputWithLabel
      key={`config-page-input-${input.name}`}
      onChange={onChangeHandler}
      fetchPicklistValues={fetchPicklistValues}
      picklistValues={picklistValues}
      tooltip={input.helpSummary}
      values={input.values}
      defaultValue={getDefaultValue(input?.defaultValue)}
      {...input}
    />
  );
};

export interface ConfigPageProps {
  className?: string;
}

const ConfigPage = ({ className }: ConfigPageProps) => {
  const { currentStep: currentStepIndex, errorMessage, inputs, loadingNextStep, steps } = useSkullConfigContext();

  const { tc } = useI18nContext();

  const currentStep = steps?.[currentStepIndex] || {};

  const { type, className: containerClassName, ...layoutProps } = currentStep.layout || {};
  const Component = type === 'hstack' ? HStack : Stack;

  return (
    <TabPanelSpin spinning={loadingNextStep} tip={tc('loading_step')}>
      <Component className={cx('synri-config-page-container', className, containerClassName)} {...layoutProps}>
        {!!errorMessage && (
          <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
            {errorMessage}
          </InlineMessage>
        )}
        {inputs?.map((input) => (
          <StableInputContainer key={input.id} input={input} />
        ))}
      </Component>
    </TabPanelSpin>
  );
};

export default ConfigPage;
