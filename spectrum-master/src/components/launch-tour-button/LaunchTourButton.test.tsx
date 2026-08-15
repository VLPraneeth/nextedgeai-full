import { render, screen, userEvent } from 'tests/helpers';
import { START_PRODUCT_TOUR_EVENT } from 'utils/GuidedDemo';

import { LaunchTourButton } from './LaunchTourButton';

const renderComponent = () => render(<LaunchTourButton />);

describe('LaunchTourButton', () => {
  it('is always available', () => {
    renderComponent();

    expect(screen.queryByLabelText('Product tour')).toBeVisible();
  });

  it('dispatches the internal tour event when clicked', async () => {
    const listener = jest.fn();
    window.addEventListener(START_PRODUCT_TOUR_EVENT, listener);
    renderComponent();

    await userEvent.click(screen.getByLabelText('Product tour'));
    expect(listener).toHaveBeenCalledTimes(1);
    window.removeEventListener(START_PRODUCT_TOUR_EVENT, listener);
  });
});
