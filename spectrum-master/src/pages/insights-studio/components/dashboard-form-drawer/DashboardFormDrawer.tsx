import { navigate } from '@reach/router';
import { message } from 'antd';
import { ChangeEvent, useEffect, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import InlineMessage from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import Validator from 'components/validator';
import { FormValidatorEventHandler } from 'components/validator/FormValidator';
import { useFocusRef } from 'hooks/useFocusRef';
import { useCreateDashboardMutation, useEditDashboardMutation } from 'store/insights-studio/api';
import { InsightsDashboard } from 'store/insights-studio/types';
import { getRtkQueryErrorMessage } from 'utils/getRtkQueryErrorMessage';
import { tc, tNamespaced } from 'utils/i18nUtil';

const tn = tNamespaced('InsightsStudio');

export interface DashboardFormDrawerProps {
  onClose: () => void;
  visible: boolean;
  dashboardToEdit: InsightsDashboard | false | undefined;
}

export const DashboardFormDrawer = ({ onClose, visible, dashboardToEdit }: DashboardFormDrawerProps) => {
  const [displayName, setDisplayName] = useState((dashboardToEdit && dashboardToEdit.displayName) || '');
  const [description, setDescription] = useState((dashboardToEdit && dashboardToEdit.description) || '');
  const [name, setName] = useState((dashboardToEdit && dashboardToEdit.name) || '');
  const [tags, setTags] = useState<string[]>((dashboardToEdit && dashboardToEdit.tags) || []);

  const [createDashboard, { error: createError }] = useCreateDashboardMutation();
  const [updateDashboard, { error: editError }] = useEditDashboardMutation();

  const focusRef = useFocusRef({ autoFocus: true });

  const reset = () => {
    setDisplayName('');
    setDescription('');
    setName('');
    setTags([]);
  };

  useEffect(() => {
    if (visible && dashboardToEdit) {
      setDisplayName(dashboardToEdit.displayName);
      setDescription(dashboardToEdit.description);
      setTags(dashboardToEdit.tags ?? []);
      setName(dashboardToEdit.name ?? '');
    }
  }, [visible, dashboardToEdit]);

  useEffect(() => {
    if (!visible) {
      reset();
    }
  }, [visible]);

  const handleSubmit: FormValidatorEventHandler = (e) => {
    e?.preventDefault();

    if (dashboardToEdit) {
      const updatePayload = {
        ...dashboardToEdit,
        // handle `dataCards` being `null` for newly-created dashboards
        dataCards: dashboardToEdit.dataCards ?? [],
        displayName,
        description,
        tags,
        name,
      };

      updateDashboard(updatePayload).then((result) => {
        if ('data' in result) {
          onClose();
          message.success(
            tn('dashboard_updated', {
              dashboard: displayName,
              interpolation: { escapeValue: false },
            })
          );
        }
      });
    } else {
      const createPayload = { displayName, description, tags };
      createDashboard(createPayload).then((result) => {
        if ('data' in result) {
          onClose();
          message.success(
            tn('dashboard_created', {
              dashboard: result.data.displayName,
              interpolation: { escapeValue: false },
            })
          );
          // allow list to refresh so the new dashboard is there
          setTimeout(() => {
            navigate('/insights-studio/' + result.data.id);
          }, 500);
        }
      });
    }
  };

  const drawerTitle = (dashboardToEdit ? tc('edit') : tc('new')) + ` ${tc('dashboard')}`;

  return (
    <DrawerPanel
      onClose={onClose}
      destroyOnClose
      mask
      maskClosable
      title={drawerTitle}
      visible={visible}
      zIndex={11}
      footer={
        <>
          <Button onClick={onClose}>{tc('cancel')}</Button>
          <Button type="primary" htmlType="submit" form="createDashboard">
            {dashboardToEdit ? tc('save') : tc('create')}
          </Button>
        </>
      }>
      <InlineMessage type="error" allowMultiline>
        {getRtkQueryErrorMessage(createError || editError)}
      </InlineMessage>
      <Validator.Form id="createDashboard" onSubmit={handleSubmit}>
        <Validator.Field
          validationOptions={{ required: true }}
          name="displayName"
          onChange={(e: ChangeEvent<HTMLInputElement>) => setDisplayName(e.target.value)}
          value={displayName}
          render={({ isValid, errorMessage, ...validatorProps }) => (
            <InputWithLabel
              ref={focusRef.refCallback}
              required
              label={tc('display_name')}
              id={tc('display_name')}
              validateStatus={!isValid ? 'error' : ''}
              help={errorMessage}
              {...validatorProps}
            />
          )}
        />

        {name && (
          <Validator.Field
            validationOptions={{ noSpecialChars: true, noSpaces: true, required: true }}
            name="name"
            onChange={(e: ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
            value={name}
            render={({ isValid, errorMessage, ...validatorProps }) => (
              <InputWithLabel
                required
                label={tc('api_name')}
                id={tc('api_name')}
                disabled
                validateStatus={!isValid ? 'error' : ''}
                help={errorMessage}
                {...validatorProps}
              />
            )}
          />
        )}

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
    </DrawerPanel>
  );
};
