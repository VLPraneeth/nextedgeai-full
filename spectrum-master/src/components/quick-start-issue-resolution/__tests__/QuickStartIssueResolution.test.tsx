import { render, screen, userEvent } from 'tests/helpers';

import { QuickStartIssueResolution } from '..';

describe('QuickStartIssueResolution', () => {
  it('should render title and message text', async () => {
    const successTitle = 'title';
    const successMessage = 'message';

    render(
      <QuickStartIssueResolution
        successTitle={successTitle}
        successMessage={successMessage}
        navigateToStep={() => {}}
      />,
      {
        testState: {},
      }
    );

    expect(await screen.findByText(successTitle)).toBeInTheDocument();
    expect(await screen.findByText(successMessage)).toBeInTheDocument();
  });

  it('should invoke the navigateToStep function when button is clicked', async () => {
    const successTitle = 'title';
    const successMessage = 'message';
    const navigateToStep = jest.fn();

    render(
      <QuickStartIssueResolution
        successTitle={successTitle}
        successMessage={successMessage}
        navigateToStep={navigateToStep}
      />,
      {
        testState: {},
      }
    );

    const button = await screen.findByText('Continue');

    await userEvent.click(button);

    expect(navigateToStep).toHaveBeenCalledTimes(1);
  });
});
