export enum ValidationMode {
  ENTITY = 'ENTITY',
  FIELD = 'FIELD',
}

export enum ValidationResultType {
  ERROR = 'ERROR',
  WARNING = 'WARNING',
}

export enum ValidationErrorLevel {
  GLOBAL = 'GLOBAL',
  ENTITY = 'ENTITY',
  ATTRIBUTE = 'ATTRIBUTE',
}

export interface ValidationResult {
  level: ValidationErrorLevel;
  type: ValidationResultType;
  nodeId?: string;
  targetId?: string;
  message: string;
}

export interface FocusedValidationResult extends ValidationResult {
  scope: string;
}

export interface ValidationState {
  errors: ValidationResult[];
  isGotoBetweenFieldPipelines: boolean;
  validationMode?: ValidationMode;
  validationResultsPanelVisible: boolean;
  validationToolbarVisible: boolean;
  warnings: ValidationResult[];
}
