import { delay, trim, values } from 'lodash';
import { batch } from 'react-redux';
import { Middleware } from 'redux';
import io, { Socket } from 'socket.io-client';

import { getSyncStatuses } from 'actions/entityPipelineActions';
import { SyncariThunkDispatch } from 'hooks/redux';
import { resyncEntityStatusUpdateThunk } from 'store/entity-pipeline/actions';
import { getEntities } from 'store/entity/thunks';
import { RootState } from 'store/types';

import { dfiRecalculationUpdateThunk } from '../data-quality/actions';

export interface MessageStreamAction {
  channelId: string;
  userName: string;
}

// Action types
export const ActionTypes = {
  MESSAGE_STREAM_CONNECT: 'MESSAGE_STREAM/CONNECT',
  MESSAGE_STREAM_CONNECTED: 'MESSAGE_STREAM/CONNECTED',
  MESSAGE_STREAM_DISCONNECT: 'MESSAGE_STREAM/DISCONNECT',
  MESSAGE_STREAM_DISPATCH_MESSAGE: 'MESSAGE_STREAM/DISPATCH_MESSAGE',
  MESSAGE_STREAM_JOIN_CHANNEL: 'MESSAGE_STREAM/JOIN_CHANNEL',
  MESSAGE_STREAM_LEAVE_CHANNEL: 'MESSAGE_STREAM/LEAVE_CHANNEL',
};

// Supported transports
const TRANSPORTS = {
  WEBSOCKET: 'websocket',
  POLLING: 'polling',
};

// 3 seconds reconnect timeout (initial reconnect)
const RECONNECT_TIMEOUT = 3000;

// 10 seconds reconnect timeout retry
const RECONNECT_RETRY_TIMEOUT = 10000;

// Maximum retry count
const MAX_RETRY = 1000;
// Dispatch message is type of message we are accepting from the server
// This middleware will dispatch a redux message.
const DISPATCH_MESSAGE = 'DISPATCH_MESSAGE';
const JOIN_CHANNEL = 'JOIN_CHANNEL';
const LEAVE_CHANNEL = 'LAVE_CHANNEL';
let socket: Socket;

/**
 * Initialize our stream. This will join to the instance channels
 * awaiting for any messages that is coming from the server
 * @param {function} dispatch store dispatch function
 * @param {Object} action object with instace
 */
const initialize = (dispatch: SyncariThunkDispatch, action: MessageStreamAction) => {
  let { host, hostname, protocol } = window.location;
  if (process?.env?.NODE_ENV === 'development') {
    host = `${hostname}:8088`;
  }

  let isConnected = false;
  let retryCount = 0;
  const transports = values(TRANSPORTS);

  socket = io(`${protocol}//${host}`, {
    path: '/messageStream',
    transports,
    reconnection: true,
    reconnectionDelay: 1000,
    reconnectionDelayMax: 5000,
    timeout: 60000, // 1 min timeout
    reconnectionAttempts: Infinity,
  });

  // Join the channel when we are connected
  socket.on('connect', function () {
    dispatch({
      type: ActionTypes.MESSAGE_STREAM_CONNECTED,
    });
    isConnected = true;
    retryCount = 0;

    dispatch({
      type: ActionTypes.MESSAGE_STREAM_JOIN_CHANNEL,
      channelId: trim(action.channelId),
      userName: action.userName,
    });
  });

  // Disconnect right away when we got disconnected
  socket.on('disconnect', () => {
    isConnected = false;

    delay(() => {
      socket.connect();
      // Retry connecting if still disconnected.
      let reconnectInterval: number | null = window.setInterval(() => {
        if (isConnected && reconnectInterval) {
          clearInterval(reconnectInterval);
          reconnectInterval = null;
        } else {
          if (retryCount <= MAX_RETRY) {
            retryCount += 1;
            socket.connect();
          }
        }
      }, RECONNECT_RETRY_TIMEOUT);
    }, RECONNECT_TIMEOUT);
  });

  // When we receive a dispatch message from the server,
  // we forward that message as redux action message
  socket.on(DISPATCH_MESSAGE, (msg: string) => {
    dispatch({
      type: ActionTypes.MESSAGE_STREAM_DISPATCH_MESSAGE,
      payload: msg,
    });
  });
};

/**
 * Close our socket on close
 */
const close = () => {
  socket.close();
};

/**
 * Join a channel in the message stream
 * @param {Object} object with channelId and userName properties. Username is normally the email address
 */
const joinChannel = ({ channelId, userName }: MessageStreamAction) => {
  socket.emit(
    JOIN_CHANNEL,
    JSON.stringify({
      channelId: trim(channelId),
      userName,
    })
  );
};

/**
 * Leave a channel in the message stream
 * @param {Object} object with channelId and userName properties. Username is normally the email address
 */
const leaveChannel = ({ channelId }: MessageStreamAction) => {
  socket.emit(
    LEAVE_CHANNEL,
    JSON.stringify({
      channelId: trim(channelId),
    })
  );
};

// Actions created with @reduxjs/toolkit can be added to this map to be
// dispatched instead of a plain action
const sliceActionCreatorsMap: Record<string, any> = {
  DFI_RECALCULATION_UPDATE: dfiRecalculationUpdateThunk,
  RESYNC_ENTITY_STATUS_UPDATE: resyncEntityStatusUpdateThunk,
  // Support dispatch for named quickstarts
  EXECUTE_QUICK_START_DONE: [getEntities, getSyncStatuses],
};

const middleware: Middleware<{}, RootState, SyncariThunkDispatch> = (store) => (next) => (action) => {
  const dispatch = store.dispatch;
  switch (action.type) {
    // User request to connect
    case ActionTypes.MESSAGE_STREAM_CONNECT:
      // Configure the object
      initialize(dispatch, action);
      break;

    // User request to disconnect
    case ActionTypes.MESSAGE_STREAM_DISCONNECT:
      close();
      break;

    // Forward the message as redux action message
    case ActionTypes.MESSAGE_STREAM_DISPATCH_MESSAGE:
      const dispatchedAction: any = JSON.parse(action.payload);

      if (dispatchedAction) {
        const action = sliceActionCreatorsMap[dispatchedAction.type];
        if (Array.isArray(action)) {
          batch(() => {
            action.forEach((act) => {
              dispatch(act());
            });
          });
        } else {
          const mappedAction = action?.(dispatchedAction) ?? dispatchedAction;
          dispatch(mappedAction);
        }
      }
      break;

    case ActionTypes.MESSAGE_STREAM_JOIN_CHANNEL:
      joinChannel(action);
      break;

    case ActionTypes.MESSAGE_STREAM_LEAVE_CHANNEL:
      leaveChannel(action);
      break;

    // We don't really need the default for middleware, just to complete
    // the switch statement.
    default:
      break;
  }
  return next(action);
};

export default middleware;
