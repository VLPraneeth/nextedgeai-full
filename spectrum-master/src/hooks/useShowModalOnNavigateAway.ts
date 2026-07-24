import { globalHistory, HistoryListenerParameter, navigate, useLocation } from '@reach/router';
import { useEffect, useRef } from 'react';

import { useNavigateConfirmationModal } from 'components/NavigateConfirmationModal';

/**
 * Helper hook to show a confirmation modal when the user navigates away from the current route and based on the user's decision either stays on the current page or navigates away.
 * @param currentPath The current pathname of the page where this hook is being used.
 * @param hasChanged A boolean value indicating whether there are any unsaved changes on the page.
 * @param modalTitle The title to display on the confirmation modal.
 * @param modalMessage The message to display on the confirmation modal.
 */
export function useShowModalOnNavigateAway(
  currentPath: string | undefined,
  hasChanged: boolean,
  modalTitle: string,
  modalMessage: string
) {
  const navigationInProgressRef = useRef(false);
  const location = useLocation();
  const { openModal, setContent } = useNavigateConfirmationModal();

  useEffect(() => {
    const handleNavigation = async ({ action, location: newLocation }: HistoryListenerParameter) => {
      if (
        !navigationInProgressRef.current &&
        hasChanged &&
        ((action === 'PUSH' && currentPath && !newLocation.pathname.includes(currentPath)) || action === 'POP') // Check that the user is navigating away from the page
      ) {
        navigationInProgressRef.current = true;

        // Navigate back to the current page to prevent the user from leaving without creating extra history entry
        if (action === 'PUSH') {
          navigate(`${location.pathname}`);
          navigate(-1);
        } else {
          navigate(`${location.pathname}`);
        }

        setContent({
          message: modalMessage,
          title: modalTitle,
        });

        const confirmed = await openModal(true); // Open the confirmation modal and wait for the user's decision

        if (confirmed) {
          navigate(`${newLocation.pathname}`); // Navigate to the new location
        } else {
          navigationInProgressRef.current = false;
          navigate(`${location.pathname}`, { replace: true });
        }
      }
    };

    const unlisten = globalHistory.listen(handleNavigation);
    return () => unlisten();
  }, [hasChanged, modalMessage, modalTitle, currentPath, location.pathname, setContent, openModal]);

  useEffect(() => {
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      if (hasChanged) {
        event.preventDefault();
        return (event.returnValue = '');
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [hasChanged]);
}
