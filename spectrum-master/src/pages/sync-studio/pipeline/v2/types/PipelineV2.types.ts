export type PipelineNodeColors = 'gray' | 'blue' | 'violet' | 'cyan' | 'red' | 'orange' | 'yellow';

export interface FieldsCountSummary {
  fieldsCount: number;
  mapped: number;
  draft: number;
  ready: number;
}
