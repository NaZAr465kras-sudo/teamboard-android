// Team-Board KI-Vermittler (Cloudflare Worker)
// Haelt den Claude-API-Schluessel geheim. Nur angemeldete, registrierte Mitarbeiter duerfen fragen.
const PROJECT = "teamboard-4b99b";
const ORIGINS = ["https://nazar465kras-sudo.github.io", "https://appassets.androidplatform.net"];
const MODELS = { quick: "claude-haiku-5-5", default: "claude-sonnet-5-5", complex: "claude-opus-5-5" };
const MAX_TOKENS = 1200, MAX_MSGS = 60, MAX_TOOLS = 8, MAX_BODY = 6_000_000;
const PER_MIN = 12, PER_DAY = 300;
const JWK_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
let jwkCache = { t: 0, keys: [] };
const hits = new Map();

const b64u = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(s.length / 4) * 4, "=")), (c) => c.charCodeAt(0));
const J = (s) => JSON.parse(new TextDecoder().decode(b64u(s)));

function cors(req) {
  const o = req.headers.get("Origin") || "";
  return {
    "Access-Control-Allow-Origin": ORIGINS.includes(o) ? o : ORIGINS[0],
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin",
  };
}
const out = (req, status, obj) =>
  new Response(JSON.stringify(obj), { status, headers: { ...cors(req), "Content-Type": "application/json" } });

async function keys() {
  if (Date.now() - jwkCache.t < 3_600_000 && jwkCache.keys.length) return jwkCache.keys;
  const r = await fetch(JWK_URL);
  if (!r.ok) throw new Error("jwk");
  jwkCache = { t: Date.now(), keys: (await r.json()).keys };
  return jwkCache.keys;
}

async function verify(token) {
  const p = token.split(".");
  if (p.length !== 3) return null;
  let h, c;
  try { h = J(p[0]); c = J(p[1]); } catch { return null; }
  if (h.alg !== "RS256") return null;
  const jwk = (await keys()).find((k) => k.kid === h.kid);
  if (!jwk) return null;
  const key = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, b64u(p[2]), new TextEncoder().encode(p[0] + "." + p[1]));
  if (!ok) return null;
  const now = Math.floor(Date.now() / 1000);
  if (c.aud !== PROJECT || c.iss !== "https://securetoken.google.com/" + PROJECT || !c.sub || c.exp < now || c.iat > now + 300) return null;
  if (typeof c.email !== "string") return null;
  return c.email.split("@")[0].split(".")[0];
}

async function isMember(id, token) {
  const r = await fetch(`https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/(default)/documents/members/${encodeURIComponent(id)}`, {
    headers: { Authorization: "Bearer " + token },
  });
  if (!r.ok) return false;
  const d = await r.json();
  const del = d.fields && d.fields.deletedAt;
  return !(del && !("nullValue" in del));
}

function limited(id) {
  const now = Date.now();
  const h = (hits.get(id) || []).filter((t) => now - t < 86_400_000);
  const min = h.filter((t) => now - t < 60_000).length;
  if (min >= PER_MIN || h.length >= PER_DAY) { hits.set(id, h); return true; }
  h.push(now); hits.set(id, h);
  if (hits.size > 5000) hits.clear();
  return false;
}

function clean(b) {
  if (!b || !Array.isArray(b.messages) || !b.messages.length || b.messages.length > MAX_MSGS) return null;
  for (const m of b.messages) {
    if (!m || (m.role !== "user" && m.role !== "assistant")) return null;
    if (typeof m.content !== "string" && !Array.isArray(m.content)) return null;
  }
  const body = { model: MODELS[b.tier] || MODELS.default, max_tokens: MAX_TOKENS, messages: b.messages };
  if (Array.isArray(b.tools) && b.tools.length) {
    body.tools = b.tools.slice(0, MAX_TOOLS).map((t) => ({
      name: String(t.name).slice(0, 64),
      description: String(t.description || "").slice(0, 2000),
      input_schema: t.input_schema && typeof t.input_schema === "object" ? t.input_schema : { type: "object", properties: {} },
    }));
  }
  return body;
}


// ---- Kostenlose Variante: Cloudflare Workers AI (Binding "AI", kein Schluessel noetig) ----
const FREE_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
function flat(c) {
  if (typeof c === "string") return c;
  return c.map((p) => p.type === "text" ? p.text : p.type === "tool_use" ? "[Werkzeug " + p.name + " " + JSON.stringify(p.input || {}) + "]" :
    p.type === "tool_result" ? "[Ergebnis] " + (typeof p.content === "string" ? p.content : JSON.stringify(p.content)) :
    p.type === "image" ? "[Foto: in der kostenlosen Version nicht lesbar]" : "").filter(Boolean).join("\n");
}
async function freeAI(env, body) {
  const messages = [{ role: "system", content: "Du bist Schnorki, der freundliche Assistent eines Teams im Restaurant/Cafe. Antworte kurz und auf Deutsch, ausser der Nutzer schreibt in einer anderen Sprache. Nutze die Werkzeuge, wenn sie helfen." }];
  for (const m of body.messages) messages.push({ role: m.role, content: flat(m.content) || "(leer)" });
  const req = { messages, max_tokens: body.max_tokens };
  if (body.tools) req.tools = body.tools.map((t) => ({ name: t.name, description: t.description, parameters: t.input_schema }));
  const r = await env.AI.run(FREE_MODEL, req);
  const x = r && r.result ? r.result : r;
  const ch = x && x.choices && x.choices[0] && x.choices[0].message;
  let text = (ch ? ch.content : x && x.response) || "";
  if (typeof text !== "string") text = JSON.stringify(text);
  let calls = (ch ? ch.tool_calls : x && x.tool_calls) || [];
  const content = [];
  if (text) content.push({ type: "text", text });
  calls = calls.slice(0, 3);
  calls.forEach((c, i) => {
    const f = c.function || c;
    let a = f.arguments !== undefined ? f.arguments : f.input;
    if (typeof a === "string") { try { a = JSON.parse(a); } catch { a = {}; } }
    content.push({ type: "tool_use", id: "t" + Date.now() + i, name: f.name, input: a || {} });
  });
  return { content, stop_reason: calls.length ? "tool_use" : "end_turn" };
}

// ---- Gemini (Google AI Studio, Secret GEMINI_API_KEY) ----
const GEMINI_MODELS = ["gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash"]; // wird der Reihe nach probiert
const SYS = "Du bist Schnorki, der freundliche Assistent eines Teams im Restaurant/Cafe. Antworte kurz und auf Deutsch, ausser der Nutzer schreibt in einer anderen Sprache. Nutze die Werkzeuge, wenn sie helfen.";
function gschema(x) {
  if (Array.isArray(x)) return x.map(gschema);
  if (!x || typeof x !== "object") return x;
  const o = {};
  for (const k in x) if (!["additionalProperties", "$schema", "$id", "definitions", "$defs", "default", "examples"].includes(k)) o[k] = gschema(x[k]);
  return o;
}
// Schluessel kann ein normales Secret (Text) oder eine Secrets-Store-Bindung (mit .get()) sein
async function secret(v) { return v && typeof v === "object" && typeof v.get === "function" ? await v.get() : v; }
async function gemini(env, body) {
  const gkey = await secret(env.GEMINI_API_KEY);
  const contents = body.messages.map((m) => {
    const parts = [];
    if (typeof m.content === "string") parts.push({ text: m.content || "(leer)" });
    else {
      const t = flat(m.content.filter((p) => p.type !== "image"));
      if (t) parts.push({ text: t });
      for (const p of m.content) if (p.type === "image" && p.source && p.source.data) parts.push({ inlineData: { mimeType: p.source.media_type || "image/jpeg", data: p.source.data } });
    }
    if (!parts.length) parts.push({ text: "(leer)" });
    return { role: m.role === "assistant" ? "model" : "user", parts };
  });
  const req = { systemInstruction: { parts: [{ text: SYS }] }, contents, generationConfig: { maxOutputTokens: body.max_tokens } };
  if (body.tools) req.tools = [{ functionDeclarations: body.tools.map((t) => ({ name: t.name, description: t.description || t.name, parameters: gschema(t.input_schema) })) }];
  let r, last = "";
  for (const m of GEMINI_MODELS) {
    r = await fetch("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent", {
      method: "POST", headers: { "x-goog-api-key": gkey, "content-type": "application/json" }, body: JSON.stringify(req),
    });
    if (r.ok) break;
    let msg = ""; try { msg = ((await r.json()).error || {}).message || ""; } catch {}
    last = m + " " + r.status + " " + msg.replace(/AIza[\w-]+/g, "***").slice(0, 160);
    if (![404, 429, 500, 502, 503, 504].includes(r.status)) break;
    if (r.status !== 404) await new Promise((ok) => setTimeout(ok, 400)); // kurz warten, dann naechstes Modell
  }
  if (!r.ok) throw Object.assign(new Error("gemini: " + last), { status: r.status === 429 ? 429 : 502 });
  const d = await r.json();
  const parts = (d.candidates && d.candidates[0] && d.candidates[0].content && d.candidates[0].content.parts) || [];
  const content = [], text = parts.filter((p) => p.text && !p.thought).map((p) => p.text).join("");
  if (text) content.push({ type: "text", text });
  const fc = parts.filter((p) => p.functionCall).slice(0, 3);
  fc.forEach((p, i) => content.push({ type: "tool_use", id: "g" + Date.now() + i, name: p.functionCall.name, input: p.functionCall.args || {} }));
  if (!content.length) content.push({ type: "text", text: "Dazu kann ich leider nichts sagen." });
  return { content, stop_reason: fc.length ? "tool_use" : "end_turn" };
}

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: cors(req) });
    if (req.method !== "POST") return out(req, 405, { error: "method" });
    const auth = req.headers.get("Authorization") || "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
    if (!token) return out(req, 401, { error: "auth" });
    let id;
    try { id = await verify(token); } catch { return out(req, 502, { error: "verify" }); }
    if (!id) return out(req, 401, { error: "auth" });
    let member;
    try { member = await isMember(id, token); } catch { return out(req, 502, { error: "members" }); }
    if (!member) return out(req, 403, { error: "member" });
    if (limited(id)) return out(req, 429, { error: "rate" });
    const len = Number(req.headers.get("Content-Length") || 0);
    if (len > MAX_BODY) return out(req, 413, { error: "size" });
    let b;
    try { const t = await req.text(); if (t.length > MAX_BODY) return out(req, 413, { error: "size" }); b = JSON.parse(t); } catch { return out(req, 400, { error: "json" }); }
    const body = clean(b);
    if (!body) return out(req, 400, { error: "body" });
    if (!env.ANTHROPIC_API_KEY && env.GEMINI_API_KEY) {
      try { return out(req, 200, await gemini(env, body)); } catch (e) { return out(req, e.status === 429 ? 429 : 502, { error: "ai", detail: String(e && e.message).slice(0, 200) }); }
    }
    if (!env.ANTHROPIC_API_KEY && env.AI) {
      try { const res = await freeAI(env, body); return out(req, 200, res); } catch (e) { return out(req, 502, { error: "ai", detail: String(e && e.message).slice(0, 200) }); }
    }
    const r = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: { "x-api-key": await secret(env.ANTHROPIC_API_KEY), "anthropic-version": "2023-06-01", "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    const text = await r.text();
    return new Response(text, { status: r.status === 200 ? 200 : 502, headers: { ...cors(req), "Content-Type": "application/json" } });
  },
};
