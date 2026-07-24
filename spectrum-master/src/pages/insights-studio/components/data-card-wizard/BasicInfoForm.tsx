import { ChangeEvent, useEffect, useState } from 'react';

import Button from 'components/Button';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Validator from 'components/validator';
import { FormValidatorEventHandler } from 'components/validator/FormValidator';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { tc } from 'utils/i18nUtil';
import { createApiName } from 'utils/StringUtil';

/**
 * Basic Info Form is used in the DataCardWizard for creating a data card by dragging a dataset
 * onto a dashboard layout.
 *
 * Form info is collected locally and saved to DataCardAuthoringContext as `newCardInfo`
 * when submitted.
 *
 * Actual card creation is handled by DataCardConfigStep
 */

export interface BasicInfoFormProps {
  onCancel: () => void;
  onSuccess: () => void;
}

export const BasicInfoForm = ({ onCancel, onSuccess }: BasicInfoFormProps) => {
  const { selectedDataCard, setNewCardInfo, newCardInfo } = useDataCardAuthoringContext();

  const [displayName, setDisplayName] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [tags, setTags] = useState<string[]>([]);

  useEffect(() => {
    setName(selectedDataCard?.name ?? newCardInfo?.name ?? '');
    setDisplayName(selectedDataCard?.displayName ?? newCardInfo?.displayName ?? '');
    setDescription(selectedDataCard?.description ?? newCardInfo?.description ?? '');
    setTags(selectedDataCard?.tags ?? newCardInfo?.tags ?? []);
    // only want to run this is selected card changes
    // eslint-disable-next-line
  }, [selectedDataCard]);

  const convertDisplayToApiName = () => {
    if (!name && !!displayName) {
      let newApiName = createApiName(displayName);
      setName(newApiName);
    }
  };

  const handleSubmit: FormValidatorEventHandler = (e) => {
    e?.preventDefault();
    setNewCardInfo({
      displayName,
      name,
      description,
      tags,
    });
    onSuccess();
  };

  return (
    <div className="data-card-basic-info-form">
      <Validator.Form onSubmit={handleSubmit} id="data-card-form">
        <Validator.Field
          name="displayName"
          validationOptions={{ required: true }}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setDisplayName(e.target.value)}
          onBlur={convertDisplayToApiName}
          value={displayName}
          render={(validatorProps) => (
            <InputWithLabel
              required
              label={tc('display_name')}
              id="displayName"
              validateStatus={validatorProps.isValid ? 'success' : 'error'}
              help={validatorProps.errorMessage}
              {...validatorProps}
            />
          )}
        />

        <Validator.Field
          name="name"
          validationOptions={{ noSpecialChars: true, noSpaces: true, required: true }}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
          value={name}
          render={(validatorProps) => (
            <InputWithLabel
              label={tc('api_name')}
              id="name"
              disabled={!!selectedDataCard}
              required
              validateStatus={validatorProps.isValid ? 'success' : 'error'}
              help={validatorProps.errorMessage}
              {...validatorProps}
            />
          )}
        />

        <InputWithLabel
          label={tc('description')}
          id={tc('description')}
          datatype="textarea"
          value={description}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setDescription(e.target.value)}
        />
        <InputWithLabel
          label={tc('tags')}
          id={tc('tags')}
          datatype="tag"
          value={tags}
          onChange={(newTags: string[]) => setTags(newTags)}
        />
      </Validator.Form>
      <div className="synri-drawer-panel__footer">
        <Button onClick={onCancel}>{tc('close')}</Button>
        <Button type="primary" htmlType="submit" form="data-card-form">
          {tc('next')}
        </Button>
      </div>
    </div>
  );
};
