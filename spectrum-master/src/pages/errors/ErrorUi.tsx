//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//

import { RouteComponentProps } from '@reach/router';

import { tNamespaced } from 'utils/i18nUtil';

import Err from './Err';

const tn = tNamespaced('ErrorUi');

// eslint-disable-next-line no-empty-pattern
const ErrorUi = ({}: RouteComponentProps) => {
  return (
    <Err>
      <span className="synri-error-ui-title synri-error-title">{String(tn('oops'))}</span>
      <span className="synri-error-description">{String(tn('error_sent_to_syncari'))}</span>
      <span dangerouslySetInnerHTML={{ __html: String(tn('dont_give_up')) }} />
    </Err>
  );
};

export default ErrorUi;
