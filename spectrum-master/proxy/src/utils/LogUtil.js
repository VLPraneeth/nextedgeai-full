//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import process from 'process';

import { createLogger, format, transports } from 'winston';

import { getConfigVariables } from 'utils/ConfigUtil';

const { combine, label, printf } = format;

const spectrumFormat = printf(({ level, message, label }) => {
  return `${new Date().toUTCString()} - [${process.pid}] - [${label}] - ${level.toUpperCase()}: ${message}`;
});

const { arcadeLogLevel: level } = getConfigVariables();
export const logger = createLogger({
  level,
  format: combine(label({ label: 'spectrum-proxy' }), spectrumFormat),
  // We are just using the console for everything.
  // Logs will show in the container logs and managed by stack driver.
  transports: [new transports.Console()],
});

export const logProvider = () => {
  function log(level) {
    return (message) => {
      logger.log(level, message);
    };
  }
  const customProvider = {
    log: log('log'),
    debug: log('debug'),
    info: log('info'),
    warn: log('warn'),
    error: log('error'),
  };
  return customProvider;
};
