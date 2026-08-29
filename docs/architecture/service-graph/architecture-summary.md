# API Service Graph Summary

## Inventory

- config: 16
- controller: 18
- dao: 1
- database: 7
- endpoint: 32
- external_client: 9
- handler: 39
- model: 17
- queue: 6
- repository: 9
- service: 11
- unknown: 64

## Endpoint Paths

### GET /api/account

- GET /api/account -> AccountResource.getAccount

### POST /api/account

- POST /api/account -> AccountResource.saveAccount

### POST /api/account/change-password

- POST /api/account/change-password -> AccountResource.changePassword

### POST /api/account/reset-password/finish

- POST /api/account/reset-password/finish -> AccountResource.finishPasswordReset

### POST /api/account/reset-password/init

- POST /api/account/reset-password/init -> AccountResource.requestPasswordReset

### GET /api/activate

- GET /api/activate -> AccountResource.activateAccount

### GET /api/algorithms

- GET /api/algorithms -> BenchmarkResource.listAlgorithms

### POST /api/algorithms

- POST /api/algorithms -> BenchmarkResource.createAlgorithm

### GET /api/authenticate

- GET /api/authenticate -> AuthenticateController.isAuthenticated

### POST /api/authenticate

- POST /api/authenticate -> AuthenticateController.authorize

### GET /api/benchmarks/compare

- GET /api/benchmarks/compare -> BenchmarkResource.compare

### GET /api/benchmarks/complexity

- GET /api/benchmarks/complexity -> BenchmarkResource.complexity

### GET /api/benchmarks/timeseries

- GET /api/benchmarks/timeseries -> BenchmarkResource.timeseries

### GET /api/datasets

- GET /api/datasets -> BenchmarkResource.listDatasets

### POST /api/datasets

- POST /api/datasets -> BenchmarkResource.createDataset

### GET /api/exception-translator-test/access-denied

- GET /api/exception-translator-test/access-denied -> ExceptionTranslatorTestController.accessdenied

### GET /api/exception-translator-test/concurrency-failure

- GET /api/exception-translator-test/concurrency-failure -> ExceptionTranslatorTestController.concurrencyFailure

### GET /api/exception-translator-test/internal-server-error

- GET /api/exception-translator-test/internal-server-error -> ExceptionTranslatorTestController.internalServerError

### POST /api/exception-translator-test/method-argument

- POST /api/exception-translator-test/method-argument -> ExceptionTranslatorTestController.methodArgument

### GET /api/exception-translator-test/missing-servlet-request-parameter

- GET /api/exception-translator-test/missing-servlet-request-parameter -> ExceptionTranslatorTestController.missingServletRequestParameterException

### GET /api/exception-translator-test/missing-servlet-request-part

- GET /api/exception-translator-test/missing-servlet-request-part -> ExceptionTranslatorTestController.missingServletRequestPartException

### GET /api/exception-translator-test/response-status

- GET /api/exception-translator-test/response-status -> ExceptionTranslatorTestController.exceptionWithResponseStatus

### GET /api/exception-translator-test/unauthorized

- GET /api/exception-translator-test/unauthorized -> ExceptionTranslatorTestController.unauthorized

### POST /api/implementations

- POST /api/implementations -> BenchmarkResource.createImplementation

### POST /api/internal/runs/{id}/result

- POST /api/internal/runs/{id}/result -> BenchmarkResource.registerResult

### POST /api/register

- POST /api/register -> AccountResource.registerAccount

### GET /api/runs

- GET /api/runs -> BenchmarkResource.listRecentRuns

### POST /api/runs

- POST /api/runs -> BenchmarkResource.createRun

### GET /api/runs/{id}

- GET /api/runs/{id} -> BenchmarkResource.getRun

### GET /api/test-cors

- GET /api/test-cors -> WebConfigurerTestController.testCorsOnApiPath

### GET /api/users

- GET /api/users -> PublicUserResource.getAllPublicUsers

### GET /test/test-cors

- GET /test/test-cors -> WebConfigurerTestController.testCorsOnOtherPath

## High Fanout Nodes

- UserResource (controller): 10 outgoing edges
- UserService (service): 8 outgoing edges
- BenchmarkServiceImpl (service): 8 outgoing edges
- BenchmarkResource (controller): 6 outgoing edges
- UserResourceIT (controller): 4 outgoing edges
- DomainUserDetailsService (service): 3 outgoing edges
- AccountResource (controller): 3 outgoing edges
- AccountResourceException (controller): 3 outgoing edges
- DomainUserDetailsServiceIT (service): 3 outgoing edges
- AccountResourceIT (controller): 3 outgoing edges

## Findings

- low: Added 23 low-confidence inferred dependencies based on co-located naming patterns.
- info: Detected 32 endpoints and 229 nodes.
- info: Node inventory: config=16, controller=18, dao=1, database=7, endpoint=32, external_client=9, handler=39, model=17, queue=6, repository=9, service=11, unknown=64
- low: 91 nodes are low-confidence, usually naming-based detections.
- low: 23 edges are explicit low-confidence inferred dependencies.

## Limitations

- generated_code: Static analysis may be incomplete because generated code was detected. (src/main/java/com/vacaro/benchlab/GeneratedByJHipster.java:9)
- ambiguous_di: Static analysis may be incomplete because ambiguous di was detected. (src/main/java/com/vacaro/benchlab/config/LiquibaseConfiguration.java:36)
- reflection: Static analysis may be incomplete because reflection was detected. (src/main/java/com/vacaro/benchlab/service/MailService.java:77)
- reflection: Static analysis may be incomplete because reflection was detected. (src/test/java/com/vacaro/benchlab/config/SqlTestContainersSpringContextCustomizerFactory.java:34)
- reflection: Static analysis may be incomplete because reflection was detected. (src/test/java/com/vacaro/benchlab/service/MailServiceIT.java:60)
- reflection: Static analysis may be incomplete because reflection was detected. (src/test/java/com/vacaro/benchlab/web/rest/TestUtil.java:10)
