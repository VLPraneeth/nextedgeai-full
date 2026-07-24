//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { Select } from 'antd';
import { map } from 'lodash';
import { useCallback, useMemo, useState } from 'react';

import { CustomAction } from 'components/custom-action/types';
import GraphItemFilter from 'components/GraphItemFilter';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { useEnhancedDispatch as useDispatch, useEnhancedSelector as useSelector } from 'hooks/redux';
import { useUserHasPermission } from 'hooks/useUserHasPermission';
import { showCustomActionWizard } from 'store/custom-action/slice';
import { CustomActionPayload } from 'store/custom-action/types';
import { tCommon as tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import ActionSharePanel from './ActionSharePanel';
import ActionStudioList from './ActionStudioList';
import CustomActionWizard from './CustomActionWizard';

import './ActionPanel.less';

const Option = Select.Option;

export type ActionListType = 'ACTION_LIBRARY' | 'ACTION_STUDIO';

export interface ActionPanelProps {
  actions: any[];
}

const ActionPanel = ({ actions }: ActionPanelProps) => {
  const { tn } = useI18nContext();
  const [actionListType, setActionListType] = useState<ActionListType>('ACTION_LIBRARY');
  const dispatch = useDispatch();
  const { wizardVisible, shareVisible } = useSelector((state) => ({
    wizardVisible: state.customAction.customActionWizardVisible,
    shareVisible: state.customAction.customActionSharing.visible,
  }));
  const { userHasPermission } = useUserHasPermission();

  const [customAction, setCustomAction] = useState<CustomAction | null>(null);

  const showCustomAction = useCallback(
    (visible: boolean, newCustomAction: CustomActionPayload | null) => {
      dispatch(showCustomActionWizard({ visible }));
      // TODO: This conversion should probably be centralized. The opposite is
      // used to send the payload when saving in CustomActionContent.tsx

      // we send headers as a map to backend but use it as an array on the frontend
      const headers = map(newCustomAction?.headers, (value, key) => ({ key, value }));
      const actionConfiguration = {
        id: newCustomAction?.id,
        endpoint: {
          selectValue: newCustomAction?.method,
          textValue: newCustomAction?.endpoint,
        },
        authentication: {
          credentialId: newCustomAction?.credentialId,
          metadataId: newCustomAction?.metadataId,
        },
        body: {
          isBatch: newCustomAction?.isBatch,
          batchSize: newCustomAction?.batchSize,
          bodyValue: newCustomAction?.body,
        },
        variables: newCustomAction?.variables,
        headers,
      };
      const newStte = { ...newCustomAction, actionConfiguration, dataFormatType: 'form' };
      // TODO: Fix Type
      // @ts-ignore
      setCustomAction(newStte);
    },
    [dispatch]
  );

  const ActionListType = useMemo(() => {
    return [
      {
        label: tn('action_library'),
        value: 'ACTION_LIBRARY',
      },
      {
        label: tn('action_studio'),
        value: 'ACTION_STUDIO',
      },
    ] as const;
  }, [tn]);

  const actionLibraryList = useMemo(
    () => <GraphItemFilter filterPlaceHolder={tc('filter_label', { label: tc('actions') })} items={actions} />,
    [actions]
  );

  const actionList = useMemo(() => {
    return actionListType === 'ACTION_LIBRARY' ? (
      actionLibraryList
    ) : (
      // TODO: Fix Type
      // @ts-ignore
      <ActionStudioList showCustomAction={showCustomAction} />
    );
  }, [actionLibraryList, actionListType, showCustomAction]);

  return (
    <div className="synri-action-panel">
      <CustomActionWizard
        key="config-wizard"
        visible={wizardVisible}
        customAction={customAction}
        close={() => {
          dispatch(showCustomActionWizard({ visible: false }));
          setCustomAction(null);
        }}
      />
      <ActionSharePanel visible={shareVisible} />
      {userHasPermission(AllPermissions.ACTION_READ) ? (
        <>
          <Select
            className="synri-action-panel__select"
            size="large"
            showSearch
            value={actionListType}
            onChange={(value: ActionListType) => setActionListType(value)}>
            {ActionListType.map((actionType) => (
              <Option key={actionType.value} value={actionType.value}>
                {actionType.label}
              </Option>
            ))}
          </Select>
          {actionList}
        </>
      ) : (
        actionLibraryList
      )}
    </div>
  );
};

export default withI18n(ActionPanel, 'Action');
