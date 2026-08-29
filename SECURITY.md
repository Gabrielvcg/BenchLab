# Security Policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Contact the
maintainer privately through the contact details in the GitHub profile and
include the affected component, reproduction steps, and impact.

## Secret handling

Runtime credentials, JWT signing keys, worker tokens, database passwords, and
TLS keystores must be supplied through the deployment environment or a secret
manager. They must never be committed to the repository. The TLS profile
expects `BENCHLAB_TLS_KEYSTORE_PATH` and `BENCHLAB_TLS_KEYSTORE_PASSWORD` when
enabled.
