import { Component, PropsWithChildren } from 'react';

import { tNamespaced } from 'utils/i18nUtil';

import { DataCardError } from '../data-card-error/DataCardError';

const tn = tNamespaced('DataCardError');

interface DataCardErrorBoundaryErrorMessage {
  message?: string;
  stack?: string;
}

interface DataCardErrorBoundaryState {
  hasError: boolean;
  error: {
    rawErrorMessage?: string;
    message: string;
  };
}

class DataCardErrorBoundary extends Component<PropsWithChildren<void>> {
  state: DataCardErrorBoundaryState = {
    hasError: false,
    error: {
      message: '',
    },
  };

  static getDerivedStateFromError(error: DataCardErrorBoundaryErrorMessage) {
    // TODO: Parse the error from the datacard and show appropriate error
    return {
      hasError: true,
      error: {
        rawErrorMessage: error?.message,
        message: tn('generic_configuration_error'),
      },
    };
  }

  render() {
    if (this.state.hasError) {
      // Note: Show the raw chart error message for now but
      // we should show an actionable user error in the future.
      return (
        <DataCardError
          tooltip={this.state.error.rawErrorMessage}
          error={{ title: tn('something_went_wrong'), body: this.state.error.message }}
        />
      );
    }

    return this.props.children;
  }
}

export default DataCardErrorBoundary;
