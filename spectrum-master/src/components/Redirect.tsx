//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { navigate, RouteComponentProps } from '@reach/router';
import { useEffect } from 'react';

interface RedirectProps extends RouteComponentProps {
  /** url to redirect to */
  redirectTo?: string;
  replace?: boolean;
}

const Redirect = ({ redirectTo, replace = false }: RedirectProps) => {
  useEffect(() => {
    if (redirectTo) {
      navigate(redirectTo, { replace });
    }
  }, [redirectTo, replace]);

  return null;
};

export default Redirect;
