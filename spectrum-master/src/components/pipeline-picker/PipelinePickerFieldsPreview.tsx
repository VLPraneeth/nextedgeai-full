import { FieldItem } from 'components/inputs/FieldOptions';

import { PipelinePickerEntity } from './PipelinePicker.types';

export interface PipelinePickerFieldsPreviewProps {
  entity: PipelinePickerEntity;
}

const PipelinePickerFieldsPreview = ({ entity }: PipelinePickerFieldsPreviewProps) => {
  return (
    <div className="synri-field-pipeline-container">
      {entity.fields.map((field) => {
        return (
          <div className="synri-field-pipeline-item">
            <FieldItem {...field} />
          </div>
        );
      })}
    </div>
  );
};

export default PipelinePickerFieldsPreview;
