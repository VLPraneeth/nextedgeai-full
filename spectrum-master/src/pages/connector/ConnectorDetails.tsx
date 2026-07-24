//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Spin } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';

import EmptyGraphPanel from 'components/EmptyGraphPanel';
import { withI18n, useI18nContext } from 'components/I18nProvider';
import Modal from 'components/Modal';
import { useEnhancedSelector } from 'hooks/redux';
import { selectConnectorsMetadata } from 'selectors/connectorSelectors';
import { useGetCapabilitiesQuery } from 'store/connector-meta/api';

import { useConnectorDetailsContext } from './ConnectorDetailsContext';

import './ConnectorDetails.scss';

const ZENDESK_DOC_STYLESHEET = 'https://p20.zdassets.com/hc/theming_assets/10329579/360004645291/style.css';

const ConnectorDetails = withI18n(() => {
  const { visible, metaId = '', showConnectorDetails } = useConnectorDetailsContext();
  const connectorsMetadata = useEnhancedSelector(selectConnectorsMetadata);
  const { tc, tn } = useI18nContext();

  const { data, isLoading, isFetching, isError } = useGetCapabilitiesQuery({ metaId }, { skip: !Boolean(metaId) });

  const onClose = () => showConnectorDetails(false, '');
  const contentRef = useRef<HTMLIFrameElement>(null);

  const [content, setContent] = useState<Record<string, string>>({});

  useEffect(() => {
    if (visible && !isLoading && !isFetching && data && !isError) {
      setContent({
        [metaId]: data,
      });
    }
  }, [data, isError, isFetching, isLoading, metaId, visible]);

  useEffect(() => {
    if (contentRef?.current && content[metaId]) {
      // Update our iframe doc with the help article content
      const doc = contentRef.current.contentWindow?.document;
      if (doc) {
        doc.open();
        doc.write(`
          <!DOCTYPE html>
          <html>
            <head>
              <link rel="stylesheet" href="${ZENDESK_DOC_STYLESHEET}">
            <head>
            <body>
              <div class="layout">
                <section class="content">
                  ${content[metaId]}
                </section>
              </div>
            </body>
          </html>
        `);
        doc.close();
      }
    }
  }, [content, metaId]);

  const onLoad = () => {
    const iFrameDoc = contentRef?.current?.contentWindow?.document;
    if (iFrameDoc) {
      //  Open the link in a new tab due to iframe sameorigin security restriction.
      iFrameDoc.querySelectorAll('a').forEach((link) => {
        link.hostname !== window.location.hostname && link.setAttribute('target', '_blank');
      });
    }
  };

  const connectorMetadata = useMemo(() => connectorsMetadata?.find((meta) => meta.id === metaId), [
    connectorsMetadata,
    metaId,
  ]);

  const title = useMemo(() => {
    const displayName = connectorMetadata?.displayName;
    return displayName ? tn('title', { connectorMetadataName: displayName }) : tc('summary');
  }, [connectorMetadata?.displayName, tc, tn]);

  return (
    <Modal
      title={title}
      centered
      width="80vw"
      visible={visible}
      className="connector-details-modal"
      onOk={onClose}
      onCancel={onClose}
      footer={
        <Button type="primary" onClick={onClose}>
          {tc('close')}
        </Button>
      }
      destroyOnClose>
      <div className="connector-details-modal__container">
        <Spin spinning={isLoading || isFetching}>
          <>
            {!content[metaId] && connectorMetadata?.displayName && !isLoading && !isFetching ? (
              <EmptyGraphPanel icon={connectorMetadata.iconUri}>
                {tn('summary_not_found', { connectorMetadataName: connectorMetadata.displayName })}
              </EmptyGraphPanel>
            ) : (
              <iframe id="connector-details-modal__content" ref={contentRef} title={tc('summary')} onLoad={onLoad} />
            )}
          </>
        </Spin>
      </div>
    </Modal>
  );
}, 'ConnectorDetails');

export { ConnectorDetails };
