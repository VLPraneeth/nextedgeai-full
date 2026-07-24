import { RouteComponentProps, navigate } from '@reach/router';
import { Button, Modal } from 'antd';
import { useEffect } from 'react';
import { useSelector } from 'react-redux';

import { TranslatedText } from 'components/typography';
import { useEnhancedDispatch } from 'hooks/redux';
import AuthenticationWrapper from 'pages/authentication/AuthenticationWrapper';
import { MarketingContent } from 'pages/authentication/Login';
import { selectUserEmail } from 'store/user/selectors';
import { getProfile } from 'store/user/thunks';
import { tNamespaced, tc } from 'utils/i18nUtil';
import RouteConstants from 'utils/RouteConstants';
import { makeUrl } from 'utils/UrlUtil';

import { ExitToAppButton } from './ExitToAppButton';

import './DashboardExpired.scss';

const tn = tNamespaced('InsightsStudio');

export default function DashboardExpired({
  dashboardId,
  email: emailFromUrl,
}: RouteComponentProps & { dashboardId: string; email: string }) {
  const dispatch = useEnhancedDispatch();
  const loggedinUserEmail = useSelector(selectUserEmail);
  useEffect(() => {
    if (!loggedinUserEmail) {
      dispatch(getProfile()).then((data) => {
        if (!data) {
          navigate(
            makeUrl(RouteConstants.INSIGHTS_STUDIO_SHARED_DASHBOARD_ENTER_PASSWORD, {
              dashboardId,
              email: emailFromUrl,
            })
          );
        }
      });
    }
  }, [dispatch, loggedinUserEmail, dashboardId, emailFromUrl]);
  return (
    <div className="dashboard-expired">
      <div className="dashboard-expired__marketing-content">
        <MarketingContent />
      </div>
      <AuthenticationWrapper
        className="dashboard-expired__wrapper"
        footer={<TranslatedText namespace="Login" beDangerous text="demo" />}>
        <div className="dashboard-expired__text">{tn('InsightsSharing.all_dashboard_expired')}</div>
        <ExitToAppButton className="dashboard-expired__exit-button" />
      </AuthenticationWrapper>
    </div>
  );
}

interface DashboardExpiredModalProps {
  handleClose: () => void;
  visible: boolean;
}

export function DashboardExpiredModal({ handleClose, visible }: DashboardExpiredModalProps) {
  return (
    <Modal
      title={tn('InsightsSharing.dashboard_expired_title')}
      visible={visible}
      onCancel={handleClose}
      onOk={handleClose}
      centered
      footer={
        <Button type="primary" onClick={handleClose}>
          {tc('ok')}
        </Button>
      }>
      <div className="dashboard-expired__text">{tn('InsightsSharing.dashboard_expired')}</div>
    </Modal>
  );
}
