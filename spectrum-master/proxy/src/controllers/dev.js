//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Dev controller
//

import { Router } from 'express';

import { getConfigVariables } from 'utils/ConfigUtil';

const router = Router();

router.route('/').get((_req, res) => {
  res.send(JSON.stringify({}));
});

router.route('/env').get((_req, res) => {
  res.send(JSON.stringify(getConfigVariables()));
});

export default router;
