//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button } from 'antd';

import { withI18n, useI18nContext } from 'components/I18nProvider';
import Modal from 'components/Modal';
import { Text } from 'components/typography';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadFile } from 'utils/DownloadUtil';

export interface DownloadSampleModalProps {
  onClose: () => void;
  visible: boolean;
}
const DownloadSampleModal = withI18n(({ onClose, visible }: DownloadSampleModalProps) => {
  const { tc, tn } = useI18nContext();

  const download = () => {
    downloadFile(DataUrlConstants.SDK_CUSTOM_SYNAPSE_DOWNLOAD_SAMPLE);
    onClose();
  };

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
          <Button onClick={onClose}>{tc('cancel')}</Button>
          <Button type="primary" onClick={download}>
            {tc('download')}
          </Button>
        </>
      }
      destroyOnClose>
      <Text size="md" beDangerous>
        {tn('description')}
      </Text>
    </Modal>
  );
}, 'DownloadSampleModal');

export { DownloadSampleModal };
