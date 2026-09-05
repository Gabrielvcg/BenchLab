import React from 'react';
import { CirclePlay } from 'lucide-react';

export function LaunchPanel({ submitting, disabled, invocations, languages, message, onRun }: {
  submitting: boolean; disabled: boolean; invocations: number; languages: number; message: string; onRun: () => void;
}) {
  return <div className="launch-panel">
    <button type="button" onClick={onRun} disabled={submitting || disabled} aria-busy={submitting}>
      <CirclePlay size={18} /><span>{submitting ? 'Queuing runs…' : 'Run comparison'}</span>
    </button>
    <p className="field-help">{languages} languages · {invocations} estimated container invocations</p>
    <p className="launch-status" role="status" aria-live="polite">{disabled ? 'Select at least one language and input size.' : message}</p>
  </div>;
}
