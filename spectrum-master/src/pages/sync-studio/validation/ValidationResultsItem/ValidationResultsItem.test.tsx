import { ValidationErrorLevel, ValidationResultType } from 'store/validation/types';
import { renderWithRouter, screen } from 'tests/helpers';

import { ValidationResultsItem, ValidationResultsItemProps } from './ValidationResultsItem';

const props: ValidationResultsItemProps = {
  result: {
    level: ValidationErrorLevel.GLOBAL,
    type: ValidationResultType.ERROR,
    message: 'I am error.',
  },
  subtitle: 'I am subtitle.',
};

const testState = {
  validation: {
    warnings: [],
  },
};

describe('ValidationResultItem', () => {
  it('should correctly display the result message', async () => {
    renderWithRouter(<ValidationResultsItem {...props} />, { testState });

    expect(await screen.findByText(props.result.message)).toBeVisible();
  });

  it('should correctly display the result subtitle', async () => {
    renderWithRouter(<ValidationResultsItem {...props} />, { testState });

    expect(await screen.findByText(props.subtitle ?? '')).toBeVisible();
  });

  it('should correctly display `Error` if the result is an error', async () => {
    renderWithRouter(<ValidationResultsItem {...props} />, { testState });

    expect(await screen.findByText('Error')).toBeVisible();
  });

  it('should correctly display `Warning` if the result is a warning', async () => {
    const modifiedProps: ValidationResultsItemProps = {
      ...props,
      result: {
        ...props.result,
        type: ValidationResultType.WARNING,
      },
    };

    renderWithRouter(<ValidationResultsItem {...modifiedProps} />, { testState });

    expect(await screen.findByText('Warning')).toBeVisible();
  });
});
