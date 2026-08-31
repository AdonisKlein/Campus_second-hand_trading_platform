# Cross-process contracts

This directory is the source of truth for interfaces that cross a process seam.
It must not contain shared JPA entities or compiled business-domain classes.

- `http/public-api-v1.tsv` freezes the browser-visible method, path, owner,
  authentication and CSRF contract before extraction.
- `events/*.v1.schema.json` freezes the RabbitMQ envelopes exchanged by Account,
  Marketplace, Trading and Governance. Consumers must ignore unknown fields so
  a producer can add metadata without breaking a rolling deployment; removing
  or renaming a required field needs a new schema version.

Changing a row requires updating its owner, caller tests and the E2E journey that
uses it. Removing a v1 route requires an explicit compatibility decision.
