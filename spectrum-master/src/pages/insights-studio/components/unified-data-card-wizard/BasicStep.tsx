import { ChangeEvent, useEffect } from 'react';

import Button from 'components/Button';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Validator from 'components/validator';
import { FormValidatorEventHandler } from 'components/validator/FormValidator';
import { useFocusRef } from 'hooks/useFocusRef';
import { useDataCardAuthoringContext } from 'pages/insights-studio/context/DataCardAuthoringContext';
import { useUnifiedDataCardAuthoring } from 'pages/insights-studio/utils/useUnifiedDataCardAuthoring';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export interface BasicStepProps {
  onCancel: () => void;
  onSuccess: () => void;
  onPrevious: () => void;
}

export const BasicStep = ({ onCancel, onSuccess }: BasicStepProps) => {
  const {
    displayName,
    setDisplayName,
    apiName,
    setApiName,
    description,
    setDescription,
    tags,
    setTags,
  } = useUnifiedDataCardAuthoring();

  const { selectedDataCard } = useDataCardAuthoringContext();

  const focusRef = useFocusRef({ autoFocus: true });

  useEffect(() => {
    if (selectedDataCard?.name) {
      setDisplayName(selectedDataCard.displayName);
      setDescription(selectedDataCard.description);
      setTags(selectedDataCard.tags);
      setApiName(selectedDataCard.name);
    }
  }, [
    selectedDataCard?.description,
    selectedDataCard?.displayName,
    selectedDataCard?.name,
    selectedDataCard?.tags,
    setApiName,
    setDescription,
    setDisplayName,
    setTags,
  ]);

  const handleSubmit: FormValidatorEventHandler = (e) => {
    onSuccess();
  };

  return (
    <div className="unified-data-card-basic-info-form">
      <Validator.Form onSubmit={handleSubmit} id="data-card-form">
        <Validator.Field
          name="displayName"
          validationOptions={{ required: true }}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setDisplayName(e.target.value)}
          value={displayName}
          render={(validatorProps) => (
            <InputWithLabel
              help={validatorProps.errorMessage}
              id="displayName"
              label={tc('display_name')}
              ref={focusRef.refCallback}
              required
              tooltip={tn('Tooltips.display_name')}
              validateStatus={validatorProps.isValid ? 'success' : 'error'}
              {...validatorProps}
            />
          )}
        />

        {apiName ? (
          <InputWithLabel label={tc('api_name')} id="name" disabled value={apiName} tooltip={tn('Tooltips.api_name')} />
        ) : null}

        <InputWithLabel
          label={tc('description')}
          id={tc('description')}
          datatype="textarea"
          value={description}
          onChange={(e: ChangeEvent<HTMLInputElement>) => setDescription(e.target.value)}
          tooltip={tn('Tooltips.description')}
        />

        <InputWithLabel
          label={tc('tags')}
          id={tc('tags')}
          datatype="tag"
          value={tags}
          onChange={(newTags: string[]) => setTags(newTags)}
          tooltip={tn('Tooltips.tags')}
        />
      </Validator.Form>
      <div className="synri-drawer-panel__footer">
        <Button onClick={onCancel}>{tc('cancel')}</Button>
        <Button type="primary" htmlType="submit" form="data-card-form" onClick={onSuccess}>
          {tc('next')}
        </Button>
      </div>
    </div>
  );
};
