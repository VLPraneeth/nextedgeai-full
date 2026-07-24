import { find } from 'lodash';
import { useState } from 'react';
import * as React from 'react';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useFieldOptions } from 'components/inputs/FieldOptions';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Select from 'components/inputs/Select';
import { HStack, Stack } from 'components/layout';
import { Text, TranslatedText } from 'components/typography';
import { useEnhancedSelector } from 'hooks/redux';
import { EMPTY_ARRAY } from 'store/constants';
import { selectEntitySchema } from 'store/schema/selectors';
import AppConstants from 'utils/AppConstants';

import { ALLOWED_ID_DATATYPE } from './FieldSchemaModal';

const COMPOSITE_KEY_DELIMITER = '|';

export interface CompositeKeySelectorProps {
  entityId: string;
  onChange?: (key?: string) => void;
  value?: string;
  fieldId?: string; // May be undefined when creating a new field
  disabled?: boolean;
}

const CompositeKeySelector = ({ entityId, value, fieldId, onChange, disabled }: CompositeKeySelectorProps) => {
  const { tn } = useI18nContext();
  const entitySchema = useEnhancedSelector((state) => selectEntitySchema(state, { entityId }));
  const valueParts = value?.split(COMPOSITE_KEY_DELIMITER);

  const [enableCompositeKey, setEnableCompositeKey] = useState(!!value);

  const rawFields = (entitySchema?.data || EMPTY_ARRAY).map((field) => field.draft?.fields || field.published?.fields);

  const availableCompositeKeyFields = rawFields.filter(
    (field) => field.id !== fieldId && ALLOWED_ID_DATATYPE.includes(field.dataType)
  );

  const selectOptions = useFieldOptions(availableCompositeKeyFields.map((field) => ({ ...field, id: field.apiName })));

  const compositeKey = valueParts
    ?.map((apiName) => find(availableCompositeKeyFields, { apiName })?.apiName)
    .filter(Boolean)
    .join(COMPOSITE_KEY_DELIMITER);

  return (
    <Stack spacing="z">
      <HStack justify="space-between">
        <InputWithLabel
          name="enableCompositeKey"
          label={<TranslatedText weight="semibold" text="composite_key" beDangerous />}
          checked={enableCompositeKey}
          datatype={AppConstants.INPUT_TYPE.CHECKBOX}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) => setEnableCompositeKey(e.target.checked)}
          disabled={disabled}
        />
        {enableCompositeKey && compositeKey && <Text className="synri-composite-key-overflow">{compositeKey}</Text>}
      </HStack>
      {!disabled && enableCompositeKey && (
        <Stack>
          <Select
            mode="multiple"
            className="full-width"
            placeholder={tn('select_composite_keys')}
            value={valueParts}
            disabled={disabled}
            onChange={(values: string[]) => {
              if (Boolean(values.length)) {
                onChange?.(values.join(COMPOSITE_KEY_DELIMITER));
              } else {
                onChange?.();
              }
            }}
            {...selectOptions}
          />
        </Stack>
      )}
    </Stack>
  );
};

export default withI18n(CompositeKeySelector, 'FieldSchemaModal');
