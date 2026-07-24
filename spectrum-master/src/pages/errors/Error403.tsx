import { tNamespaced } from 'utils/i18nUtil';

import Err from './Err';

const tn = tNamespaced('Error403');

export const Error403 = () => {
  return (
    <Err>
      <span className="synri-error-title">{tn('title')}</span>
      <span className="synri-error-description">{tn('permission_error')}</span>
      <span>{tn('contact_your_admin')}</span>
    </Err>
  );
};
