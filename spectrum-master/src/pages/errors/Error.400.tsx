//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { RouteComponentProps } from '@reach/router';
import { useEffect } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';

import { hideBreadcrumbs } from 'store/user/actions';
import { tNamespaced } from 'utils/i18nUtil';

import Err from './Err';

const tn = tNamespaced('Error400');

interface Error400Props extends RouteComponentProps {
  /**
   * Action to hide the breadcrumbs and this component is mounted
   */
  hideBreadcrumbs: (hide: boolean) => any;
}

const Error400 = ({ hideBreadcrumbs }: Error400Props) => {
  useEffect(() => {
    hideBreadcrumbs(true);
    return () => {
      hideBreadcrumbs(false);
    };
  }, [hideBreadcrumbs]);

  const urlParams = new URLSearchParams(location?.search);
  const errorMessage = urlParams.get('message');

  return (
    <Err>
      <span className="synri-error-title">{tn('title')}</span>
      <span className="synri-error-description">{tn('bad_request')}</span>
      <span>{errorMessage ? decodeURIComponent(errorMessage) : tn('wrong_address')}</span>

      <span>{tn('contact_support')}</span>
    </Err>
  );
};

export default connect(
  (state: any) => ({}),
  (dispatch: any) => {
    return bindActionCreators(
      {
        hideBreadcrumbs,
      },
      dispatch
    );
  }
)(Error400);
