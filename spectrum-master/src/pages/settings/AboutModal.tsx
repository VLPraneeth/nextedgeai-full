//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Button, Modal, Tooltip } from 'antd';
import { useEffect } from 'react';

import BrandLogo from 'components/brand/BrandLogo';
import useUserLocalMoment from 'hooks/moment';
import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { showAboutPage } from 'store/user/actions';
import { useUserInfo } from 'store/user/hooks';
import { getVersion } from 'store/user/thunks';
import { SHORT_DATE_24_TIME_TZ_FORMAT } from 'utils/DateUtil';
import { tc, tNamespaced } from 'utils/i18nUtil';

import './AboutModal.less';

const tn = tNamespaced('AboutModal');

const AboutModal = () => {
  const dispatch = useEnhancedDispatch();
  const versionMetadata = useEnhancedSelector((state) => state.user.versionMetadata);
  const { isSyncariUser } = useUserInfo();
  const userMoment = useUserLocalMoment();

  const close = () => {
    dispatch(showAboutPage(false));
  };

  useEffect(() => {
    dispatch(getVersion());
  }, [dispatch]);

  return (
    <Modal
      title={tn('syncari')}
      centered
      className="about-modal"
      visible
      footer={
        <div className="footer-container">
          <div className="footer-item">
            {/* we use 2019 for copyright year to represent the first year content was published */}
            <p className="copyright">{tn('copyright', { currentYear: userMoment(new Date()).year() })}</p>
          </div>
          <div className="footer-item">
            <Button key="ok" type="primary" onClick={close}>
              {tc('close')}
            </Button>
          </div>
        </div>
      }
      onOk={close}
      onCancel={close}
    >
      <div>
        <BrandLogo className="login-logo" />
      </div>
      <p>
        <b>{`${tn('build_date')} `}</b>
        {versionMetadata?.buildDate
          ? userMoment(new Date(versionMetadata.buildDate)).format(SHORT_DATE_24_TIME_TZ_FORMAT)
          : ''}
      </p>
      <p>
        <Tooltip
          title={
            isSyncariUser && versionMetadata?.branchName
              ? tn('branch_name', { branchName: versionMetadata.branchName })
              : ''
          }
        >
          <b>{`${tn('commit_sha1')} `}</b>
          {versionMetadata?.gitSha1 ? versionMetadata.gitSha1 : ''}
        </Tooltip>
      </p>
    </Modal>
  );
};

export default AboutModal;
