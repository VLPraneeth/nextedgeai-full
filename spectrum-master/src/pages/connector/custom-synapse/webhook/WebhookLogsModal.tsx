import { Button, message } from 'antd';
import { useState } from 'react';

import { showWebhookLogsModal } from 'actions/connectorActions';
import DrawerPanel from 'components/DrawerPanel';
import { HStack, Stack } from 'components/layout';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import DataUrlConstants from 'utils/DataUrlConstants';
import { downloadCsvDataAsFile } from 'utils/DownloadUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';
import { makeUrl } from 'utils/UrlUtil';

import { WebhookLogsTable } from './WebhookLogsTable';

import './WebhookLogsModal.scss';

const tn = tNamespaced('CustomSynapse.WebhookCustomSynapse');

export const WebhookLogsModal = () => {
  const connectorId = useEnhancedSelector((state) => state.connector.connectorId);

  const [isFileExporting, setIsFileExporting] = useState(false);

  const dispatch = useEnhancedDispatch();

  const close = () => {
    dispatch(showWebhookLogsModal(false));
  };

  return (
    <DrawerPanel
      keyboard={false}
      maskClosable={false}
      onClose={close}
      destroyOnClose
      title={tn('webhook_logs')}
      visible
      width="xlarge">
      <div className="webhook_logs_modal">
        <Stack spacing="md">
          <HStack justify="end">
            <Button
              loading={isFileExporting}
              onClick={() => {
                if (!connectorId) {
                  return;
                }
                setIsFileExporting(true);
                downloadReferenceData(connectorId)
                  .catch(() => message.error(tc('download_failed')))
                  .finally(() => setIsFileExporting(false));
              }}>
              {tc('export_as_csv')}
            </Button>
          </HStack>
          <WebhookLogsTable connectorId={connectorId} />
        </Stack>
      </div>
    </DrawerPanel>
  );
};

const downloadReferenceData = (connectorId: string) => {
  const fileName = `${connectorId}.csv`;
  const url = makeUrl(DataUrlConstants.WEBHOOK_CUSTOM_SYNAPSE_LOGS_EXPORT, {}, { connectorId });

  return downloadCsvDataAsFile(fileName, url);
};
