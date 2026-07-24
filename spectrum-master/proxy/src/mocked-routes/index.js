import { replace } from 'lodash';

// The key is the request.path with forward slashes replaced with underscores
// Set the values to `true` to enable mocking the request.

// Use `devRoutes` for mocking during development. These will not be available in production environments.
const devRoutes = {
  _api_v1_studio_data_meta: false,
  _api_v1_quickstart_author_dynamicStep_1: false,
  _api_v1_quickstart_author_list: false,
  _api_v1_quickstart_author_config: false,
  _api_v1_quickstart_install_615241f761c8f1e4ca3b562c: false,
  _api_v1_pipeline_entityPipeline_syncMetric_61b1465f018b091720407c66: false,
  _api_v1_quickstart_install_dynamicStep_1: false,
  _api_v1_user_instances: false,
  _api_v1_actions_http_validate: false,
  _api_v1_actions_http_testing: false,
  _api_v1_nodeConfig_63ea6cb0a00a4ed2219008cb: false,
  _api_v1_nodeConfig_63f7c7a9de8a6542c93bcb36: false,

  _api_v1_insights_dashboard: false,
  _api_v1_insights_dashboard_dash1: true,
  _api_v1_insights_dashboard_dash1_datacard_barCard: true,
  _api_v1_insights_dashboard_dash1_datacard_columnCard: true,
  _api_v1_insights_dashboard_dash1_datacard_lineCard: true,
  _api_v1_insights_dashboard_dash1_datacard_lineCardMultiple: true,
  _api_v1_insights_dashboard_dash1_datacard_metricCard: true,
  _api_v1_insights_dashboard_dash1_datacard_tableCard: true,
  // mock error cards
  _api_v1_insights_dashboard_dash1_datacard_customError: true,
  _api_v1_insights_dashboard_dash1_datacard_emptyData: true,

  _api_v1_insights_datacard_author: false,
};

// Use prodRoutes to make mock endpoint available in all environments, including production.
const prodRoutes = {
  _api_v1_insights_dashboard_sampleDash1: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_arrByQuarter: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_arrByType: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_currentArr: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_issuesIn7Days: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_mqlCount: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_mqlToSqlRatio: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_pipelineByClose: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_salesFunnel: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_topTenCustomers: true,
  _api_v1_insights_dashboard_sampleDash1_datacard_userGrowth: true,
};

const mockedRoutes = process.env.NODE_ENV === 'production' ? prodRoutes : { ...devRoutes, ...prodRoutes };
console.log('mockedRoutes', mockedRoutes);
console.log('process.env.NODE_ENV', process.env.NODE_ENV);

export const mockedRoutesInitialize = (app) => {
  app.use('/arcade', function (req, res, next) {
    const pathKey = replace(req.path, /\//g, '_');

    const mockedData = mockedRoutes[pathKey];
    if (mockedData) {
      console.log('Sending mock response for:', req.path);
      const stringifiedData = JSON.stringify(require(`./${pathKey}`));

      res.setHeader('Content-Length', Buffer.byteLength(stringifiedData));
      res.send(stringifiedData);
    } else {
      next();
    }
  });
};
