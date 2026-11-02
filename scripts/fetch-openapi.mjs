// Downloads the backend OpenAPI spec into openapi/openapi.json (input for `npm run api:types`).
// Usage: npm run api:fetch   (BACKEND_URL defaults to http://localhost:8080)
import { mkdir, writeFile } from "node:fs/promises";

const backend = process.env.BACKEND_URL ?? "http://localhost:8080";
const url = `${backend}/v3/api-docs`;

const res = await fetch(url);
if (!res.ok) {
  console.error(`Could not fetch ${url}: HTTP ${res.status}`);
  process.exit(1);
}
const spec = await res.json();
await mkdir("openapi", { recursive: true });
await writeFile("openapi/openapi.json", JSON.stringify(spec, null, 2) + "\n");
console.log(`Saved ${Object.keys(spec.paths ?? {}).length} paths from ${url} to openapi/openapi.json`);
