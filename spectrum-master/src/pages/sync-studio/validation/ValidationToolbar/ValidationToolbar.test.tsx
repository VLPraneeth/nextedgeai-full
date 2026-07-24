import { noop } from 'lodash';

import { ValidationMode } from 'store/validation/types';
import { renderWithRouter, screen } from 'tests/helpers';

import { ValidationToolbar } from './ValidationToolbar';

const testState = {
  entity: {
    entities: [
      {
        id: 'accountEntity',
        displayName: 'Account',
      },
    ],
  },
  entityPipeline: {
    entityPipelineValidating: false,
    entityPipelineValidated: false,
    validationErrors: [],
  },
  fieldPipeline: {
    fieldPipeline: null,
    fieldPipelineValidating: false,
    fieldPipelineValidated: false,
    validationErrors: [],
  },
  validation: {
    errors: [],
    validationMode: ValidationMode.ENTITY,
    validationResultsPanelVisible: false,
    validationToolbarVisible: true,
    warnings: [],
  },
};

describe('ValidationToolbar', () => {
  it('should be null if onValidate is undefined', async () => {
    // @ts-expect-error: ts data mismatch
    const { container } = renderWithRouter(<ValidationToolbar updateNodes={noop} />, { testState });

    expect(container.firstChild).toBeNull();
  });

  it('should display "Show validation results" button if ValidationResultsPanel is closed', async () => {
    // @ts-expect-error: ts data mismatch
    renderWithRouter(<ValidationToolbar updateNodes={noop} onValidate={noop} />, { testState });

    expect(await screen.findByText('Show validation results')).toBeVisible();
  });

  it('should display "Hide validation results" button if ValidationResultsPanel is open', async () => {
    renderWithRouter(<ValidationToolbar updateNodes={noop} onValidate={noop} />, {
      // @ts-expect-error: ts data mismatch
      testState: {
        ...testState,
        validation: {
          ...testState.validation,
          validationResultsPanelVisible: true,
        },
      },
    });

    expect(await screen.findByText('Hide validation results')).toBeVisible();
  });

  it('should display "Validating..." if the entity pipeline is validating', async () => {
    renderWithRouter(<ValidationToolbar updateNodes={noop} onValidate={noop} />, {
      // @ts-expect-error: ts data mismatch
      testState: {
        ...testState,
        entityPipeline: {
          ...testState.entityPipeline,
          entityPipelineValidating: true,
        },
      },
      route: '/sync-studio/entity/accountEntity/pipeline/new',
    });

    expect(await screen.findByText('Validating…')).toBeVisible();
  });

  it('should display two text tags: "{# of Errors} errors" and "{# of Warnings} warnings" if the entity pipeline is invalid', async () => {
    renderWithRouter(<ValidationToolbar updateNodes={noop} onValidate={noop} />, {
      // @ts-expect-error: ts data mismatch
      testState: {
        ...testState,
        entityPipeline: {
          ...testState.entityPipeline,
          entityPipelineValidated: false,
        },
        validation: {
          ...testState.validation,
          errors: [{}],
          warnings: [{}],
        },
      },
      route: '/sync-studio/entity/accountEntity/pipeline/new',
    });

    expect(await screen.findByText('1 errors')).toBeVisible();
    expect(await screen.findByText('1 warnings')).toBeVisible();
  });

  it('should display "Validating..." if the field pipeline is validating', async () => {
    renderWithRouter(<ValidationToolbar updateNodes={noop} onValidate={noop} />, {
      // @ts-expect-error: ts data mismatch
      testState: {
        ...testState,
        fieldPipeline: {
          ...testState.fieldPipeline,
          fieldPipeline: { name: 'Account Name' },
          fieldPipelineValidating: true,
        },
        validation: {
          ...testState.validation,
          validationMode: ValidationMode.FIELD,
        },
      },
      route: '/sync-studio/entity/accountEntity/field/accountNameField/pipeline/new',
    });

    expect(await screen.findByText('Validating…')).toBeVisible();
  });

  it('should display two text tags: "{# of Errors} errors" and "{# of Warnings} warnings" if the field pipeline is invalid', async () => {
    renderWithRouter(<ValidationToolbar updateNodes={noop} onValidate={noop} />, {
      // @ts-expect-error: ts data mismatch
      testState: {
        ...testState,
        fieldPipeline: {
          ...testState.fieldPipeline,
          fieldPipeline: { name: 'Account Name' },
        },
        validation: {
          ...testState.validation,
          errors: [{}, {}],
          validationMode: ValidationMode.FIELD,
          warnings: [{}],
        },
      },
      route: '/sync-studio/entity/accountEntity/field/accountNameField/pipeline/new',
    });

    expect(await screen.findByText('2 errors')).toBeVisible();
    expect(await screen.findByText('1 warnings')).toBeVisible();
  });
});
