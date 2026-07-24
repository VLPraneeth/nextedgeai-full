import { Button } from 'antd';
import cx from 'classnames';
import React, { useCallback, useState, useEffect } from 'react';

import Can from 'components/Can';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useGetCustomActionListQuery } from 'store/custom-action/api';
import { showCustomActionShare } from 'store/custom-action/slice';
import CapConstants from 'utils/CapConstants';
import { tc } from 'utils/i18nUtil';
import { AllPermissions } from 'utils/PermissionsConstants';

import { useActionStudio } from './ActionStudio.hook';

export interface ActionSharePanelProps {
  className?: string;
  visible: boolean;
}

const ActionSharePanel = ({ className, visible }: ActionSharePanelProps) => {
  const dispatch = useEnhancedDispatch();
  const customActionId = useEnhancedSelector((state) => state.customAction.customActionSharing.customActionId);

  const { tn } = useI18nContext();
  const { shareAction } = useActionStudio();

  const { data: customActions } = useGetCustomActionListQuery();
  const customAction = customActions?.find((ca) => ca.id === customActionId);

  const [shareWithOrg, setShareWithOrg] = useState(false);
  const [shareGlobally, setShareGlobally] = useState(false);

  // Set state flags
  useEffect(() => {
    if (visible) {
      setShareWithOrg(!!customAction?.shareWithOrg);
      setShareGlobally(!!customAction?.shareGlobally);
    }
  }, [customAction?.shareGlobally, customAction?.shareWithOrg, visible]);

  const close = useCallback(() => {
    if (customAction && customAction.id) {
      dispatch(showCustomActionShare({ visible: false, customActionId: customAction.id }));
    }
  }, [dispatch, customAction]);

  const submit = useCallback(async () => {
    if (customAction && customAction.id) {
      shareAction(customAction.id, shareWithOrg, shareGlobally);
      dispatch(showCustomActionShare({ visible: false, customActionId: customAction.id }));
    }
  }, [customAction, shareAction, shareWithOrg, shareGlobally, dispatch]);

  return (
    <DrawerPanel
      title={tc('share')}
      className={cx('synri-test-modal', className)}
      onClose={close}
      visible={visible}
      footerClassName={cx('synri-test-modal-footer')}
      footer={
        <>
          <Button onClick={close} className="btn-cancel">
            {tc('cancel')}
          </Button>
          <Button onClick={submit} type="primary">
            {tc('save')}
          </Button>
        </>
      }>
      <div>
        <Can permission={AllPermissions.ACTION_SHARE}>
          <InputWithLabel
            datatype="checkbox"
            label={tn('share_with_org')}
            value={shareWithOrg}
            disabled={shareGlobally}
            name="shareToOrg"
            defaultValue={shareWithOrg}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              setShareWithOrg(e.target.checked);
            }}
          />
        </Can>
        <Can capability={[CapConstants.SUPER_ADMIN]}>
          <InputWithLabel
            datatype="checkbox"
            label={tn('share_globally')}
            value={shareGlobally}
            disabled={shareWithOrg}
            name="shareGlobally"
            defaultValue={shareGlobally}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
              setShareGlobally(e.target.checked);
            }}
          />
        </Can>
      </div>
    </DrawerPanel>
  );
};

export default withI18n(ActionSharePanel, 'CustomAction');
