// @ts-nocheck
//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import * as EPActions from 'actions/entityPipelineActions';
import { fireEvent, userEvent, screen, renderWithRouter } from 'tests/helpers';

import PublishDraftModal from '../PublishDraftModal';

describe('publish pipeline', () => {
  test('PublishDraftModal renders', async () => {
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          publishDraftModalEntityId: '123',
        },
      },
    });
    expect((await screen.findByText('Publish Draft?')).textContent).toMatch(/Publish Draft\?/);
  });

  test('PublishDraftModal renders with list', async () => {
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          publishDraftModalEntityId: '123',
          fieldDraftSummary: {
            123: [
              {
                name: 'Account Name',
                id: '123',
              },
            ],
          },
        },
      },
    });
    expect((await screen.findByText('Account Name')).textContent).toMatch(/Account Name/);
  });

  test('PublishDraftModal renders with pipeline count', async () => {
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          publishDraftModalEntityId: '123',
          fieldDraftSummary: {
            123: [
              {
                name: 'Account Name',
                id: '123',
              },
              {
                name: 'Billing Address',
                id: '456',
              },
            ],
          },
        },
      },
    });
    expect((await screen.findByText(/Publishing this draft will/)).textContent).toContain('2 pipelines');
  });

  test('PublishDraftModal renders with correct url', async () => {
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          publishDraftModalEntityId: '123',
          fieldDraftSummary: {
            123: [
              {
                id: '456',
                name: 'Account Name',
              },
            ],
          },
        },
      },
    });
    expect((await screen.findByText('Account Name')).textContent).toMatch(/Account Name/);
    expect(document.querySelector('.publish-draft-modal__pipeline-name a').getAttribute('href')).toBe(
      '/sync-studio/entity/123/field/456/pipeline'
    );
  });

  test('PublishDraftModal approve called and button text changed', async () => {
    const apSpy = jest.spyOn(EPActions, 'approveEntityPipeline');
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          publishDraftModalEntityId: '123',
          fieldDraftSummary: {
            123: [
              {
                id: '456',
                name: 'Account Name',
              },
            ],
          },
        },
      },
    });
    const publishButton = await screen.findByText('Publish draft');

    const verionName = screen.getByPlaceholderText('Type version name…') as HTMLInputElement;
    fireEvent.click(verionName);
    await userEvent.type(verionName, 'Test version name', { delay: 10 });

    fireEvent.click(publishButton);
    expect(apSpy).toHaveBeenCalled();
    expect((await screen.findByText('Publishing draft…')).textContent).toMatch('Publishing draft…');
  });

  test('PublishDraftModal publish button disabled', async () => {
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          entityPipelineApproving: true,
          publishDraftModalEntityId: '123',
          fieldDraftSummary: {
            123: [
              {
                id: '456',
                name: 'Account Name',
              },
            ],
          },
        },
      },
    });
    await screen.findByText('Publishing draft…');
    expect(document.querySelector('button.ant-btn-primary').getAttribute('disabled')).toBe('');
  });

  test('PublishDraftModal error message is displayed', async () => {
    renderWithRouter(<PublishDraftModal />, {
      testState: {
        entityPipeline: {
          entityPipelineApprovingErrorMsg: 'This is a test error',
          publishDraftModalEntityId: '123',
          fieldDraftSummary: {
            123: [
              {
                id: '456',
                name: 'Account Name',
              },
            ],
          },
        },
      },
    });
    expect(await screen.findByText('This is a test error')).toBeInTheDocument();
    expect(document.querySelector('.publish-draft-modal__pipelines-container--with-error')).toBeInTheDocument();
  });
});
