import Checkbox from 'components/Checkbox';
import { FieldItem } from 'components/inputs/FieldOptions';
import { HStack } from 'components/layout';
import { TranslatedText } from 'components/typography';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { FieldAliasInput } from './FieldAliasInput';
import { PipelinePickerEntity, PipelinePickerEntityField } from './PipelinePicker.types';

export interface PipelinePickerFieldProps {
  field: PipelinePickerEntityField;
  entity: PipelinePickerEntity;
  selected: boolean;
  disabled?: boolean;
  updateSelectedItem: (checked: boolean, entityId: string, fieldId?: string | undefined, fieldAlias?: string) => void;
  hideOpenInNewTab?: boolean;
  withFieldAlias?: boolean;
}

const PipelinePickerField = ({
  field,
  entity,
  selected,
  disabled,
  updateSelectedItem,
  hideOpenInNewTab = false,
  withFieldAlias = false,
}: PipelinePickerFieldProps) => {
  return (
    <Checkbox
      className="synri-checkbox-wrapper-aligned"
      onChange={(e) => {
        updateSelectedItem(e.target.checked, entity.id, field.id);
      }}
      disabled={disabled}
      checked={selected}>
      <div className="synri-checkbox-content">
        <HStack>
          <FieldItem {...field} />
          {withFieldAlias && (
            <FieldAliasInput
              updateSelectedItem={(entityId: string, fieldId?: string, fieldAlias?: string) => {
                // Using a timeout here in case the user clicks on the field and
                // unselects the field inadvertently. The timeout will trigger
                // the updateSelectItem after the Checkbox change handler to
                // reselect the field.
                setTimeout(() => {
                  // If the alias changes we should also select the field
                  // (passing true as first param)
                  updateSelectedItem(true, entityId, fieldId, fieldAlias);
                }, 100);
              }}
              entity={entity}
              field={field}
            />
          )}
        </HStack>
        {!hideOpenInNewTab && (
          <button
            className="synri-link-button synri-link-hover-only"
            onClick={() => {
              const path = makeUrl(RouteConstants.FIELD_PIPELINE, { entityId: entity.id, fieldId: field.id });
              const url = window.location.origin + path;
              window.open(url);
            }}>
            <TranslatedText size="xs" text="open_in_new_tab" />
          </button>
        )}
      </div>
    </Checkbox>
  );
};

export default PipelinePickerField;
