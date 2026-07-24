import { useLocation } from '@reach/router';
import Icon from 'antd/lib/icon';
import notification from 'antd/lib/notification';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Workbox } from 'workbox-window';

import useInterval from 'hooks/useInterval';
import { tNamespaced } from 'utils/i18nUtil';

import Button from './Button';

const tn = tNamespaced('ServiceWorkerNotification');

// we'll poll on this interval, but check on each navigation so it should be fine for this to be very long
const SERVICE_WORKER_UPDATE_POLL_INTERVAL = 1000 * 60 * 60 * 3; // 3 hours
const SERVICE_WORKER_NOTIFICATION_KEY = 'Syncari.ServiceWorker.UpdateReady';

// don't check more than once every 2 minutes
const UPDATE_CHECK_THROTTLE_MS = 1000 * 60 * 2;

// global var used to track the timestamp of the last time we checked for an update
let lastUpdateCheck = 0;

// Show/hide the prompt if the old service worker is loaded and a new one needs to be activated + loaded
let promptNewUpdate = false;

const ServiceWorkerBanner = () => {
  const [hasNewVersion, setHasNewVersion] = useState(false);
  const worker = useRef<Workbox>();
  const location = useLocation();

  const checkForUpdate = () => {
    const now = Date.now();

    if (now - lastUpdateCheck <= UPDATE_CHECK_THROTTLE_MS) {
      return;
    }

    if (lastUpdateCheck) {
      // We're piggy backing on the throttled update check
      // to give sometime for new resources to get loaded.
      promptNewUpdate = true;
    }

    lastUpdateCheck = now;

    // have the service worker check for updates. If an update is found, it will
    // download, install and move into waiting status - which will pop our
    // notification to the user.
    if (worker.current) {
      try {
        worker.current.update();
      } catch (err) {
        console.log('Worker update fn failed', err);
      }
    }
  };

  useInterval(checkForUpdate, SERVICE_WORKER_UPDATE_POLL_INTERVAL);

  // postMessage to new worker to skip waiting phase and take over control
  const activateNewSw = useCallback(() => {
    if (worker.current) {
      worker.current.messageSkipWaiting();
      window.location.reload();
    }
  }, []);

  // when the location changes, run our update check
  useEffect(() => {
    location?.pathname && checkForUpdate();
  }, [location?.pathname]);

  // manages event handlers for our service-worker
  // provides handle via `worker` ref
  useEffect(() => {
    if (!('serviceWorker' in navigator)) {
      return;
    }

    const wb = new Workbox('/service-worker.js');

    const onNewVersionReady = () => {
      // Only prompt when the new version is installed in the background while the app is fully loaded.
      // This means the loaded assets were old and need to refresh the page
      if (promptNewUpdate) {
        setHasNewVersion(true);
      } else if (!promptNewUpdate && worker.current) {
        // Keep using what was fetched from the network and activate the new
        // service worker right away since its the same set of assets.
        worker.current.messageSkipWaiting();
      }
    };
    const onNewVersionControlling = () => setHasNewVersion(false);

    // If our new service worker takes over control without refresh, we'll clear the notification
    wb.addEventListener('controlling', onNewVersionControlling);

    // Add an event listener to detect when the registered
    // service worker has installed but is waiting to activate.
    wb.addEventListener('waiting', onNewVersionReady);
    wb.register();

    // update our ref so we have a handle for activation
    worker.current = wb;

    return () => {
      wb.removeEventListener('controlling', onNewVersionControlling);
      wb.removeEventListener('waiting', onNewVersionReady);
    };
  }, []);

  // controls showing/hiding the update notification
  useEffect(() => {
    if (hasNewVersion) {
      notification.info({
        key: SERVICE_WORKER_NOTIFICATION_KEY,
        btn: (
          <Button type="primary" size="small" onClick={activateNewSw}>
            {tn('update_btn')}
          </Button>
        ),
        icon: <Icon type="cloud-sync" />,
        description: tn('new_version_description'),
        message: tn('new_version_title'),
        duration: 0,
      });
    } else {
      try {
        // close open notification
        notification.close(SERVICE_WORKER_NOTIFICATION_KEY);
      } catch (err) {
        // no-op. If there isn't a notification open with this key, it's OK
      }
    }
  }, [activateNewSw, hasNewVersion]);

  return null;
};

export default ServiceWorkerBanner;
