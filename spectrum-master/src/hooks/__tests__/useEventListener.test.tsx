import { render, screen, userEvent } from 'tests/helpers';

import useEventListener from '../useEventListener';

const handler = jest.fn();

const CustomHandlerComponent = () => {
  useEventListener('click', handler);

  return <button>Click Text</button>;
};

describe('useEventListener', () => {
  it('should handle a click event', async () => {
    render(<CustomHandlerComponent />);

    const clickButton = await screen.findByText('Click Text');

    await userEvent.click(clickButton);

    expect(handler).toHaveBeenCalledTimes(1);
  });
});
