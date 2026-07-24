//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';

import Login from 'pages/authentication/Login';
import { RootState } from 'store/types';
import { getCsrfToken, login, logout } from 'store/user/thunks';

export type LoginContainerProps = ReturnType<typeof mapStateToProps> & ReturnType<typeof mapDispatchToProps>;

class LoginContainer extends Component<LoginContainerProps> {
  render() {
    return <Login {...this.props} />;
  }
}

const mapStateToProps = (state: RootState) => ({
  ...state.user,
  fetchingLoginStatus: state.user.fetchingLoginStatus,
  csrfToken: state.user.csrfToken,
  errorMessage: state.user.errorMessage,
});

const mapDispatchToProps = (dispatch: Dispatch) => {
  return bindActionCreators(
    {
      login,
      logout,
      getCsrfToken,
    },
    dispatch
  );
};

export default connect(mapStateToProps, mapDispatchToProps)(LoginContainer);
