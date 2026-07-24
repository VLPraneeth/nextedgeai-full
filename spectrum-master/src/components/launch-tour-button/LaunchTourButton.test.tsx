import userflow from 'userflow.js';

import { InstanceType } from 'store/instances/slice';
import { render, screen, userEvent, waitFor } from 'tests/helpers';

import { LaunchTourButton } from './LaunchTourButton';

jest.mock('userflow.js', () => {
  return { start: jest.fn() };
});

const renderComponent = (instanceType: InstanceType = 'trial') =>
  render(<LaunchTourButton />, { testState: { user: { currentInstanceType: instanceType } } });

describe('LaunchTourButton', () => {
  it('renders in a trial instance', () => {
    renderComponent();

    expect(screen.queryByLabelText('Launch tour')).toBeVisible();
  });

  it('does not render in production instance', () => {
    renderComponent('production');

    expect(screen.queryByLabelText('Launch tour')).not.toBeInTheDocument();
  });

  it('does not render in sandbox instance', () => {
    renderComponent('sandbox');

    expect(screen.queryByLabelText('Launch tour')).not.toBeInTheDocument();
  });

  it('calls userflow start when clicked', async () => {
    renderComponent();

    await userEvent.click(screen.getByLabelText('Launch tour'));
    expect(userflow.start).toHaveBeenCalledTimes(1);
    expect(userflow.start).toHaveBeenCalledWith('c43ebd33-87f3-4c4b-9af6-88b796c203dd');
  });

  it('displays a tooltip on hover', async () => {
    renderComponent();

    expect(screen.queryByText('Launch tour')).not.toBeInTheDocument();

    await userEvent.hover(screen.getByLabelText('Launch tour'));

    await waitFor(() => expect(screen.queryByText('Launch tour')).toBeVisible());
  });
});
