# Personal run history

Choose **Load my history**, then expand an experiment to inspect saved runs, statuses and CPU/wall-time measurements. **Compare this experiment** selects its results in the comparison chart without launching new work.

**Load older runs** fetches another page; **Refresh history** replaces the loaded window with the newest page. Each page contains up to 100 runs belonging to the signed-in account. Experiments may span pages, so counts refer to loaded runs. Missing measurements remain unavailable, not zero.

`GET /api/runs/history?beforeId=<last-returned-id>` uses descending IDs and an exclusive cursor. Omit the cursor for the newest page. Older persisted rows are not deleted when a new comparison starts.
