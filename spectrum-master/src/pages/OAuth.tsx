//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import { oauthAuthorize } from 'actions/connectorActions';
import { ConnectorState } from 'reducers/connectorReducer';
import { RootState } from 'store/types';
import { ARCADE_V1_PREFIX } from 'utils/DataUrlConstants';

export interface OAuthProps extends ConnectorState {
  location: Window['location'];
}

class OAuth extends Component<any, ConnectorState> {
  componentDidMount() {
    const { pathname, search } = this.props.location;
    if (this.isOauthAuthorize()) {
      this.props.oauthAuthorize(`${ARCADE_V1_PREFIX}${pathname.replace(/^\/arcade\/api\/v1/i, '')}${search}`);
    }
  }

  componentDidUpdate(prevProps: ConnectorState) {
    if (this.props.oAuthAuthorizing === false && prevProps.oAuthAuthorizing === true && !this.props.oAuthErrorMsg) {
      if (this.isOauthAuthorize()) {
        const redirectUrl = this.props.oAuthData?.headers?.['x-syncari-oauth-redirect']
          ? this.props.oAuthData.headers['x-syncari-oauth-redirect']
          : null;
        if (redirectUrl && redirectUrl.includes('/consent')) {
          window.location.href = `${redirectUrl}`;
        } else {
          window.close();
        }
      }
    }
  }

  isOauthAuthorize(pathname?: string) {
    pathname = pathname || this.props.location.pathname;
    return pathname?.indexOf('oauth/authorize') !== -1 || pathname?.indexOf('oauth2/authorize') !== -1;
  }

  _getMessage = () => {
    if (this.props.oAuthErrorData) {
      const { error, message, trace } = this.props.oAuthErrorData;
      return (
        <div>
          <div>
            <b>Error:</b> {error}
          </div>
          <div>
            <b>Message:</b> {message}
          </div>
          <div>
            <b>Trace:</b> {trace}
          </div>
        </div>
      );
    } else if (this.isOauthAuthorize()) {
      return 'Authorizing…';
    }
  };

  render() {
    const message = this._getMessage();
    return <span>{message}</span>;
  }
}

const mapStateToProps = (state: RootState) => ({
  ...state.connector,
  oAuthErrorMsg: state.connector.oAuthErrorMsg,
  oAuthAuthorizing: state.connector.oAuthAuthorizing,
  oAuthData: state.connector.oAuthData,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators(
    {
      oauthAuthorize,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(OAuth);
