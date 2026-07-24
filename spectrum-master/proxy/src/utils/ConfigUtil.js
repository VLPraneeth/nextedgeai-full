//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import process from 'process';

import { camelCase } from 'lodash';

const { env } = process;

// Known configurations
const KNOWN_CONFIGS = [
  'ARTIFACTS_ROOT',
  'GCP_CREDENTIALS_KEY',
  'GCP_PROJECT',
  'GCS_BUCKET_NAME',
  'GCS_PATH_PREFIX',
  'GENERIC_TOPIC_NAME',
  'VIPER_TOPIC_NAME',
  'TRACK_RESPONSE_TIME',
];

// Secret configuration. This will not be printed in the logs
export const SECRET_CONFIGS = ['GCP_CREDENTIALS_KEY'];

/**
 * Get the config variables of the spectrum gateway
 * @returns {Object} config object with variable values
 */
export function getConfigVariables() {
  const {
    // Config variables with defaults
    ARCADE_LOG_LEVEL = 'info',
    SPECTRUM_PORT = '8088',
    SECURE_COOKIES = 'true',
    USE_MOCK = 'false',
    MOCK_TARGET = 'http://localhost:3001',
    ARCADE_TARGET = 'http://localhost:8080',
    ARTIFACTS_ROOT = '../build',
    ...envs
  } = env;

  // Extract the known environment variables available and make it available.
  const knownConfigs = KNOWN_CONFIGS.reduce((acc, knownKey) => {
    if (knownKey in envs) {
      return {
        ...acc,
        [camelCase(knownKey)]: envs[knownKey],
      };
    }

    return acc;
  }, {});

  return {
    ...knownConfigs,
    arcadeTarget: ARCADE_TARGET,
    spectrumPort: Number(SPECTRUM_PORT),
    arcadeLogLevel: ARCADE_LOG_LEVEL,
    secureCookies: SECURE_COOKIES === 'true',

    // Mock configuration
    useMock: USE_MOCK === 'true',
  };
}
