import { fireEvent, render, screen, userEvent } from 'tests/helpers';

import { FieldValidator, FieldValidatorProps } from './FieldValidator';
const changeSpy = jest.fn();
const blurSpy = jest.fn();
const setValiditySpy = jest.fn();

const renderComponent = (
  validationOptions: FieldValidatorProps['validationOptions'],
  submitAttempted = false,
  value?: string
) =>
  render(
    <FieldValidator
      name="test-input"
      onChange={changeSpy}
      onBlur={blurSpy}
      validationOptions={validationOptions}
      submitAttempted={submitAttempted}
      value={value}
      setFieldValidity={setValiditySpy}
      render={(renderProps) => {
        return (
          <>
            <label>
              Test input
              <input onChange={renderProps.onChange} onBlur={renderProps.onBlur} aria-invalid={!renderProps.isValid} />
            </label>
            <p>{renderProps.errorMessage}</p>
          </>
        );
      }}
    />
  );

describe('FieldValidator', () => {
  it('calls props.onChange when change event fires', async () => {
    renderComponent({});

    await userEvent.type(screen.getByLabelText('Test input'), 'x');
    expect(changeSpy).toHaveBeenCalledTimes(1);
  });

  it('calls props.onBlur when blur event fires', () => {
    renderComponent({});

    fireEvent.blur(screen.getByLabelText('Test input'));
    expect(blurSpy).toHaveBeenCalledTimes(1);
  });

  it('does not validate until after first blur', async () => {
    renderComponent({ noSpecialChars: true });

    expect(screen.getByLabelText('Test input')).toBeValid();

    await userEvent.type(screen.getByLabelText('Test input'), 'invalid!');

    expect(screen.getByLabelText('Test input')).toBeValid();

    fireEvent.blur(screen.getByLabelText('Test input'));

    expect(screen.getByLabelText('Test input')).toBeInvalid();
  });

  it('forces validation whe `submitAttempted` = true', () => {
    renderComponent({ required: true }, true);

    expect(screen.getByLabelText('Test input')).toBeInvalid();
  });

  it('forces validation when a value is pre-populated', () => {
    renderComponent({ noSpecialChars: true }, false, 'test!');

    expect(screen.getByLabelText('Test input')).toBeInvalid();
  });

  it('calls setFieldValidity with name and valid state', async () => {
    renderComponent({ noSpecialChars: true });

    await userEvent.type(screen.getByLabelText('Test input'), 'test!');
    fireEvent.blur(screen.getByLabelText('Test input'));

    expect(setValiditySpy).toHaveBeenCalledWith('test-input', false);
  });

  const validationTests = [
    {
      testName: 'displays required field error when no value given to required field',
      validationOptions: { required: true },
      expectedError: 'This field is required',
      invalidInput: '',
      validInput: 'text',
    },
    {
      testName: 'validates special characters',
      validationOptions: { noSpecialChars: true },
      expectedError: 'Cannot contain special characters',
      invalidInput: 'some text!',
      validInput: 'some text',
    },
    {
      testName: 'validates no spaces',
      validationOptions: { noSpaces: true },
      expectedError: 'Cannot contain spaces',
      invalidInput: 'some text',
      validInput: 'some_text',
    },
  ];

  validationTests.forEach(({ testName, validationOptions, expectedError, invalidInput, validInput }) => {
    it(testName, async () => {
      renderComponent(validationOptions);

      if (invalidInput) {
        await userEvent.type(screen.getByLabelText('Test input'), invalidInput);
      }
      fireEvent.blur(screen.getByLabelText('Test input'));

      expect(screen.getByLabelText('Test input')).toBeInvalid();
      expect(screen.queryByText(expectedError)).toBeVisible();

      await userEvent.clear(screen.getByLabelText('Test input'));
      await userEvent.type(screen.getByLabelText('Test input'), validInput);

      expect(screen.getByLabelText('Test input')).toBeValid();
      expect(screen.queryByText(expectedError)).not.toBeInTheDocument();
    });
  });
});
