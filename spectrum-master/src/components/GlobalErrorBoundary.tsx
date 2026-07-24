//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Component } from 'react';

import { isHighChartsError } from 'components/vizer/utils/useHighchartsAxisGraph';
import ChunkLoadError, { ChunkLoadErrorType } from 'pages/errors/ChunkLoadError';
import ErrorUi from 'pages/errors/ErrorUi';
import { phoneHome } from 'utils/ErrorUtils';

class GlobalErrorBoundary extends Component<any, any> {
  static getDerivedStateFromError(error: any) {
    return { hasError: true, errorType: error.name };
  }

  static sendErrorToServer(error: any, componentStack: any) {
    phoneHome({
      error,
      componentStack,
    });
  }

  constructor(props: any) {
    super(props);

    this.state = {
      hasError: false,
      errorType: null,
    };

    this.handleGlobalError = this.handleGlobalError.bind(this);
  }

  componentDidMount() {
    window.addEventListener('error', this.handleGlobalError);
    window.addEventListener('uncaughtrejection', this.handleGlobalError);
  }

  componentWillUnmount() {
    window.removeEventListener('error', this.handleGlobalError);
    window.removeEventListener('uncaughtrejection', this.handleGlobalError);
  }

  componentDidCatch(error: any, info: any) {
    GlobalErrorBoundary.sendErrorToServer(error, info);
  }

  handleGlobalError(evt: any) {
    const err = evt?.error;
    // Skip global handler if its a highcharts error.
    // The highchart error boundary will show the appropriate error
    // message
    if (isHighChartsError(err?.message)) {
      return;
    }

    if (err) {
      this.setState({ hasError: true, errorType: err.name });
      GlobalErrorBoundary.sendErrorToServer(err, null);
    }
  }

  render() {
    const { children } = this.props;
    const { hasError, errorType } = this.state;

    if (hasError) {
      // show pretty Error screen for customers in production
      return (
        <div className="synri-error-boundary">
          {errorType === ChunkLoadErrorType ? <ChunkLoadError /> : <ErrorUi />}
        </div>
      );
    }

    return children;
  }
}

export default GlobalErrorBoundary;
