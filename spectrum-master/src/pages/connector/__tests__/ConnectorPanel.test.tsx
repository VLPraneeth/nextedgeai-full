import { getMultipleEmptyConnectorMetadata } from 'store/connectors';
import { render, screen, userEvent } from 'tests/helpers';
import { tNamespaced } from 'utils/i18nUtil';

import ConnectorPanel from '../ConnectorPanel';

const tn = tNamespaced('ConnectorPanel');

describe('ConnectorPanel', () => {
  it('should render elements in the connector metadata', async () => {
    const connectorMetadata = getMultipleEmptyConnectorMetadata();
    render(<ConnectorPanel connectorMetadata={connectorMetadata} />);

    expect(await screen.findByText(connectorMetadata[0].displayName || '')).toBeInTheDocument();
  });

  it('should render help menu under when a helpUrl is provided', async () => {
    const helpUrl = 'https://support.syncari.com/hc/en-us/articles/360056102571-Synapse-Coming-Soon-';
    const connectorMetadata = getMultipleEmptyConnectorMetadata({ helpUrl });
    render(<ConnectorPanel connectorMetadata={connectorMetadata} />);

    window.open = jest.fn();

    const menuButton = screen.queryByRole('button');
    if (menuButton) {
      await userEvent.click(menuButton);
    }

    const helpButton = await screen.findByText(tn('help'));
    if (helpButton) {
      await userEvent.click(helpButton);
    }

    expect(window.open).toHaveBeenCalledWith(helpUrl);
  });
});
