import PipelineEditorMoreActions, {
  PipelineEditorMoreActionsProps,
} from 'pages/sync-studio/pipeline/PipelineEditorMoreActions';
import { fireEvent, hideBenignTestWarnings, mockedAjaxUtils, renderWithRouter, screen, userEvent } from 'tests/helpers';
import { AllPermissions } from 'utils/PermissionsConstants';

import PipelineToolbar from '../PipelineToolbar';

jest.mock('utils/AjaxUtil');
const ajaxMock = mockedAjaxUtils();

hideBenignTestWarnings();

describe('Pipeline PipelineToolbar', () => {
  test('Does not crash when version is undefined', async () => {
    renderWithRouter(<PipelineToolbar entityId="entityId" draftSelectionText="Approved" />, {
      testState: {
        entityPipeline: {
          schemas: [],
        },
        fieldPipeline: {
          fieldPipeline: {
            graphVersion: 'approved',
            id: 'abcdef123456',
            draft: null,
          },
        },
        pipelineAction: {
          fieldPipelineActions: [],
        },
        pipelineFunction: {
          fieldPipelineFunctions: [],
        },
        validation: {
          validationToolbarVisible: false,
        },
        pipelineError: {},
      },
    });
    expect(await screen.findByText('Unmapped')).toBeInTheDocument();
  });

  test('Resync button shows on EntityPipelines', async () => {
    ajaxMock.get.mockImplementation((url) => {
      return Promise.resolve({ data: {} });
    });

    renderWithRouter(
      <PipelineEditorMoreActions
        {...({
          graphIsReadOnly: false,
          isApproveWithDraftGraph: false,
          isDraftOnlyGraph: false,
        } as PipelineEditorMoreActionsProps)}
      />,
      {
        testState: {
          entityPipeline: {
            schemas: [],
          },
          fieldPipeline: {
            fieldPipeline: {
              graphVersion: 'approved',
              id: 'abcdef123456',
              draft: null,
            },
          },
          pipelineAction: {
            fieldPipelineActions: [],
          },
          pipelineFunction: {
            fieldPipelineFunctions: [],
          },
          validation: {
            validationToolbarVisible: false,
          },
        },
      }
    );
    await userEvent.click(screen.getByText('More Actions'));
    expect(await screen.findByText('Resync')).toBeInTheDocument();
  });

  test("Resync button doesn't show on FieldPipelines", async () => {
    ajaxMock.get.mockImplementation((url) => {
      return Promise.resolve({ data: {} });
    });

    renderWithRouter(
      <PipelineEditorMoreActions
        {...({
          fieldId: 'abcdefghijkl',
          graphIsReadOnly: false,
          isApproveWithDraftGraph: false,
          isDraftOnlyGraph: false,
        } as PipelineEditorMoreActionsProps)}
      />,
      {
        testState: {
          entityPipeline: {
            schemas: [],
          },
          fieldPipeline: {
            fieldPipeline: {
              graphVersion: 'approved',
              id: 'abcdef123456',
              draft: null,
            },
          },
          pipelineAction: {
            fieldPipelineActions: [],
          },
          pipelineFunction: {
            fieldPipelineFunctions: [],
          },
          validation: {
            validationToolbarVisible: false,
          },
        },
      }
    );

    await userEvent.click(screen.getByText('More Actions'));
    await expect(() => screen.findByText('Resync')).rejects.toThrow();
  });

  test('Ready field should be available for drafts', async () => {
    renderWithRouter(
      <PipelineToolbar
        entityId="entityId"
        fieldId="abcdefghijkl"
        availableVersions={{ DRAFT: { test: 'test' } } as any}
        draftSelectionText="Draft"
      />,
      {
        testState: {
          entityPipeline: {
            schemas: [],
          },
          fieldPipeline: {
            fieldPipeline: {
              graphVersion: 'approved',
              id: 'abcdef123456',
              draft: null,
            },
          },
          pipelineAction: {
            fieldPipelineActions: [],
          },
          pipelineFunction: {
            fieldPipelineFunctions: [],
          },
          validation: {
            validationToolbarVisible: false,
          },
          pipelineError: {},
        },
      }
    );
    expect(await screen.findByText('Ready to Publish')).toBeInTheDocument();
  });

  test('Ready field should not be available for published', async () => {
    renderWithRouter(
      <PipelineToolbar
        entityId="entityId"
        fieldId="abcdefghijkl"
        availableVersions={{ APPROVED: { test: 'test' } } as any}
        draftSelectionText="Approved"
      />,
      {
        testState: {
          entityPipeline: {
            schemas: [],
          },
          fieldPipeline: {
            fieldPipeline: {
              graphVersion: 'approved',
              id: 'abcdef123456',
              draft: null,
            },
          },
          pipelineAction: {
            fieldPipelineActions: [],
          },
          pipelineFunction: {
            fieldPipelineFunctions: [],
          },
          validation: {
            validationToolbarVisible: false,
          },
          pipelineError: {},
        },
      }
    );
    expect(screen.queryByText('Ready to Publish')).toBeNull();
  });

  test('Ready field should show hide when toggling between draft and publish', async () => {
    const testState = {
      entityPipeline: {
        schemas: [],
      },
      fieldPipeline: {
        fieldPipeline: {
          graphVersion: 'approved',
          id: 'abcdef123456',
          draft: null,
        },
      },
      pipelineAction: {
        fieldPipelineActions: [],
      },
      pipelineFunction: {
        fieldPipelineFunctions: [],
      },
      validation: {
        validationToolbarVisible: false,
      },
      pipelineError: {},
    };
    const { rerenderWithRouter } = renderWithRouter(
      <PipelineToolbar
        entityId="entityId"
        fieldId="abcdefghijkl"
        availableVersions={{ APPROVED: { test: 'test' }, DRAFT: { test: 'test' } } as any}
        draftSelectionText="Approved"
      />,
      { testState }
    );
    expect(await screen.findByText('Published')).toBeInTheDocument();
    expect(screen.queryByText('Ready to Publish')).toBeNull();

    rerenderWithRouter(
      <PipelineToolbar
        entityId="entityId"
        fieldId="abcdefghijkl"
        availableVersions={{ APPROVED: { test: 'test' }, DRAFT: { test: 'test' } } as any}
        draftSelectionText="Draft"
      />
    );
    expect(await screen.findByText('Draft')).toBeInTheDocument();
    expect(await screen.findByText('Ready to Publish')).toBeInTheDocument();
  });

  test('Ready field toggled', async () => {
    const test = { callMe: (_ready: boolean) => {} };
    const apSpy = jest.spyOn(test, 'callMe');
    renderWithRouter(
      <PipelineToolbar
        entityId="entityId"
        fieldId="abcdefghijkl"
        availableVersions={{ DRAFT: { test: 'test' } } as any}
        draftSelectionText="Draft"
        onReadyToggleChange={(ready) => test.callMe(ready)}
      />,
      {
        testState: {
          entityPipeline: {
            schemas: [],
          },
          fieldPipeline: {
            fieldPipeline: {
              graphVersion: 'approved',
              id: 'abcdef123456',
              draft: null,
            },
          },
          pipelineAction: {
            fieldPipelineActions: [],
          },
          pipelineFunction: {
            fieldPipelineFunctions: [],
          },
          validation: {
            validationToolbarVisible: false,
          },
          user: {
            privileges: [AllPermissions.WRITE_STUDIO],
          },
          pipelineError: {},
        },
      }
    );
    expect(await screen.findByText('Ready to Publish')).toBeInTheDocument();
    const publish = document.querySelector('.synri-ready-publish button.ant-switch');
    publish && fireEvent.click(publish);
    const publishChecked = document.querySelector('.synri-ready-publish button.ant-switch.ant-switch-checked');
    expect(publishChecked).toBeInTheDocument();
    publish && fireEvent.click(publish);
    expect(apSpy).toHaveBeenCalledTimes(2);
  });
});
