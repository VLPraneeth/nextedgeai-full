import { navigate } from '@reach/router';
import { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';

import RouteConstants from 'utils/RouteConstants';
import { START_PRODUCT_TOUR_EVENT } from 'utils/GuidedDemo';

import './GuidedProductTour.less';

type GuidedProductTourProps = {
  autoStart?: boolean;
};

type TourStep = {
  title: string;
  body: string;
  target?: string;
};

const TOUR_STEPS: TourStep[] = [
  {
    title: 'Welcome to NextEdge AI',
    body: 'This guided workspace contains only the currently approved demo features. It is read-only, so you can explore safely.',
  },
  {
    title: 'Start with the workspace',
    body: 'Use this overview to see the V1 flow, system status, imported data, and AI-assisted mapping in one place.',
    target: '[data-tour-target="workspace"]',
  },
  {
    title: 'Explore five working data connections',
    body: 'Synapses contains Amazon S3, PostgreSQL, MySQL, and MongoDB. File/CSV ingestion is available separately under Imported Files.',
    target: '[data-tour-target="synapses"]',
  },
  {
    title: 'Understand and map your data',
    body: 'Schema Studio describes connected data. Sync Studio defines how records move, while Data Studio lets you inspect the result.',
    target: '[data-tour-target="schema"]',
  },
  {
    title: 'Monitor every run',
    body: 'Logs is the operational view for transactions, sync activity, and errors. You can restart this guide from the header at any time.',
    target: '[data-tour-target="logs"]',
  },
];

export const GuidedProductTour = ({ autoStart = false }: GuidedProductTourProps) => {
  const [isOpen, setOpen] = useState(false);
  const [stepIndex, setStepIndex] = useState(0);
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);
  const dialogRef = useRef<HTMLDivElement>(null);

  const openTour = useCallback(() => {
    setStepIndex(0);
    setOpen(true);
  }, []);

  const startTour = useCallback(() => {
    openTour();
    if (window.location.pathname !== RouteConstants.V1_WORKSPACE) {
      navigate(RouteConstants.V1_WORKSPACE);
    }
  }, [openTour]);

  useEffect(() => {
    window.addEventListener(START_PRODUCT_TOUR_EVENT, startTour);
    return () => window.removeEventListener(START_PRODUCT_TOUR_EVENT, startTour);
  }, [startTour]);

  useEffect(() => {
    if (autoStart) {
      openTour();
    }
  }, [autoStart, openTour]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const updateTarget = () => {
      const selector = TOUR_STEPS[stepIndex].target;
      const target = selector ? document.querySelector(selector) : null;
      setTargetRect(target?.getBoundingClientRect() || null);
    };

    const timer = window.setTimeout(updateTarget, 80);
    window.addEventListener('resize', updateTarget);
    window.addEventListener('scroll', updateTarget, true);
    dialogRef.current?.focus();

    return () => {
      window.clearTimeout(timer);
      window.removeEventListener('resize', updateTarget);
      window.removeEventListener('scroll', updateTarget, true);
    };
  }, [isOpen, stepIndex]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  const keepFocusInDialog = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'Tab' || !dialogRef.current) {
      return;
    }
    const controls = Array.from(
      dialogRef.current.querySelectorAll<HTMLElement>('button:not(:disabled), [href], [tabindex]:not([tabindex="-1"])')
    );
    const firstControl = controls[0];
    const lastControl = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === firstControl) {
      event.preventDefault();
      lastControl?.focus();
    } else if (!event.shiftKey && document.activeElement === lastControl) {
      event.preventDefault();
      firstControl?.focus();
    }
  };

  const step = TOUR_STEPS[stepIndex];
  const isLastStep = stepIndex === TOUR_STEPS.length - 1;

  return createPortal(
    <div className="guided-tour" aria-live="polite">
      <div className="guided-tour__veil" />
      {targetRect && (
        <div
          className="guided-tour__spotlight"
          style={{
            top: Math.max(8, targetRect.top - 6),
            left: Math.max(8, targetRect.left - 6),
            width: targetRect.width + 12,
            height: targetRect.height + 12,
          }}
        />
      )}
      <div
        className="guided-tour__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="guided-tour-title"
        tabIndex={-1}
        ref={dialogRef}
        onKeyDown={keepFocusInDialog}
      >
        <div className="guided-tour__progress">
          <span>Product tour</span>
          <span>
            {stepIndex + 1} / {TOUR_STEPS.length}
          </span>
        </div>
        <div className="guided-tour__bar" aria-hidden="true">
          <span style={{ width: `${((stepIndex + 1) / TOUR_STEPS.length) * 100}%` }} />
        </div>
        <h2 id="guided-tour-title">{step.title}</h2>
        <p>{step.body}</p>
        <div className="guided-tour__actions">
          <button type="button" className="guided-tour__skip" onClick={() => setOpen(false)}>
            Skip tour
          </button>
          <div>
            <button
              type="button"
              className="guided-tour__back"
              disabled={stepIndex === 0}
              onClick={() => setStepIndex((current) => Math.max(0, current - 1))}
            >
              Back
            </button>
            <button
              type="button"
              className="guided-tour__next"
              onClick={() => (isLastStep ? setOpen(false) : setStepIndex((current) => current + 1))}
            >
              {isLastStep ? 'Finish' : 'Next'}
            </button>
          </div>
        </div>
        <p className="guided-tour__escape">Press Esc to close</p>
      </div>
    </div>,
    document.body
  );
};

export default GuidedProductTour;
