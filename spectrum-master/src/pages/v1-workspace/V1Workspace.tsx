import { Link, RouteComponentProps } from '@reach/router';
import { useEffect, useMemo, useState } from 'react';

import { useEnhancedSelector } from 'hooks/redux';
import { selectCurrentInstanceId } from 'store/user/selectors';
import { get, post } from 'utils/AjaxUtil';
import DataUrlConstants from 'utils/DataUrlConstants';
import RouteConstants from 'utils/RouteConstants';

import './V1Workspace.less';

type ReadinessSummary = {
  connections: number | null;
  datasets: number | null;
  runs: number | null;
  state: 'loading' | 'ready' | 'partial';
};

type MappingSuggestion = {
  source: string;
  target: string;
  confidence: number;
  reason: string;
};

const workflow = [
  {
    number: '01',
    eyebrow: 'Input',
    title: 'Bring in a CSV',
    description: 'Upload a customer file, validate its type, and preview the inferred columns before anything runs.',
    action: 'Open CSV import',
    path: RouteConstants.IMPORTED_FILES,
  },
  {
    number: '02',
    eyebrow: 'Model',
    title: 'Map the schema',
    description:
      'Match source fields to your governed customer model manually or start with a structured AI suggestion.',
    action: 'Try AI mapping',
    path: '#mapping-assistant',
  },
  {
    number: '03',
    eyebrow: 'Quality',
    title: 'Apply two clear rules',
    description: 'Start with required-field and duplicate-key checks, then review failures before publishing.',
    action: 'Open data quality',
    path: RouteConstants.DATA_QUALITY_STUDIO_ROOT,
  },
  {
    number: '04',
    eyebrow: 'Output',
    title: 'Deliver governed data',
    description: 'Use Amazon S3 for object delivery or PostgreSQL for structured downstream access.',
    action: 'Configure outputs',
    path: RouteConstants.SYNAPSES_CONNECTIONS,
  },
  {
    number: '05',
    eyebrow: 'Operate',
    title: 'Watch every run',
    description: 'See the run state, audit trail, actionable failure details, and retry path in one place.',
    action: 'View run history',
    path: RouteConstants.LOGS,
  },
];

const initialSummary: ReadinessSummary = {
  connections: null,
  datasets: null,
  runs: null,
  state: 'loading',
};

function splitFields(value: string) {
  return value
    .split(',')
    .map((field) => field.trim())
    .filter(Boolean);
}

function countResponse(result: PromiseSettledResult<any>) {
  if (result.status !== 'fulfilled') {
    return null;
  }
  return Array.isArray(result.value?.data) ? result.value.data.length : 0;
}

const V1Workspace = (_props: RouteComponentProps) => {
  const nextEdgeId = useEnhancedSelector(selectCurrentInstanceId);
  const [summary, setSummary] = useState<ReadinessSummary>(initialSummary);
  const [sourceFields, setSourceFields] = useState('customer_id, name, email, status, updated_at');
  const [targetFields, setTargetFields] = useState('id, full_name, email_address, lifecycle_status, last_updated');
  const [suggestions, setSuggestions] = useState<MappingSuggestion[]>([]);
  const [mappingState, setMappingState] = useState<'idle' | 'loading' | 'success' | 'error'>('idle');
  const [mappingMessage, setMappingMessage] = useState('');

  useEffect(() => {
    let active = true;
    Promise.allSettled([
      get(DataUrlConstants.CONNECTOR),
      get(DataUrlConstants.IMPORTED_FILES_FOLDERS),
      get(DataUrlConstants.ENTITY_PIPELINE_STATUSES),
    ]).then((results) => {
      if (!active) {
        return;
      }
      const counts = results.map(countResponse);
      setSummary({
        connections: counts[0],
        datasets: counts[1],
        runs: counts[2],
        state: counts.every((count) => count !== null) ? 'ready' : 'partial',
      });
    });
    return () => {
      active = false;
    };
  }, []);

  const readinessCopy = useMemo(() => {
    if (summary.state === 'loading') {
      return 'Checking this workspace…';
    }
    if (summary.state === 'partial') {
      return 'Core platform online; some live counts are unavailable.';
    }
    return 'Core platform online and tenant context verified.';
  }, [summary.state]);

  const requestMapping = async () => {
    const source = splitFields(sourceFields);
    const target = splitFields(targetFields);
    if (!source.length || !target.length) {
      setMappingState('error');
      setMappingMessage('Add at least one source field and one target field.');
      return;
    }

    setMappingState('loading');
    setMappingMessage('');
    setSuggestions([]);
    try {
      const response = await post(DataUrlConstants.NEXTEDGE_AI_MAPPING, { sourceFields: source, targetFields: target });
      const mappings = response.data?.mappings || [];
      setSuggestions(mappings);
      setMappingState('success');
      setMappingMessage(`${mappings.length} validated mapping suggestions returned by Amazon Bedrock.`);
    } catch (error: any) {
      setMappingState('error');
      setMappingMessage(
        error?.response?.data?.message || 'NextEdge AI could not create a mapping suggestion. Please try again.'
      );
    }
  };

  return (
    <main className="v1-workspace" id="main-content">
      <header className="v1-workspace__hero">
        <div>
          <p className="v1-workspace__kicker">NextEdge AI · Focused V1</p>
          <h1>From raw CSV to governed output.</h1>
          <p className="v1-workspace__intro">
            One guided workflow to ingest customer data, map it, validate it, deliver it, and understand every run.
          </p>
          <div className="v1-workspace__support" aria-label="V1 supported connectors">
            <span>CSV input</span>
            <span>Amazon S3</span>
            <span>PostgreSQL</span>
          </div>
        </div>
        <aside className="v1-workspace__tenant" aria-label="Current workspace status">
          <span className={`v1-workspace__status v1-workspace__status--${summary.state}`}>{readinessCopy}</span>
          <dl>
            <div>
              <dt>NextEdge ID</dt>
              <dd>{nextEdgeId || 'Loading…'}</dd>
            </div>
            <div>
              <dt>Registered connections</dt>
              <dd>{summary.connections ?? '—'}</dd>
            </div>
            <div>
              <dt>Dataset folders</dt>
              <dd>{summary.datasets ?? '—'}</dd>
            </div>
            <div>
              <dt>Current pipeline runs</dt>
              <dd>{summary.runs ?? '—'}</dd>
            </div>
          </dl>
        </aside>
      </header>

      <section className="v1-workspace__section" aria-labelledby="workflow-title">
        <div className="v1-workspace__section-heading">
          <div>
            <p className="v1-workspace__kicker">The V1 path</p>
            <h2 id="workflow-title">Five steps. One controlled outcome.</h2>
          </div>
          <a className="v1-workspace__text-link" href="/demo/customers.csv" download>
            Download sample CSV
          </a>
        </div>
        <div className="v1-workspace__workflow">
          {workflow.map((step) => (
            <article className="v1-workspace__step" key={step.number}>
              <div className="v1-workspace__step-number" aria-hidden="true">
                {step.number}
              </div>
              <p className="v1-workspace__step-eyebrow">{step.eyebrow}</p>
              <h3>{step.title}</h3>
              <p>{step.description}</p>
              {step.path.startsWith('#') ? (
                <a href={step.path}>{step.action}</a>
              ) : (
                <Link to={step.path}>{step.action}</Link>
              )}
            </article>
          ))}
        </div>
      </section>

      <section className="v1-workspace__assistant" id="mapping-assistant" aria-labelledby="mapping-title">
        <div className="v1-workspace__assistant-copy">
          <p className="v1-workspace__kicker">Controlled AI assist</p>
          <h2 id="mapping-title">Create a structured field mapping.</h2>
          <p>
            NextEdge AI sends field names only—never CSV row values—to Amazon Bedrock. Every returned mapping is checked
            against the fields you supplied before it reaches this screen.
          </p>
        </div>
        <form
          className="v1-workspace__mapping-form"
          onSubmit={(event) => {
            event.preventDefault();
            requestMapping();
          }}
        >
          <label htmlFor="v1-source-fields">Source CSV fields</label>
          <textarea
            id="v1-source-fields"
            value={sourceFields}
            onChange={(event) => setSourceFields(event.target.value)}
            rows={3}
            aria-describedby="v1-field-help"
          />
          <label htmlFor="v1-target-fields">Target customer fields</label>
          <textarea
            id="v1-target-fields"
            value={targetFields}
            onChange={(event) => setTargetFields(event.target.value)}
            rows={3}
            aria-describedby="v1-field-help"
          />
          <p className="v1-workspace__field-help" id="v1-field-help">
            Separate field names with commas. Maximum 30 per side.
          </p>
          <button type="submit" disabled={mappingState === 'loading'}>
            {mappingState === 'loading' ? 'Creating suggestion…' : 'Suggest mapping with NextEdge AI'}
          </button>
          {mappingMessage && (
            <p
              className={`v1-workspace__message v1-workspace__message--${mappingState}`}
              role="status"
              aria-live="polite"
            >
              {mappingMessage}
            </p>
          )}
        </form>

        {suggestions.length > 0 && (
          <div className="v1-workspace__mapping-results" aria-label="Validated mapping suggestions">
            <div className="v1-workspace__mapping-row v1-workspace__mapping-row--header" aria-hidden="true">
              <span>Source</span>
              <span>Target</span>
              <span>Confidence</span>
              <span>Reason</span>
            </div>
            {suggestions.map((suggestion) => (
              <div className="v1-workspace__mapping-row" key={`${suggestion.source}-${suggestion.target}`}>
                <span data-label="Source">{suggestion.source}</span>
                <span data-label="Target">{suggestion.target}</span>
                <span data-label="Confidence">{Math.round(suggestion.confidence * 100)}%</span>
                <span data-label="Reason">{suggestion.reason}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </main>
  );
};

export default V1Workspace;
