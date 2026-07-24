//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Component } from 'react';
import { connect } from 'react-redux';

import ErrorUi from 'pages/errors/ErrorUi';
import { selectGlobalError, selectLastActions } from 'selectors/appSelectors';
import { ReduxError } from 'store/middleware/crashReporter';
import { phoneHome } from 'utils/ErrorUtils';

class ErrorBoundary extends Component<any> {
  state = { hasError: false };

  static getDerivedStateFromError(error: any) {
    return { hasError: true };
  }

  componentDidCatch(error: any, errorInfo: any) {
    const { sendState, allState, lastActions } = this.props as any;

    // if this was thrown by redux, pass it to phone home
    if (error instanceof ReduxError) {
      phoneHome({ error });
    } else {
      // otherwise, let's add some context to the error
      phoneHome({
        error,
        componentStack: errorInfo,
        state: sendState ? allState : {},
        actions: lastActions,
      });
    }
  }

  render() {
    const { children } = this.props;
    const { hasError } = this.state;

    if (hasError) {
      // show pretty Error screen for customers in production
      return (
        <div className="synri-error-boundary">
          <ErrorUi />
        </div>
      );
    }

    return children;
  }
}

(ErrorBoundary as any).defaultProps = {
  sendState: false,
};

export default connect((state) => ({
  allState: state, // you're in good hands
  lastActions: selectLastActions(state as any),
  globalError: selectGlobalError(state as any),
}))(ErrorBoundary);
