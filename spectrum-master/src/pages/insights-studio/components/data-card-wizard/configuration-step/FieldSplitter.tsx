import './FieldSplitter.scss';

export function FieldSplitter({ label }: { label: string }) {
  return (
    <div className="field-splitter">
      <div className="field-splitter__label">{label}</div>
      <div className="field-splitter__line" />
    </div>
  );
}
