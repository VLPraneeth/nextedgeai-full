import { useLocation, useMatch } from '@reach/router';
import { AuthStatus, AuthType, init } from '@thoughtspot/visual-embed-sdk';
import { createContext, FC, useContext, useEffect, useMemo, useState } from 'react';

import { useEnhancedDispatch, useEnhancedSelector } from 'hooks/redux';
import { useGenerateTSTokenMutation } from 'store/insights-studio';
import { setThoughtspotToken } from 'store/user/actions';
import { selectThoughtspotToken, selectUserEmail } from 'store/user/selectors';
import AppConstants from 'utils/AppConstants';
import { tNamespaced } from 'utils/i18nUtil';

import { useInsightsThoughtspotEnabled } from '../utils/useInsightsThoughtspotEnabled';

const tn = tNamespaced('InsightsStudio.thoughtspot');

export type InsightsView = 'legacy' | 'thoughtspot';

export interface InsightsViewContextValue {
  insightsView: InsightsView;
  setInsightsView: React.Dispatch<React.SetStateAction<InsightsView>>;
  isThoughtSpotEnabled: boolean;
  isThoughtSpotView: boolean;
  isThoughtSpotLoading: boolean;
  setIsThoughtspotLoading: React.Dispatch<React.SetStateAction<boolean>>;
  isThoughtSpotInitialized: boolean;
}

const InsightsViewContext = createContext<InsightsViewContextValue>({
  insightsView: 'legacy',
  setInsightsView: () => {},
  isThoughtSpotEnabled: false,
  isThoughtSpotView: false,
  isThoughtSpotLoading: false,
  setIsThoughtspotLoading: () => {},
  isThoughtSpotInitialized: false,
});

export const useInsightsViewContext = () => useContext(InsightsViewContext);

export const InsightsViewContextProvider: FC = ({ children }) => {
  const isThoughtSpotEnabled = useInsightsThoughtspotEnabled();
  const [insightsView, setInsightsView] = useState<InsightsView>('legacy');
  const tsMatch = useMatch('/insights-studio/ts/*');
  const legacyDashboardMatch = useMatch('/insights-studio/:dashboardId/*');
  const [isThoughtSpotLoading, setIsThoughtspotLoading] = useState(false);
  const [isThoughtSpotInitialized, setIsThoughtSpotInitialized] = useState(false);
  const location = useLocation();
  useEffect(() => {
    if ((!isThoughtSpotEnabled || legacyDashboardMatch) && !tsMatch) {
      setInsightsView('legacy');
    } else {
      setInsightsView('thoughtspot');
    }
  }, [isThoughtSpotEnabled, legacyDashboardMatch, tsMatch]);

  const email = useEnhancedSelector(selectUserEmail);
  const thoughtspotToken = useEnhancedSelector(selectThoughtspotToken);
  const dispatch = useEnhancedDispatch();
  const [generateTSToken] = useGenerateTSTokenMutation();

  useEffect(() => {
    if (
      !email ||
      !isThoughtSpotEnabled ||
      thoughtspotToken ||
      !location ||
      !location.pathname.startsWith('/insights-studio/ts') ||
      isThoughtSpotInitialized
    ) {
      return;
    }
    setIsThoughtspotLoading(true);
    init({
      thoughtSpotHost: AppConstants.THOUGHTSPOT_URL,
      authType: AuthType.TrustedAuthTokenCookieless,
      getAuthToken: (async () => {
        try {
          const token = await generateTSToken().unwrap();
          dispatch(setThoughtspotToken(token));
          return new Promise((resolve) => resolve(token));
        } catch (err) {
          dispatch(setThoughtspotToken(''));
          console.log('error', err);
        }
      }) as any,
      autoLogin: true,
      queueMultiRenders: true,
      loginFailedMessage: tn('access_error'),
      callPrefetch: true,
      customizations: {
        content: {
          strings: {
            Liveboard: tn('dashboard'),
            'Show underlying data': tn('show_source_data'),
            Worksheets: tn('datasets'),
            Answers: tn('datacards'),
            Answer: tn('datacard'),
            answer: tn('datacard').toLowerCase(),
            answers: tn('datacards').toLowerCase(),
            '{answerName}': '{answerName}',
            'Ask Sage': tn('ask_syncaroo'),
            'You can add filters only when the data sources are models. Your data sources for this Dashboard are views or tables.': tn(
              'filter_tooltip'
            ),
            'No parameters created yet': tn('parameter_tooltip'),
            'Enter a user name, email or group name': "Enter recipient's email address",
          },
        },
        style: {
          customCSSUrl:
            process.env.REACT_APP_THOUGHTSPOT_CSS_URL || `${window.parent.location.origin}/thoughtspot.css`,
        },
      },
      username: email,
    })?.on(AuthStatus.SDK_SUCCESS, () => {
        setIsThoughtspotLoading(false);
        setIsThoughtSpotInitialized(true);
      })
      .on(AuthStatus.FAILURE, (sessionInfo: any) => {
        setIsThoughtspotLoading(false);
      });
  }, [email, generateTSToken, location, dispatch, isThoughtSpotEnabled, thoughtspotToken, isThoughtSpotInitialized]);

  const value = useMemo(() => {
    return {
      insightsView,
      isThoughtSpotView: insightsView === 'thoughtspot',
      setInsightsView,
      isThoughtSpotEnabled,
      isThoughtSpotLoading,
      setIsThoughtspotLoading,
      isThoughtSpotInitialized,
    };
  }, [insightsView, isThoughtSpotEnabled, isThoughtSpotLoading, isThoughtSpotInitialized]);

  return <InsightsViewContext.Provider value={value}>{children}</InsightsViewContext.Provider>;
};
