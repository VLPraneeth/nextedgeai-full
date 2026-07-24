import { SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID } from 'components/skull';
import configureAppStore from 'store/configureStore';
import { render, screen, userEvent } from 'tests/helpers';

import SDKCustomSynapseFileUpload from './SDKCustomSynapseFileUpload';

describe('CustomSynapseFileUpload', () => {
  const mountingNode = document.createElement('div');

  beforeEach(() => {
    // Setup the node for the portal to mount the custom footer
    mountingNode.setAttribute('id', SKULL_CUSTOM_FOOTER_PORTAL_ROOT_ID);
    mountingNode.setAttribute('class', 'synri-config-footer');
    document.body.append(mountingNode);
  });

  afterEach(() => {
    mountingNode.remove();
  });

  test('When no id is present show the Create Custom Synapse button', async () => {
    render(
      <SDKCustomSynapseFileUpload
        id=""
        defaultValue={
          {
            id: '',
            name: '',
            displayName: '',
          } as any
        }
        onChange={() => {}}
        refreshStep={() => {}}
        navigateToStep={() => {}}
      />,
      {
        store: configureAppStore(),
      }
    );

    const nextButton = (await screen.findByText('Create Custom Synapse')).closest('button');
    expect(nextButton).toBeVisible();
    expect(nextButton).not.toBeDisabled();
  });

  test('When the custom synapse status is ERROR show the error message', async () => {
    render(
      <SDKCustomSynapseFileUpload
        id="sampleId"
        defaultValue={
          {
            // The id is used in the customSynapse handlers file (msw) to return
            // the status of "ERROR"
            id: 'ERROR',
            name: 'custom_synapse_name',
            displayName: 'Custom Synapse Name',
          } as any
        }
        onChange={() => {}}
        refreshStep={() => {}}
        navigateToStep={() => {}}
      />,
      {
        store: configureAppStore(),
      }
    );

    const statusNode = await screen.findByText('An error occurred. Please update your synapse files and retry.');
    expect(statusNode).toBeVisible();

    // Next button should be visible but disabled until a change is made
    const nextButton = (await screen.findByText('Next')).closest('button');
    expect(nextButton).toBeVisible();
    expect(nextButton).toBeDisabled();

    const displayName = await screen.findByDisplayValue('Custom Synapse Name');
    await userEvent.type(displayName, 'New display name');

    // Update button should be visible and enabled after a change is made
    const updateButton = (await screen.findByText('Update draft')).closest('button');
    expect(updateButton).toBeVisible();
    expect(updateButton).not.toBeDisabled();
  });
});
