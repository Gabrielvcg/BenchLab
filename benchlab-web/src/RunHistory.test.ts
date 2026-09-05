import { expect, it } from 'vitest';
import { groupHistory, type HistoryRun } from './RunHistory';

it('keeps earlier experiments and failed runs grouped without dropping rows', () => {
  const rows = [
    {id:30,algorithmId:2,algorithmName:'second',status:'SUCCEEDED'},
    {id:29,algorithmId:1,algorithmName:'first',status:'TIMEOUT'},
    {id:28,algorithmId:1,algorithmName:'first',status:'SUCCEEDED'},
  ] as HistoryRun[];
  const groups = groupHistory(rows);
  expect(groups.map(group => group.name)).toEqual(['second','first']);
  expect(groups[1].runs.map(run => run.id)).toEqual([29,28]);
});
