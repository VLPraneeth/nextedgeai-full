import { MergeOptionValues } from 'components/merge-options/MergeOptions.types';
import { FieldDataType } from 'components/types';

export interface QSInstallEntity {
  id: string;
  apiName: string;
  displayName: string;
  fields: QSInstallEntityField[];
}

export interface QSInstallEntityReplacement {
  id: string;
  apiName: string;
  displayName: string;
  replacementFields: {
    field: QSInstallEntityField;
    replacementField: QSInstallEntityField;
  }[];
}

export interface QSInstallReviewMergePipeline {
  id: string;
  apiName: string;
  displayName: string;
  fields: (QSInstallEntityField & { source: MergeOptionValues; destination: MergeOptionValues })[];
}

export interface QSInstallEntityPipelines {
  id: string;
  displayName: string;
  fields: QSInstallEntityField[];
}

export interface QSInstallEntityField {
  id: string;
  apiName: string;
  displayName: string;
  dataType: FieldDataType;
}
