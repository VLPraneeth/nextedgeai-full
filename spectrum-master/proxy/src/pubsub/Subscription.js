//
// Copyright (c) 2019-Present Syncari - All rights reserved.
// Pub/Sub subscription
//
import { execSync } from 'child_process';
import fs, { existsSync } from 'fs';
import process from 'process';

import { each, isFunction, remove, snakeCase } from 'lodash';
import shortid from 'shortid';

import { PubSub } from '@google-cloud/pubsub';

import { getConfigVariables } from 'utils/ConfigUtil';
import { logger } from 'utils/LogUtil';
import { base64Decode } from 'utils/StringUtil';

const { gcpProject, genericTopicName, viperTopicName, gcpCredentialsKey } = getConfigVariables();

// Viper events that we are relaying to the UI
export const VIPER_EVENT_TYPES = [
  'SYNC_SUCCESS',
  'PIPELINE_EVENT',
  'TEST_PIPELINE_DONE',
  'SIMULATE_PIPELINE_COMPLETED',
];

// Generic events that we are relaying to the UI
export const GENERIC_EVENT_TYPES = [
  'CONNECTOR_ACTIVATED',
  'CONNECTOR_ACTIVATION_FAILED',
  'REFRESH_SCHEMA',
  'REFRESH_SCHEMA_COMPLETED',
  'REFRESH_SCHEMA_FAILED',
  'DFI_RECALCULATION_UPDATE',
  'RESYNC_ENTITY_STATUS_UPDATE',
  'EXECUTE_QUICK_START_DONE',
  'INSTALL_QUICK_START_SUCCESS',
];

// Name of the subscription that gets created
let subscriptionNames = [];
let pubsubClient;
const PUBSUB_KEY_FILENAME = '.psk';

/**
 * Initialize the google pubsub. Created subscription in viper and generic topics.
 * @param {Function} sendMessage function with messages from the subscription
 */
export const initializePubsub = (sendMessage) => {
  const pubsubReq = {
    gcpCredentialsKey,
    gcpProject,
    viperTopicName,
    genericTopicName,
  };
  const emptyVals = [];
  each(pubsubReq, (val, key) => {
    if (!val) {
      emptyVals.push(snakeCase(key).toUpperCase());
    }
  });
  if (Object.keys(emptyVals)?.length > 0) {
    logger.error(
      `${JSON.stringify(emptyVals)} environment variable/s not found. Running proxy without events from the backend.`
    );
    return;
  }

  // Use pubsub for multiple server synchronization
  fs.writeFileSync(PUBSUB_KEY_FILENAME, base64Decode(gcpCredentialsKey));
  pubsubClient = new PubSub({
    projectId: gcpProject,
    keyFilename: PUBSUB_KEY_FILENAME,
  });

  subscribe(viperTopicName, VIPER_EVENT_TYPES, sendMessage);
  subscribe(genericTopicName, GENERIC_EVENT_TYPES, sendMessage);

  setupExitEvents();
};

/**
 * Transform subscription message to a message that can be dispatched
 * to the UI.
 * @param {Buffer} msg buffer from a subscription message
 */
const transformDispatchMessage = (message) => {
  const msgString = message?.data?.toString();
  if (msgString) {
    const msg = JSON.parse(msgString);
    if (msg?.syncariId && msg?.event) {
      const { event } = msg;
      return {
        payload: msg?.event?.details,
        channelId: msg.syncariId,
        type: event?.type,
      };
    }
  }
};

/**
 * Message handler for the gcp pubsub
 * @param {Array} eventTypes array of event types it will relay
 * @param {function} sendMessage callback function to send the message to the ui
 * @param {String} message payload string
 */
const messageHandler = (eventTypes, sendMessage, message) => {
  logger.debug(`Received message: ${message.id}. Data: ${message.data}`);
  const msg = transformDispatchMessage(message);
  if (eventTypes?.includes(msg?.type)) {
    if (msg && isFunction(sendMessage)) {
      // Send the message to the ui
      sendMessage(msg);
    } else {
      logger.error(`Unexpected message format: ${msg}`);
    }
  }
  message.ack();
};

/**
 * Create a subscription in the topic and listen.
 * @param {String} topicName PubSub topic name
 * @param {Array} eventTypes array of event types that we will relaying to the ui
 * @param {Function} sendMessage callback function for sending the payload message
 */
export const subscribe = async (topicName, eventTypes, sendMessage) => {
  const subscriptionName = `spectrum-${topicName}-${shortid.generate()}`;

  logger.debug(`Creating subscription ${subscriptionName} in topic ${topicName}`);
  const topic = pubsubClient.topic(topicName);

  // Create subscription with proper options for v5+
  const [subscription] = await topic.createSubscription(subscriptionName);

  subscriptionNames.push(subscriptionName);

  // Listen for new messages until timeout is hit
  subscription.on('message', messageHandler.bind(this, eventTypes, sendMessage));
};

/**
 * Cleans up the subscription that were created when the proxy boots up
 */
const cleanupSubscription = async (shutdown) => {
  if (subscriptionNames?.length <= 0) {
    return;
  }
  logger.debug(`Cleaning up subscriptions: ${JSON.stringify(subscriptionNames)}`);
  const remainingSubscriptions = [...subscriptionNames];
  subscriptionNames = [];
  [...remainingSubscriptions].forEach(async (subscriptionName) => {
    // Odd that even with await on delete, it returned right away and didn't get deleted properly.
    // The request didn't either went out or got cancelled on process exit
    pubsubClient
      .subscription(subscriptionName)
      .delete()
      .then(() => {
        logger.debug(`Subscription ${subscriptionName} deleted.`);
        remove(remainingSubscriptions, (n) => n === subscriptionName);
        remainingSubscriptions.length <= 0 && shutdown && shutdown();
      })
      .catch((e) => {
        logger.error(`Error deleting subscription: ${e}`);
        shutdown && shutdown();
      });
  });
};
/**
 * Cleanup to do a graceful exit
 * @param {String} signal process signal
 */
const gracefulExit = (signal) => {
  logger.debug(`Singal ${signal} received. Cleaning up...`);
  cleanupSubscription(() => {
    logger.debug(`Proxy ${process.pid} terminating.`);
    process.exit(0);
  });
};

/**
 * Listen to the exit events that could be potentially receive and delete
 * the subscriptions that were created
 */
const setupExitEvents = async () => {
  // This should not happen since we are in the express event loop
  // but just in case it exits, we cleanup properly.
  process.on('exit', async (code) => {
    await cleanupSubscription();
    await new Promise((r) => setTimeout(r, 4000));
    logger.debug(`Process exit event with code: ${code}`);
  });

  // Listen to different signals that we are expecting
  // ctrl-c from terminal
  process.on('SIGINT', gracefulExit);

  // kill/shutdown/restart
  process.on('SIGTERM', gracefulExit);

  // nodemon restart on dev mode
  process.on('SIGUSR2', gracefulExit);

  if (process.env.NODE_ENV !== 'production') {
    const signalReload = (errorType) => {
      const indexFile = './src/index.js';
      const signalIndex = `touch ${indexFile}`;
      logger.error(`${errorType}, signaling app to reload...`);
      if (!existsSync(indexFile)) {
        logger.error(`File not found: ${indexFile}`);
      } else {
        execSync(signalIndex);
      }
    };

    process.on('uncaughtException', (err, origin) => {
      logger.error(`Caught exception: ${err}`);
      logger.error(`Exception origin: ${origin}`);
      signalReload('uncaughtException');
    });

    process.on('unhandledRejection', (reason, promise) => {
      logger.error(`Unhandled Rejection at ${promise}, reason: ${reason}`);
      signalReload('unhandledRejection');
    });
  }
};
