import { useState, useCallback, useEffect } from 'react';
import * as React from 'react';

import Button from 'components/Button';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch } from 'hooks/redux';
import { EntityFilter } from 'store/data-studio/types';
import { HStack } from 'components/layout';
import { tNamespaced } from 'utils/i18nUtil';
import AppConstants from 'utils/AppConstants';
import { FilterFormValues } from 'pages/data-studio-new/FilterPanel';

const tn = tNamespaced('DataStudio');
const { INPUT_TYPE } = AppConstants;

export interface DataStudioSaveFilterProps {
  entityId: string;
  filter?: Partial<EntityFilter> | null;
  formValues: Partial<EntityFilter> | FilterFormValues;
  errors?: Partial<EntityFilter>;
  onChange: (values: Partial<FilterFormValues>) => void;
  onSave: (selectedFilter: EntityFilter) => Promise<void>;
  isDisabled?: boolean;
}

const DataStudioSaveFilter = ({ formValues, errors = {}, onChange, onSave, isDisabled }: DataStudioSaveFilterProps) => {
  const [isSaving, setIsSaving] = useState(false);

  const handleChange = useCallback(
    (evt: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const { target } = evt;
      const { name, value } = target;
      const isCheckbox = target.type === 'checkbox';

      onChange({
        [name]: isCheckbox ? (target as HTMLInputElement).checked : value,
      });
    },
    [onChange]
  );

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault();
      setIsSaving(true);
      try {
        await onSave(formValues as EntityFilter);
      } finally {
        setIsSaving(false);
      }
    },
    [onSave]
  );

  const fieldsInvalid = !formValues.name || formValues.name.trim().length < 3;

  return (
    <div className="data-studio-save-filter-container">
      <form onSubmit={handleSubmit}>
        <InputWithLabel
          id="filter-name"
          autoFocus
          name="name"
          datatype={INPUT_TYPE.STRING}
          label={tn('filter_name_label')}
          value={formValues.name}
          onChange={handleChange}
          validateStatus={errors.name ? 'error' : undefined}
          help={errors.name}
          disabled={isDisabled}
        />
        <InputWithLabel
          id="filter-description"
          name="description"
          datatype={INPUT_TYPE.TEXTAREA}
          label={tn('filter_description_label')}
          value={formValues.description}
          onChange={handleChange}
          disabled={isDisabled}
        />
      </form>
    </div>
  );
};

export default DataStudioSaveFilter;
