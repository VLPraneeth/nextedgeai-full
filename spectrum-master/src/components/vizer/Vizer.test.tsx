import { dataCard1 } from 'mocks/fixtures/insights';
import { Globals } from 'react-spring';

import { render, screen } from 'tests/helpers';

import { Vizer } from './Vizer';

Globals.assign({ skipAnimation: true });

describe('Vizer', () => {
  it('displays error for an unsupported visualization', () => {
    // @ts-ignore, intentionally using a fake type
    render(<Vizer dataCardContent={{ configuration: { vizType: 'fake' } }} />);

    expect(screen.getByText('Something went wrong')).toBeVisible();
  });

  it('displays error for missing data', () => {
    // @ts-ignore, intentionally using incomplete props
    render(<Vizer dataCardContent={{ configuration: { vizType: 'BAR' }, data: [] }} />);

    expect(screen.getByText('No data')).toBeVisible();
  });

  it('renders normally with valid card', () => {
    render(<Vizer dataCardContent={dataCard1.contents} graphHeight={100} />);

    expect(screen.queryByTestId('data-card-error')).not.toBeInTheDocument();
  });
});
