//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';

import { useI18nContext, withI18n } from 'components/I18nProvider';
import Modal from 'components/Modal';
import Spinner from 'components/Spinner';
import { Text } from 'components/typography';
import { useGetSyncariSdkInfoQuery } from 'store/connectors/api';

export interface SdkInfoModalProps {
  onClose: () => void;
  visible: boolean;
}
const SdkInfoModal = withI18n(({ onClose, visible }: SdkInfoModalProps) => {
  const { tc, tn } = useI18nContext();

  const { data, isLoading } = useGetSyncariSdkInfoQuery();

  let content = (
    <Text size="md" beDangerous>
      {tn('latest_stable_version', { versionNumber: data?.version })}
    </Text>
  );

  if (isLoading) {
    content = <Spinner />;
  }

  return (
    <Modal
      title={tn('title')}
      centered
      width="500px"
      visible={visible}
      onOk={onClose}
      onCancel={onClose}
      footer={
        <>
          <Button type="primary" onClick={onClose}>
            {tc('close')}
          </Button>
        </>
      }
      destroyOnClose>
      {content}
    </Modal>
  );
}, 'SdkInfoModal');

export { SdkInfoModal };
