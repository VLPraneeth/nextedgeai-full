import { RootState } from 'reducers';

export const selectValidationResultsPanelState = (state: RootState) => ({
  currentGraph: state.pipeline.currentGraph,
  entities: state.entity.entities,
  entityPipeline: state.entityPipeline.entityPipeline,
  entityPipelineDraft: state.entityPipeline.entityPipeline?.draft ?? null,
  entityPipelineValidating: state.entityPipeline.entityPipelineValidating,
  errors: state.validation.errors,
  fieldPipeline: state.fieldPipeline.fieldPipeline,
  fieldPipelineDraft: state.fieldPipeline.fieldPipeline?.draft ?? null,
  fieldPipelineValidating: state.fieldPipeline.fieldPipelineValidating,
  validationMode: state.validation.validationMode,
  visible: state.validation.validationResultsPanelVisible,
  warnings: state.validation.warnings,
});

export const selectValidationToolbarState = (state: RootState) => ({
  currentGraph: state.pipeline.currentGraph,
  entities: state.entity.entities,
  entityPipeline: state.entityPipeline.entityPipeline,
  entityPipelineValidating: state.entityPipeline.entityPipelineValidating,
  entityValidationErrors: state.entityPipeline.validationErrors,
  errors: state.validation.errors,
  fieldPipeline: state.fieldPipeline.fieldPipeline,
  fieldPipelineValidating: state.fieldPipeline.fieldPipelineValidating,
  fieldValidationErrors: state.fieldPipeline.validationErrors,
  selectedFieldNode: state.fieldPipeline.selectedGraphNode,
  selectedEntityNode: state.entityPipeline.selectedGraphNode,
  testPanelView: state.test?.testPanelView,
  validationMode: state.validation.validationMode,
  validationResultsPanelVisible: state.validation.validationResultsPanelVisible,
  visible: state.validation.validationToolbarVisible,
  warnings: state.validation.warnings,
});
