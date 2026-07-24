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

import './Error504.less';

const tn = tNamespaced('Error504');

interface Error504Props extends RouteComponentProps {
  /**
   * Action to hide the breadcrumbs and this component is mounted
   */
  hideBreadcrumbs: (hide: boolean) => void;
}

const Error504 = ({ hideBreadcrumbs }: Error504Props) => {
  useEffect(() => {
    hideBreadcrumbs(true);
    return () => {
      hideBreadcrumbs(false);
    };
  }, [hideBreadcrumbs]);

  return (
    <Err className="synri-error-504">
      <span className="synri-error-title">{tn('title')}</span>
      <span className="synri-error-description">{tn('description')}</span>
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
)(Error504);
