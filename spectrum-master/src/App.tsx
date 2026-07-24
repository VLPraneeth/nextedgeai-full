//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Provider } from 'react-redux';

import ErrorBoundary from 'components/ErrorBoundary';
import GlobalErrorBoundary from 'components/GlobalErrorBoundary';
import { NavigateConfirmationModal } from 'components/NavigateConfirmationModal';
import MainApp from 'pages/MainApp';
import configureStore from 'store/configureStore';
import { init as i18nInit } from 'utils/i18nUtil';

import 'antd/dist/antd.css';

import './App.less';

const store = configureStore();

i18nInit();

function App() {
  return (
    <GlobalErrorBoundary>
      <Provider store={store}>
        <ErrorBoundary sendState={false}>
          <NavigateConfirmationModal>
            <MainApp />
          </NavigateConfirmationModal>
        </ErrorBoundary>
      </Provider>
    </GlobalErrorBoundary>
  );
}

export default App;
