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

const tn = tNamespaced('Error404');

interface Error404Props extends RouteComponentProps {
  /**
   * Action to hide the breadcrumbs and this component is mounted
   */
  hideBreadcrumbs: (hide: boolean) => any;
}

const Error404 = ({ hideBreadcrumbs, location }: Error404Props) => {
  useEffect(() => {
    hideBreadcrumbs(true);
    return () => {
      hideBreadcrumbs(false);
    };
  }, [hideBreadcrumbs]);

  const urlParams = new URLSearchParams(location?.search);
  const errorType = urlParams.get('errorType');
  const errorMessage = urlParams.get('message');

  return (
    <Err>
      <span className="synri-error-title">{tn('title')}</span>
      <span className="synri-error-description">{tn('cannot_be_found')}</span>
      <span>{errorMessage ? decodeURIComponent(errorMessage) : tn('wrong_address')}</span>
      {!(errorType === 'auth') ? <span dangerouslySetInnerHTML={{ __html: String(tn('dont_give_up')) }} /> : null}
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
)(Error404);
