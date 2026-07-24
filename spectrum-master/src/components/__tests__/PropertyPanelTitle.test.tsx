//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import PropertyPanelTitle from 'components/PropertyPanelTitle';
import { render, screen, fireEvent } from 'tests/helpers';

describe('PropertyPanelTitle', () => {
  const testTitleText = 'Test Title';

  it('renders title when passed', async () => {
    render(<PropertyPanelTitle title={testTitleText} />);

    expect(await screen.findByText(testTitleText)).toBeVisible();
  });

  it('calls close when close is passed and button is clicked', () => {
    const close = jest.fn();
    const { getByLabelText } = render(<PropertyPanelTitle title={testTitleText} onClose={close} />);

    fireEvent(
      getByLabelText('close panel'),
      new MouseEvent('click', {
        bubbles: true,
        cancelable: true,
      })
    );
    expect(close).toHaveBeenCalled();
  });

  it('shows element on screen when passed in icon prop', async () => {
    const iconTextContent = 'test div';
    const icon = <div>{iconTextContent}</div>;

    render(<PropertyPanelTitle title={testTitleText} icon={icon} />);

    expect(await screen.findByText(iconTextContent)).toBeVisible();
  });
});
