//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { render, screen, userEvent } from 'tests/helpers';

import DrawerPanel, { DrawerPanelProps } from '../DrawerPanel';

const testTitle = 'Sample Content',
  testContent =
    'Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus accumsan neque vitae eleifend placerat. Pellentesque in egestas lorem, eu viverra tortor';

const renderDrawer = (props?: Partial<DrawerPanelProps>) =>
  render(
    <DrawerPanel {...props}>
      <div>{testContent}</div>
    </DrawerPanel>
  );

describe('Drawer Panel', () => {
  test('Renders only children by default', async () => {
    renderDrawer();

    expect(screen.queryByTestId('DrawerPanelLandingZone')).not.toBeInTheDocument();
    expect(screen.queryByTestId('DrawerPanelFooter')).not.toBeInTheDocument();
    expect(screen.queryByText(testTitle)).not.toBeInTheDocument();

    expect(await screen.findByText(testContent)).toBeVisible();
  });

  test('Renders a title when provided', async () => {
    renderDrawer({ title: testTitle });

    expect(screen.queryByText(testTitle)).toBeVisible();
  });

  test('Renders a landing zone when specified', async () => {
    renderDrawer({ useLandingZone: true });

    expect(screen.queryByTestId('DrawerPanelLandingZone')).toBeVisible();
  });

  test('Renders a footer zone when provided', async () => {
    renderDrawer({ footer: <div>Footer</div> });

    expect(screen.queryByText('Footer')).toBeVisible();
  });

  test('Calls close function when close button clicked', async () => {
    const onClose = jest.fn();

    renderDrawer({ onClose });

    expect(onClose).not.toHaveBeenCalled();
    await userEvent.click(screen.getByLabelText('Close'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
