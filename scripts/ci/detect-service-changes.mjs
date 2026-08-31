import fs from "node:fs";
import { execFileSync } from "node:child_process";

const values = process.argv.slice(2);
const args = {};
for (let index = 0; index < values.length; index += 1) {
  if (values[index].startsWith("--")) args[values[index].slice(2)] = values[index + 1] ?? "";
}
const services = ["api-gateway", "account-service", "marketplace-service", "trading-service", "governance-service"];
const all = [...services, "web", "infrastructure"];
let files;
try {
  const base = args.base || (args.before && !/^0+$/.test(args.before) ? args.before : "HEAD~1");
  files = execFileSync("git", ["diff", "--name-only", `${base}...HEAD`], {encoding: "utf8"}).split(/\r?\n/).filter(Boolean);
} catch {
  files = ["shared-or-unknown-change"];
}
const infrastructure = files.some(path => /^(\.github\/|contracts\/|scripts\/ci\/|k8s\/|deploy\/|e2e\/)/.test(path));
const affected = infrastructure || files.includes("shared-or-unknown-change") ? all : all.filter(name => name === "web"
  ? files.some(path => path.startsWith("frontend/"))
  : name !== "infrastructure" && files.some(path => path.startsWith(`services/${name}/`)));
const result = {event: args.event || "local", comparedFrom: args.base || args.before || "HEAD~1", files, affected};
fs.mkdirSync("test-results", {recursive: true});
fs.writeFileSync(args.output || "test-results/changed-services.json", `${JSON.stringify(result, null, 2)}\n`);
console.log(`Affected components: ${affected.join(", ") || "documentation-only"}`);
