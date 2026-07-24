//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { useCallback, useEffect, useState } from 'react';

import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import InlineMessage from 'components/InlineMessage';
import { HStack } from 'components/layout';
import CenterLayout from 'components/layout/CenterLayout';
import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import { updateUserDataCardConfig } from 'store/insights-studio/slice';

import { useDataCardSettingsContext } from './DataCardSettingsContext';
import SettingsForm from './DataCardSettingsForm';

const SettingsModal = () => {
  const { settingsVisible: visible, showSettings, settingsOptions, currentValue } = useDataCardSettingsContext();
  const { tn } = useI18nContext();
  const dataCard = settingsOptions?.dataCard;
  const dispatch = useEnhancedDispatch();

  const [errorMessage, setErrorMessage] = useState('');

  const closeHandler = useCallback(() => showSettings(false), [showSettings]);

  const applyHandler = useCallback(() => {
    if (settingsOptions?.dataCard && settingsOptions?.dashboardId) {
      dispatch(
        updateUserDataCardConfig({
          dashboardId: settingsOptions.dashboardId,
          dataCardId: settingsOptions.dataCard.id,
          configuration: currentValue,
        })
      );
      closeHandler();
    }
  }, [closeHandler, currentValue, dispatch, settingsOptions?.dashboardId, settingsOptions?.dataCard]);

  useEffect(() => setErrorMessage(''), [visible]);

  return (
    <DrawerPanel
      absolutePositioning
      afterVisibleChange={() => {}}
      className="insights-studio-settings"
      footer={
        <HStack justify="end">
          <Button onClick={closeHandler}>
            <TranslatedText namespace="Common" text="cancel" />
          </Button>
          <Button type="primary" onClick={applyHandler}>
            <TranslatedText namespace="Common" text="apply" />
          </Button>
        </HStack>
      }
      mask
      maskClosable={false}
      onClose={closeHandler}
      title={tn('title', { name: dataCard?.displayName })}
      visible={visible}
      width="large">
      <InlineMessage type="error" title={errorMessage}>
        {errorMessage}
      </InlineMessage>
      {!dataCard?.configurationMeta && <CenterLayout>{tn('no_configuration')}</CenterLayout>}
      {visible && dataCard?.configurationMeta && settingsOptions?.dashboardId && settingsOptions.dataCard && (
        <SettingsForm />
      )}
    </DrawerPanel>
  );
};

export default withI18n(SettingsModal, 'InsightsStudio.Settings');
