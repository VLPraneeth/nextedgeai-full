import { Link, RouteComponentProps } from '@reach/router';

import './LandingPage.less';

const AUTH_ROUTE = '/login';

const ArrowIcon = () => (
  <svg aria-hidden="true" viewBox="0 0 20 20" focusable="false">
    <path
      d="M4 10h11M11 6l4 4-4 4"
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.8"
    />
  </svg>
);

const BrandMark = () => (
  <svg className="landing-brand__mark" aria-hidden="true" viewBox="0 0 64 64" focusable="false">
    <path d="M14 3H50L61 14V50L50 61H14L3 50V14L14 3Z" fill="currentColor" />
    <path d="M47 3H50L61 14V25L39 3H47Z" fill="#C69B48" />
    <path d="M15 47V17H23L41 37V17H49V47H41L23 27V47H15Z" fill="#F7F4EC" />
  </svg>
);

const CapabilityIcon = ({ type }: { type: 'connect' | 'govern' | 'automate' }) => {
  if (type === 'connect') {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
        <circle cx="6" cy="6" r="3" />
        <circle cx="18" cy="6" r="3" />
        <circle cx="12" cy="18" r="3" />
        <path d="m8.5 7.8 2.3 6.8M15.5 7.8l-2.3 6.8M9 6h6" />
      </svg>
    );
  }

  if (type === 'govern') {
    return (
      <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
        <path d="M12 3 5 6v5c0 4.6 2.8 8.2 7 10 4.2-1.8 7-5.4 7-10V6l-7-3Z" />
        <path d="m9 12 2 2 4-5" />
      </svg>
    );
  }

  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
      <path d="M13 2 5 14h6l-1 8 8-12h-6l1-8Z" />
    </svg>
  );
};

const LandingPage = (_props: RouteComponentProps) => {
  return (
    <div className="landing-page">
      <a className="landing-skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="landing-header">
        <nav className="landing-nav" aria-label="Primary navigation">
          <a className="landing-brand" href="/" aria-label="NextEdge AI home">
            <BrandMark />
            <span>
              NextEdge <strong>AI</strong>
            </span>
          </a>
          <div className="landing-nav__links">
            <a href="#capabilities">Capabilities</a>
            <a href="#how-it-works">How it works</a>
          </div>
          <div className="landing-nav__actions">
            <Link className="landing-link-button landing-link-button--quiet" to={AUTH_ROUTE}>
              Log in
            </Link>
            <Link className="landing-link-button landing-link-button--primary" to={`${AUTH_ROUTE}?intent=signup`}>
              Sign up
              <ArrowIcon />
            </Link>
          </div>
        </nav>
      </header>

      <main id="main-content">
        <section className="landing-hero" aria-labelledby="landing-title">
          <div className="landing-hero__copy">
            <p className="landing-eyebrow">
              <span aria-hidden="true" /> Intelligent data operations
            </p>
            <h1 id="landing-title">Make every system work as one.</h1>
            <p className="landing-hero__lede">
              NextEdge AI connects your business data, creates one governed model, and automates the work between every
              system.
            </p>
            <div className="landing-hero__actions">
              <Link
                className="landing-link-button landing-link-button--primary landing-link-button--large"
                to={AUTH_ROUTE}
              >
                Enter NextEdge
                <ArrowIcon />
              </Link>
              <a
                className="landing-link-button landing-link-button--outline landing-link-button--large"
                href="#capabilities"
              >
                Explore capabilities
              </a>
            </div>
            <dl className="landing-proof" aria-label="Platform highlights">
              <div>
                <dt>Multi-tenant</dt>
                <dd>Built for isolated workspaces</dd>
              </div>
              <div>
                <dt>AWS-hosted</dt>
                <dd>Deployed in your cloud region</dd>
              </div>
              <div>
                <dt>Policy-driven</dt>
                <dd>Governed by design</dd>
              </div>
            </dl>
          </div>

          <div className="landing-product" aria-label="NextEdge AI product preview">
            <div className="landing-product__frame">
              <div className="landing-product__toolbar">
                <span className="landing-product__wordmark">
                  <BrandMark /> NextEdge AI
                </span>
                <span className="landing-product__status">
                  <i aria-hidden="true" /> Workspace healthy
                </span>
              </div>
              <div className="landing-product__body">
                <aside className="landing-product__rail" aria-label="Connected sources">
                  <span>Sources</span>
                  <b>CRM</b>
                  <b>Warehouse</b>
                  <b>Product</b>
                  <b>Files</b>
                </aside>
                <div className="landing-product__canvas">
                  <div className="landing-product__canvas-head">
                    <div>
                      <span>Unified data model</span>
                      <strong>Customer 360</strong>
                    </div>
                    <span className="landing-product__published">Published</span>
                  </div>
                  <div className="landing-flow" aria-hidden="true">
                    <div className="landing-flow__node">
                      <span>01</span>
                      <b>Capture</b>
                      <small>4 systems</small>
                    </div>
                    <i />
                    <div className="landing-flow__node landing-flow__node--core">
                      <span>02</span>
                      <b>Unify</b>
                      <small>Rules applied</small>
                    </div>
                    <i />
                    <div className="landing-flow__node">
                      <span>03</span>
                      <b>Activate</b>
                      <small>3 destinations</small>
                    </div>
                  </div>
                  <div className="landing-product__metrics">
                    <div>
                      <span>Records governed</span>
                      <strong>1.24M</strong>
                      <small>Across every source</small>
                    </div>
                    <div>
                      <span>Active automations</span>
                      <strong>18</strong>
                      <small>All systems healthy</small>
                    </div>
                    <div>
                      <span>Data confidence</span>
                      <strong>98.7%</strong>
                      <small>Policy checks passed</small>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <p className="landing-product__caption">
              <span>Live workspace</span> One place to connect, govern, and activate data.
            </p>
          </div>
        </section>

        <section
          className="landing-section landing-capabilities"
          id="capabilities"
          aria-labelledby="capabilities-title"
        >
          <div className="landing-section__intro">
            <p className="landing-kicker">One intelligent layer</p>
            <h2 id="capabilities-title">From scattered systems to trusted operations.</h2>
            <p>Move from connection to action without stitching together another fragile stack.</p>
          </div>
          <div className="landing-capability-grid">
            <article>
              <span className="landing-capability__icon">
                <CapabilityIcon type="connect" />
              </span>
              <p>01 / Connect</p>
              <h3>Bring every source into view.</h3>
              <span>Connect operational systems and files through one consistent workspace.</span>
            </article>
            <article>
              <span className="landing-capability__icon">
                <CapabilityIcon type="govern" />
              </span>
              <p>02 / Govern</p>
              <h3>Create one trusted data model.</h3>
              <span>Map, validate, and control how data moves with policies your team can inspect.</span>
            </article>
            <article>
              <span className="landing-capability__icon">
                <CapabilityIcon type="automate" />
              </span>
              <p>03 / Automate</p>
              <h3>Turn clean data into action.</h3>
              <span>Run reliable workflows and use AI assistance where it removes real operational work.</span>
            </article>
          </div>
        </section>

        <section className="landing-section landing-process" id="how-it-works" aria-labelledby="process-title">
          <div className="landing-process__copy">
            <p className="landing-kicker">A simpler operating model</p>
            <h2 id="process-title">Connect once. Govern centrally. Move with confidence.</h2>
          </div>
          <ol className="landing-process__steps">
            <li>
              <span>01</span>
              <div>
                <h3>Choose your systems</h3>
                <p>Start with the sources your team already depends on.</p>
              </div>
            </li>
            <li>
              <span>02</span>
              <div>
                <h3>Define the trusted model</h3>
                <p>Map identities, rules, and ownership in one visual layer.</p>
              </div>
            </li>
            <li>
              <span>03</span>
              <div>
                <h3>Activate the workflow</h3>
                <p>Publish governed data and monitor every operation end to end.</p>
              </div>
            </li>
          </ol>
        </section>

        <section className="landing-cta" aria-labelledby="cta-title">
          <p className="landing-kicker">Your data has a next move</p>
          <h2 id="cta-title">Make it a confident one.</h2>
          <p>Enter the NextEdge AI workspace and turn connected data into governed action.</p>
          <div className="landing-cta__actions">
            <Link className="landing-link-button landing-link-button--gold landing-link-button--large" to={AUTH_ROUTE}>
              Log in to NextEdge
              <ArrowIcon />
            </Link>
            <Link
              className="landing-link-button landing-link-button--light landing-link-button--large"
              to={`${AUTH_ROUTE}?intent=signup`}
            >
              Sign up
            </Link>
          </div>
        </section>
      </main>

      <footer className="landing-footer">
        <a className="landing-brand" href="/" aria-label="NextEdge AI home">
          <BrandMark />
          <span>
            NextEdge <strong>AI</strong>
          </span>
        </a>
        <p>Intelligent data operations, built for control.</p>
        <Link to={AUTH_ROUTE}>Secure login</Link>
      </footer>
    </div>
  );
};

export default LandingPage;
