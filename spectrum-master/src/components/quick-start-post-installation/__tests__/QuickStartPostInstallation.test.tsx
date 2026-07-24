import { render, screen } from 'tests/helpers';

import { QuickStartPostInstallation } from '..';

describe('QuickStartPostInstallation', () => {
  it('should render post install message', async () => {
    const postInstallMessage = 'message';

    render(<QuickStartPostInstallation postInstallMessage={postInstallMessage} />, {
      testState: {},
    });

    expect(await screen.findByText(postInstallMessage)).toBeInTheDocument();
  });
});
