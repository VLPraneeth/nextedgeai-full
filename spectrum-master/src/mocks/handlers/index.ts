import accessControl from './accessControl';
import connectors from './connectors';
import customSynapse from './customSynapse';
import errorCatalog from './errorCatalog';
import importedFiles from './importedFiles';
import insights from './insights';
import instanceFeature from './instanceFeature';
import schema from './schema';
import specter from './specter';
import syncStudio from './syncStudio';
import tokens from './tokens';

const handlers = [
  accessControl,
  customSynapse,
  errorCatalog,
  insights,
  instanceFeature,
  importedFiles,
  syncStudio,
  tokens,
  specter,
  schema,
  connectors,
].flat();

export default handlers;
