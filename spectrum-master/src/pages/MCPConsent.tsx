import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import { Card, List, Typography, message, Icon } from 'antd';
import { useEffect, useState } from 'react';
import Button from 'components/Button';
import { oauthAuthorize } from 'actions/connectorActions';
import { ConnectorState } from 'reducers/connectorReducer';
import { RootState } from 'store/types';
import { useI18nContext, withI18n } from 'components/I18nProvider';
import { getProfile } from 'store/user/thunks';
import { useEnhancedDispatch } from 'hooks/redux';
import RouteConstants from 'utils/RouteConstants';
import BrandLogo from 'components/brand/BrandLogo';

const { Title, Text } = Typography;

export interface MCPConsentProps extends ConnectorState {
  location: Window['location'];
  oauthAuthorize: (url: string) => void;
  oAuthData?: any;
}

const MCPConsent = (props: MCPConsentProps) => {
  const { tn } = useI18nContext();
  const { location, oAuthErrorMsg, oAuthErrorData, oAuthAuthorizing, oAuthData } = props;
  const dispatch = useEnhancedDispatch();
  const [hasConsented, setHasConsented] = useState(false);

  useEffect(() => {
    dispatch(getProfile()).catch(() => {
      window.location.href = `${RouteConstants.LOGIN}?redirect=${encodeURIComponent(location.href)}`;
    });
  }, [dispatch, location.href]);

  useEffect(() => {
    if (!oAuthAuthorizing && !oAuthErrorMsg && hasConsented) {
      if (oAuthData?.headers?.['x-syncari-oauth-redirect']) {
        const redirectUrl = oAuthData.headers['x-syncari-oauth-redirect'];
        window.location.href = redirectUrl;
      }
    }
  }, [oAuthAuthorizing, oAuthErrorMsg, hasConsented, oAuthData]);

  const permissions = [
    {
      name: tn('permissions.read.name'),
      description: tn('permissions.read.description'),
    },
    {
      name: tn('permissions.run_actions.name'),
      description: tn('permissions.run_actions.description'),
    },
    {
      name: tn('permissions.read_schema.name'),
      description: tn('permissions.read_schema.description'),
    },
  ];

  const handleAccept = () => {
    setHasConsented(true);
    const { search } = location;
    const urlParams = new URLSearchParams(search);

    const redirectUri = urlParams.get('redirect_uri') || '';

    const otherParams = Array.from(urlParams.entries())
      .filter(([key]) => key !== 'redirect_uri')
      .reduce(
        (acc, [key, value]) => {
          acc[key] = value;
          return acc;
        },
        {} as Record<string, string>
      );

    const redirectUriWithParams = new URL(redirectUri);
    Object.entries(otherParams).forEach(([key, value]) => {
      redirectUriWithParams.searchParams.set(key, value);
    });

    const newParams = new URLSearchParams();
    newParams.set('redirect_uri', redirectUriWithParams.toString());
    newParams.set('consent', 'accepted');
    const newUrl = `${location.pathname}?${newParams.toString()}`;

    props.oauthAuthorize(newUrl);
  };

  const handleReject = () => {
    message.error(
      <div>
        <p>{tn('consent_rejected_message')}</p>
        <Button
          type="primary"
          size="small"
          style={{ marginTop: '8px' }}
          onClick={() => {
            window.location.href = RouteConstants.LOGIN;
          }}
        >
          {tn('return_to_login')}
        </Button>
      </div>,
      0
    );
  };

  if (oAuthErrorMsg || oAuthErrorData) {
    return (
      <div style={{ padding: '20px' }}>
        <Title level={4}>{tn('authorization_error')}</Title>
        <Text type="danger">
          {oAuthErrorMsg ||
            (oAuthErrorData && typeof oAuthErrorData === 'object' && 'message' in oAuthErrorData
              ? oAuthErrorData.message
              : tn('unknown_error'))}
        </Text>
      </div>
    );
  }

  return (
    <div style={{ padding: '20px', maxWidth: '600px', margin: '0 auto' }}>
      <Card>
        <div style={{ textAlign: 'center', marginBottom: '24px' }}>
          <BrandLogo style={{ height: '40px', width: 'auto' }} />
        </div>
        <Title level={3}>{tn('title')}</Title>
        <Text>{tn('permissions_requested')}</Text>

        <List
          style={{ margin: '20px 0' }}
          dataSource={permissions}
          renderItem={(item) => (
            <List.Item>
              <List.Item.Meta
                avatar={
                  item.name === tn('permissions.read.name') ? (
                    <Icon type="book" style={{ fontSize: '24px', marginTop: 10 }} />
                  ) : item.name === tn('permissions.run_actions.name') ? (
                    <Icon type="play-circle" style={{ fontSize: '24px', marginTop: 10 }} />
                  ) : (
                    <Icon type="database" style={{ fontSize: '24px', marginTop: 10 }} />
                  )
                }
                title={item.name}
                description={item.description}
              />
            </List.Item>
          )}
        />

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <Button type="default" onClick={handleReject}>
            {tn('reject')}
          </Button>
          <Button type="primary" onClick={handleAccept}>
            {tn('accept')}
          </Button>
        </div>
      </Card>
    </div>
  );
};

const mapStateToProps = (state: RootState) => ({
  ...state.connector,
  oAuthErrorMsg: state.connector.oAuthErrorMsg,
  oAuthAuthorizing: state.connector.oAuthAuthorizing,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators(
    {
      oauthAuthorize,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(withI18n(MCPConsent, 'MCPConsent'));
