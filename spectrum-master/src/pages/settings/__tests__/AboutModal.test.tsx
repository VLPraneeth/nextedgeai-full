import { getEmptyUserState } from 'store/user';
import { mockedAjaxUtils, render, screen } from 'tests/helpers';

import AboutModal from '../AboutModal';

const AjaxUtils = mockedAjaxUtils();
jest.mock('utils/AjaxUtil');

const buildDate = '01/01/2020 00:00:00 UTC';
const gitSha1 = '732bf0sh';

const versionCall = jest.fn(() => Promise.resolve({ data: { gitSha1 } }));
AjaxUtils.get.mockImplementation(versionCall);

describe('AboutModal', () => {
  it('should render the AboutModal with build date and sha', async () => {
    render(<AboutModal />, {
      testState: {
        user: getEmptyUserState({
          versionMetadata: {
            buildDate,
            gitSha1,
          },
          timeZone: 'UTC',
        }),
      },
    });

    expect(await screen.findByText(buildDate)).toBeInTheDocument();
    expect(await screen.findByText(gitSha1)).toBeInTheDocument();
  });

  it('should fetch the version when mounted', async () => {
    render(<AboutModal />, {
      testState: {},
    });

    expect(versionCall).toHaveBeenCalledWith('/version');
  });
});
