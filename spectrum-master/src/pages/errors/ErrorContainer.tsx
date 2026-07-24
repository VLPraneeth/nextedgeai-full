//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Router } from '@reach/router';
import Error400 from './Error.400';
import Error404 from './Error404';
import Error504 from './Error504';
import ErrorUi from './ErrorUi';

const ErrorContainer = () => {
  return (
    <Router className="synri-error-container-router">
      <ErrorUi path="/error-ui" />
      <Error400 path="/error-400" />
      <Error404 path="/error-404" />
      <Error504 path="/error-504" />
      <Error404 default />
    </Router>
  );
};

export default ErrorContainer;
