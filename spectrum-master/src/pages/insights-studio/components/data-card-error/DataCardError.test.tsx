import { Globals } from 'react-spring';

import { render, screen } from 'tests/helpers';

import { DataCardError, DataCardErrorProps } from './DataCardError';

Globals.assign({ skipAnimation: true });

const renderComponent = (props?: DataCardErrorProps) => render(<DataCardError error={props?.error} />);

describe('DataCardError', () => {
  it('displays a generic error message by default', () => {
    renderComponent();

    expect(screen.getByText('Something went wrong')).toBeVisible();
  });
  it('can display a custom error message', () => {
    const customError = { title: 'Custom Error', body: 'Custom error text' };
    renderComponent({ error: customError });

    expect(screen.getByText(customError.title)).toBeVisible();
    expect(screen.getByText(customError.body)).toBeVisible();
  });
});
