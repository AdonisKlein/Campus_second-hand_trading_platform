import fs from "node:fs";
import path from "node:path";

const directory = "contracts/events";
const files = fs.readdirSync(directory).filter(name => name.endsWith(".v1.schema.json")).sort();
const expected = ["governance-action-requested", "governance-action-result", "item-command", "item-result", "user-public-profile-changed"];
const failures = [];

for (const name of expected) {
  if (!files.includes(`${name}.v1.schema.json`)) failures.push(`missing ${name}.v1.schema.json`);
}

for (const file of files) {
  const schema = JSON.parse(fs.readFileSync(path.join(directory, file), "utf8"));
  if (schema.type !== "object") failures.push(`${file}: root type must be object`);
  if (schema.additionalProperties !== true) failures.push(`${file}: v1 consumers must tolerate additive metadata`);
  for (const field of ["eventId", "correlationId", "version", "occurredAt", "producer", "type"]) {
    if (!schema.required?.includes(field)) failures.push(`${file}: envelope requires ${field}`);
  }
  if (schema.properties?.version?.const !== 1) failures.push(`${file}: version must be const 1`);

  const sample = Object.fromEntries((schema.required || []).map(field => [field, sampleValue(field, schema.properties?.[field])]));
  sample.futureMetadata = { safelyIgnored: true };
  const valid = validate(schema, sample);
  if (valid.length) failures.push(`${file}: valid additive sample rejected: ${valid.join(", ")}`);
  for (const required of schema.required || []) {
    const missing = { ...sample }; delete missing[required];
    if (validate(schema, missing).length === 0) failures.push(`${file}: missing required ${required} was accepted`);
  }
}

const profileDto = fs.readFileSync("services/marketplace-service/src/main/java/com/campus/secondhand/marketplace/UserPublicProfileChanged.java", "utf8");
if (!profileDto.includes("@JsonIgnoreProperties(ignoreUnknown = true)")) failures.push("profile event DTO must ignore additive producer fields");

if (failures.length) {
  console.error(failures.map(failure => `- ${failure}`).join("\n"));
  process.exit(1);
}
console.log(`Versioned event contracts passed: ${files.length} schemas; additive fields accepted and required fields enforced.`);

function sampleValue(field, definition = {}) {
  if (definition.const !== undefined) return definition.const;
  if (definition.enum) return definition.enum[0];
  const type = Array.isArray(definition.type) ? definition.type.find(value => value !== "null") : definition.type;
  if (type === "integer") return Math.max(1, definition.minimum || 1);
  if (field === "occurredAt") return "2026-08-31T00:00:00Z";
  return `${field}-sample`;
}

function validate(schema, value) {
  const errors = [];
  for (const field of schema.required || []) if (!(field in value)) errors.push(`missing ${field}`);
  for (const [field, fieldValue] of Object.entries(value)) {
    const definition = schema.properties?.[field];
    if (!definition) { if (schema.additionalProperties === false) errors.push(`unknown ${field}`); continue; }
    if (definition.const !== undefined && fieldValue !== definition.const) errors.push(`${field} const`);
    if (definition.enum && !definition.enum.includes(fieldValue)) errors.push(`${field} enum`);
    const types = Array.isArray(definition.type) ? definition.type : definition.type ? [definition.type] : [];
    if (types.length && !types.some(type => matches(type, fieldValue))) errors.push(`${field} type`);
    if (typeof fieldValue === "number" && definition.minimum !== undefined && fieldValue < definition.minimum) errors.push(`${field} minimum`);
    if (typeof fieldValue === "string" && definition.minLength !== undefined && fieldValue.length < definition.minLength) errors.push(`${field} minLength`);
  }
  return errors;
}

function matches(type, value) {
  return type === "null" ? value === null
    : type === "integer" ? Number.isInteger(value)
      : type === "string" ? typeof value === "string"
        : type === "object" ? value !== null && typeof value === "object" && !Array.isArray(value)
          : true;
}
