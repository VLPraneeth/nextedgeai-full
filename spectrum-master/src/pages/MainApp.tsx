//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Router } from '@reach/router';
import * as React from 'react';

import RouteSpin from 'components/RouteSpin';
import { EnhancedReactLazy } from 'utils/ModuleUtils';
import RouteConstants from 'utils/RouteConstants';

import ResetPassword from './authentication/ResetPassword';
import './MainApp.less';

// load our likely routes concurrently, lazy load the rest
const Home = EnhancedReactLazy(() => import('containers/HomeContainer'), { loadConcurrently: true });
const LandingPage = EnhancedReactLazy(() => import('pages/landing/LandingPage'), { loadConcurrently: true });
const Login = EnhancedReactLazy(() => import('containers/LoginContainer'), { loadConcurrently: true });
const Errors = EnhancedReactLazy(() => import('pages/errors/ErrorContainer'), { loadConcurrently: true });
const Error404 = EnhancedReactLazy(() => import('pages/errors/Error404'), { loadConcurrently: true });

const OAuth = EnhancedReactLazy(() => import('pages/OAuth'));
const MCPConsent = EnhancedReactLazy(() => import('pages/MCPConsent'));
const SwitchInstance = EnhancedReactLazy(() => import('pages/SwitchInstance'));
const UpdatePassword = EnhancedReactLazy(() => import('pages/authentication/UpdatePassword'));
const PasswordResetSuccess = EnhancedReactLazy(() => import('pages/authentication/PasswordResetSuccess'));
const ForgotPassword = EnhancedReactLazy(() => import('pages/authentication/ForgotPassword'));
const ExternalAction = EnhancedReactLazy(() => import('pages/integrations/ExternalAction'));
const ErrorNotificationValidateEmail = EnhancedReactLazy(
  () => import('pages/settings/notifications/emails/ValidateEmail')
);
const SharedDashboard = EnhancedReactLazy(() => import('pages/insights-studio/dashboard-sharing/SharedDashboard'));
const PasswordPage = EnhancedReactLazy(() => import('pages/insights-studio/dashboard-sharing/PasswordPage'));
const DashboardExpired = EnhancedReactLazy(() => import('pages/insights-studio/dashboard-sharing/DashboardExpired'));

const MainApp = () => {
  return (
    <React.Suspense fallback={<RouteSpin />}>
      <Router className="main-app-route-container">
        <LandingPage path="/" />
        <Home path="/*" />
        <Login path="/login" />
        <SwitchInstance path="/switch-instance/:instanceId" />
        {/* This will be rendered if the user pasted the url on the browser */}
        <OAuth path="/oauth/*" />
        <OAuth path="/arcade/oauth/*" />
        <OAuth path="/arcade/api/v1/oauth/authorize/*" />
        <OAuth path="/arcade/api/v1/oauth2/authorize/*" />
        <MCPConsent path="/arcade/api/v1/oauth2/consent/*" />
        <ExternalAction path="/action/*" />
        <ForgotPassword path="/authentication/forgotpassword" />
        <PasswordResetSuccess path="/authentication/passwordresetsuccess" />
        <ResetPassword path={RouteConstants.PASSWORD_RESET} />
        <UpdatePassword path="/invited-user/setpassword/:invitationId" />
        <ErrorNotificationValidateEmail path="/error-notifications/validate-email/:encInstanceId/:invitationId/:status" />
        <SharedDashboard path="/insightssharing/user/:email/dashboard/:dashboardId" />
        <PasswordPage path="/insightssharing/enter-password/user/:email/dashboard/:dashboardId" />
        <DashboardExpired path="/insightssharing/expired-dashboard/user/:email/dashboard/:dashboardId" />
        <Errors path="/errors/*" />
        <Error404 default />
      </Router>
    </React.Suspense>
  );
};

export default MainApp;
