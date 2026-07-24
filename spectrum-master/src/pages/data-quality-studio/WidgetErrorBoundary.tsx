import { Component, ReactNode } from 'react';

import { phoneHome } from 'utils/ErrorUtils';

import { WidgetErrorState } from './WidgetContents';

class WidgetErrorBoundary extends Component<{ children: ReactNode }> {
  state = {
    hasError: false,
    error: null,
  };

  static sendErrorToServer(error: Error, componentStack: any) {
    phoneHome({
      error,
      componentStack,
    });
  }

  static getDerivedStateFromError(error: Error) {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: any) {
    WidgetErrorBoundary.sendErrorToServer(error, info);
  }

  render() {
    if (this.state.hasError) {
      return <WidgetErrorState />;
    }

    return this.props.children;
  }
}

export default WidgetErrorBoundary;
