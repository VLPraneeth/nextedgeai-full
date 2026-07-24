//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import React, { useCallback, useEffect, useState } from 'react';

import { ReactComponent as SycariLogo } from 'assets/images/connectors/syncari-logo.svg';
import Button from 'components/Button';
import DrawerPanel from 'components/DrawerPanel';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import InlineMessage, { Types as InlineMessageTypes } from 'components/InlineMessage';
import InputWithLabel from 'components/inputs/InputWithLabel';
import { HStack, Spacer, Stack } from 'components/layout';
import TabPanelSpin from 'components/TabPanelSpin';
import { TranslatedText } from 'components/typography';
import { EmptyPanelContent } from 'pages/insights-studio/components/empty-panel-content/EmptyPanelContent';
import { useDisableFeatureMutation, useEnableFeatureMutation } from 'store/instance-feature/api';
import { useInstanceFeatures } from 'store/instance-feature/hooks';
import { InstanceFeature } from 'store/instance-feature/types';
import AppConstants from 'utils/AppConstants';

export interface InstanceFeatureModalProps {
  visible: boolean;
  show: (visible: boolean) => void;
}
const InstanceFeatureModal = ({ visible, show }: InstanceFeatureModalProps) => {
  const [enableFeature] = useEnableFeatureMutation();
  const [disableFeature] = useDisableFeatureMutation();
  const [errorMessage, setErrorMessage] = useState('');
  const { tn } = useI18nContext();
  const [formValues, setFormValues] = useState<Record<string, boolean | undefined>>({});
  const { features, refetch, isLoading, isFetching, hasVisibleFeatures, visibleFeatures } = useInstanceFeatures();

  useEffect(() => {
    setErrorMessage('');
    if (!visible) {
      setFormValues({});
    } else {
      refetch();
    }
  }, [refetch, visible]);

  const featuresLoading = isFetching || isLoading;

  const closeHandler = useCallback(() => show(false), [show]);

  const applyHandler = useCallback(() => {
    setErrorMessage('');
    if (!Object.keys(formValues).length) {
      closeHandler();
      return;
    }
    const promises: Promise<InstanceFeature>[] = [];
    Object.keys(formValues).forEach((key) => {
      const feature = features?.find((feature) => feature.name === key);
      // Ignore if its not in the feature list or it has the same value
      if (!feature || feature?.enabled === formValues[key]) {
        return;
      }
      formValues[key] ? promises.push(enableFeature(key).unwrap()) : promises.push(disableFeature(key).unwrap());
    });
    Promise.all(promises)
      .then(() => closeHandler())
      .catch((err) => setErrorMessage(err.data.message));
  }, [closeHandler, disableFeature, enableFeature, features, formValues]);

  return (
    <DrawerPanel
      absolutePositioning
      className="instance-feature-modal"
      footer={
        <HStack justify="end">
          {hasVisibleFeatures ? (
            <>
              <Button onClick={closeHandler}>
                <TranslatedText namespace="Common" text="cancel" />
              </Button>
              <Spacer x="md" />
              <Button type="primary" onClick={applyHandler} disabled={featuresLoading}>
                <TranslatedText namespace="Common" text="apply" />
              </Button>
            </>
          ) : (
            <Button type="primary" onClick={closeHandler}>
              <TranslatedText namespace="Common" text="close" />
            </Button>
          )}
        </HStack>
      }
      onClose={closeHandler}
      title={tn('title')}
      visible={visible}
      width="standard">
      <TabPanelSpin spinning={featuresLoading}>
        <Stack>
          <InlineMessage type={InlineMessageTypes.ERROR} title={errorMessage}>
            {errorMessage}
          </InlineMessage>
          {visibleFeatures?.length ? (
            visibleFeatures.map((instanceFeature) => {
              return (
                <InputWithLabel
                  id={instanceFeature.name}
                  key={instanceFeature.name}
                  name={instanceFeature.name}
                  data-testid={`instanceFeature${instanceFeature.name}`}
                  label={instanceFeature.displayName}
                  datatype={AppConstants.INPUT_TYPE.CHECKBOX}
                  tooltip={instanceFeature.description}
                  checked={formValues[instanceFeature.name] ?? instanceFeature.enabled}
                  onChange={(evt: React.ChangeEvent<HTMLInputElement>) =>
                    setFormValues({ ...formValues, [instanceFeature.name]: evt.target.checked })
                  }
                />
              );
            })
          ) : (
            <EmptyPanelContent icon={<SycariLogo width={48} height={48} />}>
              <TranslatedText text="no_available_features" size="xl" />
            </EmptyPanelContent>
          )}
        </Stack>
      </TabPanelSpin>
    </DrawerPanel>
  );
};

export default withI18n(InstanceFeatureModal, 'InstanceFeatureModal');
