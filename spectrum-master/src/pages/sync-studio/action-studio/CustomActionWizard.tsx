//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { CustomAction } from 'components/custom-action/types';
import DrawerPanel from 'components/DrawerPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';

import CustomActionContent from './CustomActionContent';

export interface CustomActionWizardProps {
  customAction: CustomAction | null;
  visible?: boolean;
  close?: () => void;
}

const CustomActionWizard = ({ customAction, visible, close }: CustomActionWizardProps) => {
  const { tn } = useI18nContext();

  return (
    <DrawerPanel
      className="synri-config-full-content"
      keyboard={false}
      maskClosable={false}
      noPadding
      onClose={close}
      title={customAction?.id ? tn('edit_action_title', { name: customAction.displayName }) : tn('title')}
      visible={visible}
      width="full">
      {visible && <CustomActionContent customAction={customAction} close={close} />}
    </DrawerPanel>
  );
};

export default withI18n(CustomActionWizard, 'CustomAction');
