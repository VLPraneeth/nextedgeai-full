import './InformationSnippet.scss';

interface InformationSnippetProps {
  label: string;
  value: string;
}

const InformationSnippet = ({ label, value }: InformationSnippetProps) => (
  <div className="information-snippet">
    <h1 className="information-snippet__label">{label}</h1>
    {value && <h2 className="information-snippet__value">{value}</h2>}
  </div>
);

export default InformationSnippet;
