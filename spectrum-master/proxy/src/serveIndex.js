import fs from 'fs';
import path from 'path';

import { template } from 'lodash';

import { getXsrfCookie } from 'utils/AppUtil';

function serveIndex(req, res, _next) {
  const data = fs.readFileSync(path.resolve(__dirname, 'public', 'index.template'), 'utf8');

  if (data) {
    res.set('Cache-Control', 'no-store, private');
    res.set('Pragma', 'no-cache');
    res.send(
      template(data)({
        token: getXsrfCookie(req, res),
        demoAdminEmail: process.env.NEXTEDGE_ADMIN_EMAIL || '',
        demoAdminPassword: process.env.NEXTEDGE_ADMIN_PASSWORD || '',
        demoGuidedEmail: process.env.NEXTEDGE_GUIDED_DEMO_EMAIL || '',
        demoGuidedPassword: process.env.NEXTEDGE_GUIDED_DEMO_PASSWORD || '',
      })
    );
  }
}

export default serveIndex;
