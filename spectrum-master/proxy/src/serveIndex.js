import fs from 'fs';
import path from 'path';

import { template } from 'lodash';

import { getXsrfCookie } from 'utils/AppUtil';

function serveIndex(req, res, _next) {
  const data = fs.readFileSync(path.resolve(__dirname, 'public', 'index.template'), 'utf8');

  if (data) {
    res.send(template(data)({ token: getXsrfCookie(req, res) }));
  }
}

export default serveIndex;
