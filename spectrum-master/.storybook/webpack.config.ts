//
// Copyright (c) 2019-Present Syncari All rights reserved.
//

const alias = require('../config/resolve.alias');
const lessModuleRegex = /\.module\.(less|less)$/;
const lessRegex = /\.less$/;
const local = require('../config/webpack.config');

export default {
  resolve: {
    alias,
  },
};
