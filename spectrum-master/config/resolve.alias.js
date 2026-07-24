//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
const paths = require('./paths');

module.exports = {
  // Utilities alias
  utils: `${paths.appSrc}/utils`,
  // Components alias
  components: `${paths.appSrc}/components`,
  // Our view containers
  containers: `${paths.appSrc}/containers`,
  // Redux selectors path alias
  selectors: `${paths.appSrc}/selectors`,
  // Redux actions path alias
  actions: `${paths.appSrc}/actions`,
  // Redux reducers
  reducers: `${paths.appSrc}/reducers`,
  // Assets alias
  assets: `${paths.appSrc}/assets`,
  // Pages alias
  pages: `${paths.appSrc}/pages`,
  // store alias
  store: `${paths.appSrc}/store`,
  // i18n resources alias
  i18nRes: `${paths.appSrc}/i18n`,
  // styles alias
  styles: `${paths.appSrc}/styles`,
  // test alias
  tests: `${paths.appSrc}/tests`,
  // Application contexts aliases
  contexts: `${paths.appSrc}/contexts`,
  // Custom hooks alias
  hooks: `${paths.appSrc}/hooks`,
  mocks: `${paths.appSrc}/mocks`,
};
