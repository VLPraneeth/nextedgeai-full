import { ValidationErrorLevel, ValidationMode, ValidationResult, ValidationResultType } from 'store/validation/types';
import { renderWithRouter, screen } from 'tests/helpers';

import { ValidationResultsPanel } from './ValidationResultsPanel';

// /sync-studio/entity/626fe8ffee18af17f1ee5fd8/pipeline/new

const errors: ValidationResult[] = [
  {
    message: 'Error 1',
    type: ValidationResultType.ERROR,
    level: ValidationErrorLevel.GLOBAL,
  },
  {
    message: 'Error 2',
    type: ValidationResultType.ERROR,
    level: ValidationErrorLevel.ATTRIBUTE,
  },
  {
    message: 'Error 3',
    type: ValidationResultType.ERROR,
    level: ValidationErrorLevel.ENTITY,
  },
];

const testState = {
  entity: {
    entities: [],
  },
  entityPipeline: {
    entityPipeline: {},
    entityPipelineValidating: false,
  },
  fieldPipeline: {
    fieldPipeline: null,
    fieldPipelineValidating: false,
  },
  pipeline: {
    currentGraph: null,
  },
  validation: {
    errors: [],
    validationMode: ValidationMode.ENTITY,
    validationResultsPanelVisible: true,
    warnings: [],
  },
};

describe('ValidationResultsPanel', () => {
  it('should show `Validating...` when a entity pipeline validation is in progress', async () => {
    renderWithRouter(<ValidationResultsPanel />, {
      testState: {
        ...testState,
        entityPipeline: {
          ...testState.entityPipeline,
          entityPipelineValidating: true,
        },
      },
    });

    expect(await screen.findByText('Validating…')).toBeVisible();
  });

  it('should show `Validating...` when a field pipeline validation is in progress', async () => {
    renderWithRouter(<ValidationResultsPanel />, {
      testState: {
        ...testState,
        fieldPipeline: {
          ...testState.fieldPipeline,
          fieldPipelineValidating: true,
        },
      },
    });

    expect(await screen.findByText('Validating…')).toBeVisible();
  });

  it('should show `Success!` when a pipeline validation is successful', async () => {
    renderWithRouter(<ValidationResultsPanel />, { testState });

    expect(await screen.findByText('Success!')).toBeVisible();
  });

  it('should show `Global ({Number of Errors} Errors, {Number of Warnings} Warnings)` when global errors occur', async () => {
    renderWithRouter(<ValidationResultsPanel />, {
      testState: {
        ...testState,
        validation: {
          ...testState.validation,
          errors,
        },
      },
    });

    expect(await screen.findByText('Global (1 Errors, 0 Warnings)')).toBeVisible();
  });

  it('should show `Field ({Number of Errors} Errors, {Number of Warnings} Warnings)` when field errors occur', async () => {
    renderWithRouter(<ValidationResultsPanel />, {
      testState: {
        ...testState,
        validation: {
          ...testState.validation,
          errors,
        },
      },
    });

    expect(await screen.findByText('Field (1 Errors, 0 Warnings)')).toBeVisible();
  });

  it('should show `Nodes ({Number of Errors} Errors, {Number of Warnings} Warnings)` when node errors occur', async () => {
    renderWithRouter(<ValidationResultsPanel />, {
      testState: {
        ...testState,
        validation: {
          ...testState.validation,
          errors,
        },
      },
    });

    expect(await screen.findByText('Nodes (1 Errors, 0 Warnings)')).toBeVisible();
  });
});
