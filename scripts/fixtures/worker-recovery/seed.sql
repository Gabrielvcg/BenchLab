\set ON_ERROR_STOP on
BEGIN;
INSERT INTO algorithm(id,name,category,version,complexity_declared)
VALUES(1,'callback-fixture','fixture','1','O(1)');
INSERT INTO dataset(id,type,size_value,seed,checksum,dataset_version)
VALUES(1,'fixture',1,1,'fixture','1');
INSERT INTO implementation(id,algorithm_id,language,source_code,implementation_hash)
VALUES(1,1,'PYTHON','print("fixture-result")','fixture'),
      (2,1,'PYTHON','import sys; print("fixture-result"); sys.stderr.write("e" * 12000)','fixture-long-stderr');
INSERT INTO benchmark_run(id,implementation_id,dataset_id,status,trace_id,queued_at,requested_by)
VALUES(1,1,1,'QUEUED','fixture-run-1',CURRENT_TIMESTAMP,'fixture'),
      (2,2,1,'QUEUED','fixture-run-2',CURRENT_TIMESTAMP,'fixture');
COMMIT;
