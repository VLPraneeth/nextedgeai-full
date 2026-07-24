//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import userEvent from '@testing-library/user-event';

import { fireEvent, render } from 'tests/helpers';
import { init } from 'utils/i18nUtil';

import ConditionRightValue from '../ConditionRightValue';

describe('Condition right value', () => {
  beforeAll(() => init());

  test('should render default datatype string', async () => {
    const { container } = render(<ConditionRightValue name="testCondition" />);
    expect(container.querySelector('input[datatype="string"]')).toBeInTheDocument();
  });

  test('renders boolean datatype', async () => {
    const { container } = render(<ConditionRightValue name="testCondition" leftValue={{ datatype: 'boolean' }} />);
    expect(container.querySelector('input[type="checkbox"]')).toBeInTheDocument();
  });

  test('should be able to change value of string', async () => {
    const { container } = render(<ConditionRightValue name="testCondition" />);
    const rightInputEl: any = container.querySelector('input[datatype="string"]');

    expect(rightInputEl.value).toBe('');
    await userEvent.type(rightInputEl, 'syncari');
    expect(rightInputEl.value).toBe('syncari');
    expect(rightInputEl.value).not.toBe('');
  });

  test('should call on change when the right value changed', async (done) => {
    const shouldCall = (val: string) => {
      expect(val).toEqual({ value: 's', type: 'literal' });
      done();
    };

    // @ts-expect-error: onChange type mismatch
    const { container } = render(<ConditionRightValue name="testCondition" onChange={shouldCall} />);
    const rightInputEl = container.querySelector('input[datatype="string"]');
    if (rightInputEl) {
      await userEvent.type(rightInputEl, 's');
    }
  });

  test('should have check the checkbox for boolean inputs', async () => {
    const { container } = render(<ConditionRightValue name="testCondition" leftValue={{ datatype: 'boolean' }} />);
    const checkbox: any = container.querySelector('input[datatype="checkbox"]');
    if (checkbox) {
      fireEvent.click(checkbox);
      expect(checkbox.checked).toBe(true);
    }
  });

  test('should render picklist', async () => {
    const { container } = render(<ConditionRightValue name="testCondition" leftValue={{ datatype: 'picklist' }} />);
    expect(container.querySelector('div.synri-auto-complete')).toBeInTheDocument();
  });

  test('should render textarea with tokens', async () => {
    // Unfortunately Slate uses contenteditable which isn't supported by JSDOM
    // which react-testing-library uses so we can't test editing the values of
    // token values. See https://stackoverflow.com/a/65539375/4280755
    const { container } = render(<ConditionRightValue name="testCondition" leftValue={{ datatype: 'textarea' }} />);
    expect(container.querySelector('div.tokens-textarea-container')).toBeInTheDocument();
  });
});
