# Cross-process contracts

This directory is the source of truth for interfaces that cross a process seam.
It must not contain shared JPA entities or compiled business-domain classes.

- `http/public-api-v1.tsv` freezes the browser-visible method, path, owner,
  authentication and CSRF contract before extraction.
- Later work items add internal HTTP OpenAPI descriptions and versioned event
  JSON Schemas here.

Changing a row requires updating its owner, caller tests and the E2E journey that
uses it. Removing a v1 route requires an explicit compatibility decision.
