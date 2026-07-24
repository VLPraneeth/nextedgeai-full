//
// Copyright (c) 2019-Present Syncari - All rights reserved.
//
import { Server } from 'socket.io';

import axios from 'axios';
import cookie from 'cookie';
import { trim } from 'lodash';

import AppConstants from 'utils/AppConstants';
import { getConfigVariables } from 'utils/ConfigUtil';
import { logger } from 'utils/LogUtil';
import { base64Decode } from 'utils/StringUtil';

import { initializePubsub } from '../pubsub/Subscription';

const { arcadeTarget } = getConfigVariables();

const AUTH_ERROR = 'Failed authentication: ';
const AUTHORIZATION_KEY = AppConstants.AUTHORIZATION_KEY;
const AUTH_URL = '/api/v1/user';
const DISPATCH_MESSAGE = 'DISPATCH_MESSAGE';
const LEAVE_CHANNEL = 'LEAVE_CHANNEL';
const JOIN_CHANNEL = 'JOIN_CHANNEL';

let io;

/**
 * Middleware to authorize connection
 * @param {Socket} express Socket
 * @param {function} express middleware next
 */
const authorize = async (socket, next) => {
  const headerCookie = socket.request?.headers?.cookie;
  if (headerCookie?.toLowerCase().indexOf(`${AUTHORIZATION_KEY}=`) !== -1) {
    const cookies = cookie.parse(headerCookie || '');
    if (cookies?.[AUTHORIZATION_KEY]) {
      const token = base64Decode(cookies[AUTHORIZATION_KEY]);
      try {
        const response = await axios.request({
          url: `${arcadeTarget}${AUTH_URL}`,
          method: 'GET',
          headers: {
            Authorization: token,
          },
        });
        logger.info(`User ${response?.data?.email} authenticated for push update.`);
        next();
      } catch (error) {
        logger.info(`${AUTH_ERROR} ${error.message}`);
        next(new Error(AUTH_ERROR));
      }
    } else {
      logger.info(`${AUTH_ERROR} no authorization cookie.`);
      next(new Error(AUTH_ERROR));
    }
  } else {
    logger.info(`${AUTH_ERROR} no cookie. ${AUTHORIZATION_KEY} ${headerCookie}`);
    next(new Error(AUTH_ERROR));
  }
};

/**
 * Initialize our message stream
 * @param {HttpServer} httpServer express http sever
 */
export const initMessageStream = (httpServer) => {
  io = new Server(httpServer, {
    path: '/messageStream',
    serveClient: false,
    pingInterval: 15000, // Socket.io v4: use pingInterval for heartbeat interval
    pingTimeout: 29000, // Socket.io v4: use pingTimeout for heartbeat timeout
    cors: {
      origin: true,
      credentials: true,
    },
  });

  initializePubsub(sendMessage);

  io.use(authorize);
  logger.debug('Message stream initialized.');

  io.on('connection', function (socket) {
    logger.debug('Connection initiated.');

    socket.on(JOIN_CHANNEL, function (joinParams) {
      const { channelId, userName } = JSON.parse(joinParams);
      logger.info(`Username: ${userName} joining channel: ${channelId}`);
      socket.userName = userName;
      socket.join(trim(channelId)?.toLowerCase());
    });

    socket.on(LEAVE_CHANNEL, function (channel) {
      channel = channel.toLowerCase();
      logger.info(`Username: ${socket.userName} leaving channel: ${channel}`);
      socket.join(trim(channel));
    });

    socket.on('disconnect', function () {
      logger.info(`${socket.userName} disconnected.`);
    });
  });
};

/**
 * Checks if the channel as any members
 * @param {String} channel channel name
 */
const channelHasMembers = (channel) => {
  // Socket.io v4: rooms is now a Map, not an object
  const room = io.sockets.adapter.rooms.get(channel);
  return room && room.size > 0;
};

/**
 * Send the message to the channel
 * @param {Object} msgJson object with channelId to send the message to
 */
const sendMessage = (msgJson) => {
  if (msgJson?.channelId) {
    const channelId = trim(msgJson.channelId).toLowerCase();
    if (channelHasMembers(channelId)) {
      const msg = JSON.stringify(msgJson);
      // Socket.io v4: .to() is the preferred method (io.in() still works but deprecated)
      io.to(channelId).emit(DISPATCH_MESSAGE, msg);
      logger.debug(`Message sent in channel ${channelId}: ${msg}`);
    } else {
      let msg = `Message not sent. Channel ${channelId} is empty.`;
      logger.debug(msg);
    }
  } else {
    logger.error(`Invalid message, skipping send. ${msgJson}`);
  }
};
