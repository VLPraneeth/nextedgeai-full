import { useState } from 'react';
import * as React from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import useMountUnmountEffect from 'hooks/useMountUnmountEffect';
import useToastForFetchStatusChange from 'hooks/useToastForFetchStatusChange';
import { clearFilterUpsertStatus } from 'store/data-studio/actions';
import { selectFilterCreatingStatus, selectFilterUpdatingStatus } from 'store/data-studio/selectors';
import { createEntityFilter, updateEntityFilter } from 'store/data-studio/thunks';
import { EntityFilter } from 'store/data-studio/types';
import AppConstants from 'utils/AppConstants';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { alphaNumericRegEx } from 'utils/RegexUtil';

const tn = tNamespaced('DataStudio');

const { FETCH_STATUS, INPUT_TYPE } = AppConstants;

const isCheckboxOrRadio = (variableToCheck: any): variableToCheck is HTMLInputElement => {
  return (
    typeof variableToCheck.type !== 'undefined' &&
    (variableToCheck.type === 'checkbox' || variableToCheck.type === 'radio')
  );
};

const nameMinLength = 3;

type FilterFormValues = Pick<EntityFilter, 'name' | 'description' | 'bookmarked' | 'tags'>;

export interface UpdateFilterDrawerProps {
  entityId: string;
  filter: Partial<EntityFilter>;
  onRequestClose: () => void;
  onSaveFilter: (filter: EntityFilter) => void;
}

const UpdateFilterDrawer = ({ entityId, filter, onRequestClose, onSaveFilter }: UpdateFilterDrawerProps) => {
  const [formValues, setFormValues] = useState<Partial<EntityFilter> | FilterFormValues>(() => ({
    name: '',
    description: '',
    bookmarked: false,
    tags: [],
    ...filter,
  }));
  const [errors, setErrors] = useState<Partial<EntityFilter>>({});

  const dispatch = useEnhancedDispatch();
  const creatingStatus = useEnhancedSelector((state) => selectFilterCreatingStatus(state, entityId));
  const updatingStatus = useEnhancedSelector((state) => selectFilterUpdatingStatus(state, filter?.id || ''));

  const isLoading = updatingStatus === FETCH_STATUS.LOADING || creatingStatus === FETCH_STATUS.LOADING;

  useToastForFetchStatusChange(updatingStatus, {
    error: tn('updating_filter_failed'),
    success: tn('updating_filter_success', { name: formValues.name }),
  });

  useToastForFetchStatusChange(creatingStatus, {
    error: tn('updating_filter_failed'),
    success: tn('updating_filter_success', { name: formValues.name }),
  });

  useMountUnmountEffect(() => {
    dispatch(clearFilterUpsertStatus(entityId, filter.id));
  });

  const handleSave = async () => {
    const errors: Record<string, string> = {};
    // lightweight validation for form
    if (!formValues.name) {
      errors.name = tn('required');
    } else if (formValues.name.length < nameMinLength) {
      errors.name = tn('name_min_length', { min_length: nameMinLength });
    }

    if (Object.keys(errors).length) {
      setErrors(errors);
    } else {
      if (filter.id) {
        const updatedFilter = { ...(filter as EntityFilter), ...formValues };
        const result = await dispatch(updateEntityFilter(filter.id, updatedFilter));

        if (result.success) {
          onSaveFilter(updatedFilter);
          onRequestClose();
        }
      } else {
        // satisfy TS
        const filterData = formValues as EntityFilter;

        dispatch(
          createEntityFilter(
            entityId,
            filterData.criteria,
            filterData.name,
            filterData.description || '',
            filterData.tags || [],
            filterData.bookmarked
          )
        );
      }
    }
  };

  const handleChange = (evt: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { target } = evt;
    const { name, value } = target;

    setFormValues((prev) => ({
      ...prev,
      [name]: isCheckboxOrRadio(target) ? target.checked : value,
    }));

    if (errors[name]) {
      // clear errors for the field
      setErrors((prev) => ({
        ...prev,
        [name]: undefined,
      }));
    }
  };

  const fieldsInvalid = !alphaNumericRegEx.test(formValues?.name!) && formValues?.name?.replace(/\s/g, '') === '';

  return (
    <DrawerPanel
      className="filter-detail-panel"
      mask
      visible
      title={filter?.id ? tn('save_filter') : tn('create_filter')}
      onClose={onRequestClose}
      footer={
        <>
          <Button onClick={onRequestClose}>{tc('cancel')}</Button>
          <Button type="primary" onClick={handleSave} disabled={isLoading || fieldsInvalid}>
            {filter?.id ? tc('save') : tc('create')}
          </Button>
        </>
      }>
      <form onSubmit={handleSave}>
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
        />
        <InputWithLabel
          id="filter-description"
          name="description"
          datatype={INPUT_TYPE.TEXTAREA}
          label={tn('filter_description_label')}
          value={formValues.description}
          onChange={handleChange}
        />
        <InputWithLabel
          id="filter-tags"
          name="tags"
          datatype={INPUT_TYPE.TAG}
          label={tn('filter_tags_label')}
          value={formValues.tags}
          onChange={(tags: string[]) => {
            setFormValues((prev) => ({
              ...prev,
              tags,
            }));
          }}
        />
        <InputWithLabel
          id="filter-favorite"
          name="bookmarked"
          datatype={INPUT_TYPE.BOOLEAN}
          label={tn('filter_bookmarked_label')}
          value={formValues.bookmarked}
          onChange={(bookmarked: boolean) => {
            setFormValues((prev) => ({
              ...prev,
              bookmarked,
            }));
          }}
        />
      </form>
    </DrawerPanel>
  );
};

export default UpdateFilterDrawer;
