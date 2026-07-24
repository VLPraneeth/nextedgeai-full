import ReactDOM from 'react-dom';

/**
 * Creates a portal using a stable id so that the container is only mounted 1 time.
 *
 */
export function createPortal(children: React.ReactNode, portalContainerId: string) {
  let container = document.getElementById(portalContainerId);

  if (!container) {
    container = document.createElement('div');
    container.id = portalContainerId;

    document.body.appendChild(container);
  }

  return ReactDOM.createPortal(children, container);
}
