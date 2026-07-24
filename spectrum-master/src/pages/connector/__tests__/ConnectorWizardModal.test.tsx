import { getDefaultConnectorState, getEmptyConnectorMetadata } from 'store/connectors';
import { render, screen } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import ConnectorWizardModal from '../ConnectorWizardModal';

const tc = tNamespaced('Common');

describe('ConnectorWizardModal', () => {
  test('should render the footer with next and cancel buttons', async () => {
    render(<ConnectorWizardModal />, {
      testState: {
        connector: getDefaultConnectorState({
          modalConnectorMetadata: getEmptyConnectorMetadata(),
        }),
      },
    });

    const nextButton = await screen.findByText(tc('next'));
    expect(nextButton).toBeInTheDocument();

    const cancelButton = await screen.findByText(tc('cancel'));
    expect(cancelButton).toBeInTheDocument();
  });

  it('should render support article when available', async () => {
    const helpUrl = 'https://support.syncari.com/hc/en-us/articles/360056102571-Synapse-Coming-Soon-';

    render(<ConnectorWizardModal />, {
      testState: {
        connector: getDefaultConnectorState({
          modalConnectorMetadata: getEmptyConnectorMetadata({ helpUrl }),
        }),
      },
    });

    const supportArticleLink = await screen.findByText('support article');

    expect(supportArticleLink).toBeInTheDocument();
    expect(supportArticleLink).toHaveProperty('href', helpUrl);
  });

  it('should not render support article when unavailable', async () => {
    const helpUrl = null;

    render(<ConnectorWizardModal />, {
      testState: {
        connector: getDefaultConnectorState({
          modalConnectorMetadata: getEmptyConnectorMetadata({ helpUrl }),
        }),
      },
    });

    const supportArticleLink = screen.queryByText('support article');

    expect(supportArticleLink).toBeNull();
  });
});
