import { createHash, randomBytes } from "node:crypto";
import { attachDatabasePool } from "@neon/functions";
import { createRemoteJWKSet, jwtVerify } from "jose";
import pg from "pg";

const pool = new pg.Pool({ connectionString: process.env.DATABASE_URL, max: 5 });
attachDatabasePool(pool);
let schemaReady: Promise<void> | undefined;

const MAX_PAYLOAD_BYTES = 5_000_000;
const ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SECRET_PATTERN = /^[A-Za-z0-9_-]{40,64}$/;
const authBaseUrl = process.env.NEON_AUTH_BASE_URL!;
const firebaseProjectId = process.env.FIREBASE_PROJECT_ID ?? "girocerto-copiloto";
const firebaseJwks = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

function hash(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

async function firebaseUser(request: Request) {
  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!token) throw new Error("missing token");
  const { payload } = await jwtVerify(token, firebaseJwks, {
    issuer: `https://securetoken.google.com/${firebaseProjectId}`,
    audience: firebaseProjectId,
  });
  if (!payload.sub) throw new Error("missing subject");
  return {
    id: `firebase:${payload.sub}`,
    email: typeof payload.email === "string" ? payload.email : "",
    name: typeof payload.name === "string" ? payload.name : "Motorista",
  };
}

function json(body: unknown, status = 200): Response {
  return Response.json(body, {
    status,
    headers: { "cache-control": "no-store", "content-type": "application/json; charset=utf-8" },
  });
}

function credentials(request: Request): { id: string; secretHash: string } | Response {
  const id = new URL(request.url).searchParams.get("id")?.trim() ?? "";
  const authorization = request.headers.get("authorization") ?? "";
  const secret = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!ID_PATTERN.test(id) || !SECRET_PATTERN.test(secret)) {
    return json({ error: "Credenciais de backup inválidas" }, 401);
  }
  return { id, secretHash: createHash("sha256").update(secret, "utf8").digest("hex") };
}

async function createMobileSession(user: { id: string; email?: string; name?: string }) {
  const token = randomBytes(32).toString("base64url");
  await pool.query(
    `insert into mobile_sessions(token_hash,user_id,email,display_name,expires_at)
     values($1,$2,$3,$4,now()+interval '90 days')`,
    [hash(token), user.id, user.email ?? "", user.name?.trim() || "Motorista"],
  );
  return { token, user: { id: user.id, email: user.email ?? "", name: user.name?.trim() || "Motorista" } };
}

async function emailAuth(request: Request, action: "sign-up" | "sign-in"): Promise<Response> {
  const body = await request.json().catch(() => ({})) as { email?: string; password?: string; name?: string };
  const email = body.email?.trim().toLowerCase() ?? "";
  const password = body.password ?? "";
  const name = body.name?.trim() ?? "";
  if (!/^\S+@\S+\.\S+$/.test(email) || password.length < 8 || (action === "sign-up" && name.length < 2)) {
    return json({ error: "Preencha nome, e-mail válido e senha com pelo menos 8 caracteres" }, 400);
  }
  const callbackURL = `${new URL(request.url).origin}/auth/callback`;
  const authResponse = await fetch(`${authBaseUrl}/${action}/email`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "accept": "application/json",
      "Origin": new URL(request.url).origin,
      "Referer": `${new URL(request.url).origin}/`,
    },
    body: JSON.stringify(action === "sign-up" ? { email, password, name, callbackURL } : { email, password, callbackURL }),
  });
  const result = await authResponse.json().catch(() => ({})) as {
    user?: { id?: string; email?: string; name?: string };
    message?: string; code?: string;
  };
  if (!authResponse.ok || !result.user?.id) {
    const fallback = action === "sign-up" ? "Não foi possível criar a conta" : "E-mail ou senha incorretos";
    const status = authResponse.status >= 400 && authResponse.status < 500 ? authResponse.status : 502;
    return json({ error: result.message ?? fallback, code: result.code }, status);
  }
  return json(await createMobileSession({
    id: result.user.id,
    email: result.user.email ?? email,
    name: result.user.name ?? name,
  }));
}

export default async function cloudBackups(request: Request): Promise<Response> {
  schemaReady ??= pool.query(`
    create table if not exists cloud_backups (
      id uuid primary key,
      secret_hash char(64) not null,
      payload jsonb not null,
      client_updated_at bigint not null check (client_updated_at > 0),
      created_at timestamptz not null default now(),
      updated_at timestamptz not null default now(),
      check (octet_length(payload::text) <= 5000000)
    );
    create index if not exists cloud_backups_updated_at_idx on cloud_backups (updated_at desc);
    create table if not exists mobile_sessions (
      token_hash char(64) primary key,
      user_id text not null,
      email text not null,
      display_name text not null,
      expires_at timestamptz not null,
      created_at timestamptz not null default now()
    );
    create index if not exists mobile_sessions_user_idx on mobile_sessions (user_id);
  `).then(() => undefined);
  await schemaReady;

  const url = new URL(request.url);
  if (request.method === "POST" && url.pathname.endsWith("/auth/email/sign-up")) {
    return emailAuth(request, "sign-up");
  }

  if (request.method === "POST" && url.pathname.endsWith("/auth/email/sign-in")) {
    return emailAuth(request, "sign-in");
  }
  if (request.method === "POST" && url.pathname.endsWith("/auth/firebase/exchange")) {
    try {
      return json(await createMobileSession(await firebaseUser(request)));
    } catch {
      return json({ error: "Login do Google inválido ou expirado" }, 401);
    }
  }
  if (request.method === "GET" && url.pathname.endsWith("/auth/session")) {
    const bearer = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "") ?? "";
    const found = bearer ? await pool.query(
      `select user_id,email,display_name from mobile_sessions where token_hash=$1 and expires_at>now()`, [hash(bearer)]
    ) : { rowCount: 0, rows: [] };
    if (found.rowCount === 0) return json({ error: "Sessão expirada" }, 401);
    const user = found.rows[0];
    return json({ user: { id: user.user_id, email: user.email, name: user.display_name } });
  }

  if (request.method === "GET" && url.pathname.endsWith("/health")) {
    return json({ ok: true, service: "giro-certo-backups" });
  }

  const auth = credentials(request);
  if (auth instanceof Response) return auth;

  if (request.method === "GET") {
    const result = await pool.query(
      `select payload, client_updated_at, updated_at
         from cloud_backups where id = $1 and secret_hash = $2`,
      [auth.id, auth.secretHash],
    );
    if (result.rowCount === 0) return json({ error: "Backup não encontrado" }, 404);
    return json({
      payload: result.rows[0].payload,
      clientUpdatedAt: Number(result.rows[0].client_updated_at),
      storedAt: result.rows[0].updated_at,
    });
  }

  if (request.method === "PUT") {
    const declaredSize = Number(request.headers.get("content-length") ?? "0");
    if (declaredSize > MAX_PAYLOAD_BYTES + 100_000) return json({ error: "Backup muito grande" }, 413);
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > MAX_PAYLOAD_BYTES + 100_000) {
      return json({ error: "Backup muito grande" }, 413);
    }
    let body: { payload?: unknown; clientUpdatedAt?: unknown };
    try { body = JSON.parse(raw); } catch { return json({ error: "JSON inválido" }, 400); }
    if (typeof body.payload !== "object" || body.payload === null || Array.isArray(body.payload)) {
      return json({ error: "Conteúdo de backup inválido" }, 400);
    }
    const payloadText = JSON.stringify(body.payload);
    if (new TextEncoder().encode(payloadText).byteLength > MAX_PAYLOAD_BYTES) {
      return json({ error: "Backup muito grande" }, 413);
    }
    const clientUpdatedAt = Number(body.clientUpdatedAt);
    if (!Number.isSafeInteger(clientUpdatedAt) || clientUpdatedAt <= 0) {
      return json({ error: "Data do backup inválida" }, 400);
    }
    const result = await pool.query(
      `insert into cloud_backups (id, secret_hash, payload, client_updated_at)
       values ($1, $2, $3::jsonb, $4)
       on conflict (id) do update set
         payload = excluded.payload,
         client_updated_at = excluded.client_updated_at,
         updated_at = now()
       where cloud_backups.secret_hash = excluded.secret_hash
       returning updated_at`,
      [auth.id, auth.secretHash, payloadText, clientUpdatedAt],
    );
    if (result.rowCount === 0) return json({ error: "Chave de recuperação incorreta" }, 403);
    return json({ ok: true, storedAt: result.rows[0].updated_at });
  }

  return json({ error: "Método não permitido" }, 405);
}
