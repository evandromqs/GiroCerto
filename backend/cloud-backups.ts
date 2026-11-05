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
const authJwks = createRemoteJWKSet(new URL(process.env.NEON_AUTH_JWKS_URL!));

function hash(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

async function neonUser(request: Request) {
  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7).trim() : "";
  if (!token) throw new Error("missing token");
  const { payload } = await jwtVerify(token, authJwks, { issuer: authBaseUrl });
  if (!payload.sub) throw new Error("missing subject");
  return {
    id: payload.sub,
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
    create table if not exists mobile_login_codes (
      code_hash char(64) primary key,
      user_id text not null,
      email text not null,
      display_name text not null,
      expires_at timestamptz not null,
      used_at timestamptz
    );
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
  if (request.method === "GET" && url.pathname.endsWith("/auth/start")) {
    const callbackURL = `${url.origin}/auth/callback`;
    const authResponse = await fetch(`${authBaseUrl}/sign-in/social`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ provider: "google", callbackURL, disableRedirect: true }),
    });
    const result = await authResponse.json() as { url?: string; message?: string };
    if (!authResponse.ok || !result.url) return json({ error: result.message ?? "Login indisponível" }, 502);
    return json({ url: result.url });
  }

  if (request.method === "GET" && url.pathname.endsWith("/auth/callback")) {
    const endpoint = url.origin;
    const html = `<!doctype html><html lang="pt-BR"><meta name="viewport" content="width=device-width"><title>Giro Certo</title>
      <style>body{font-family:system-ui;background:#0b1730;color:white;display:grid;place-items:center;min-height:90vh;margin:0}main{text-align:center;padding:28px}button{padding:14px 20px;border-radius:14px;border:0;font-weight:700}</style>
      <main><h1>Giro Certo</h1><p id="status">Concluindo seu login com segurança…</p><button id="retry" hidden>Tentar novamente</button></main>
      <script>
      async function finish(){
        const status=document.getElementById('status'),retry=document.getElementById('retry'); retry.hidden=true;
        try {
          const tokenResponse=await fetch(${JSON.stringify(`${authBaseUrl}/token`)},{credentials:'include'});
          const tokenData=await tokenResponse.json(); const token=tokenData?.token;
          if(!token) throw new Error('Sessão do Google não encontrada');
          const codeResponse=await fetch(${JSON.stringify(`${endpoint}/auth/mobile-code`)},{method:'POST',headers:{Authorization:'Bearer '+token}});
          const codeData=await codeResponse.json(); if(!codeData.code) throw new Error(codeData.error||'Código indisponível');
          status.textContent='Login concluído. Voltando ao aplicativo…'; location.href='girocerto://auth/callback?code='+encodeURIComponent(codeData.code);
        } catch(error){ status.textContent='Não foi possível concluir o login. '+error.message; retry.hidden=false; }
      }
      document.getElementById('retry').onclick=finish; finish();
      </script></html>`;
    return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" } });
  }

  if (request.method === "POST" && url.pathname.endsWith("/auth/mobile-code")) {
    try {
      const user = await neonUser(request);
      const code = randomBytes(32).toString("base64url");
      await pool.query(
        `insert into mobile_login_codes(code_hash,user_id,email,display_name,expires_at)
         values($1,$2,$3,$4,now()+interval '5 minutes')`,
        [hash(code), user.id, user.email, user.name],
      );
      return json({ code });
    } catch { return json({ error: "Sessão Neon inválida" }, 401); }
  }

  if (request.method === "POST" && url.pathname.endsWith("/auth/exchange")) {
    const body = await request.json().catch(() => ({})) as { code?: string };
    if (!body.code || !SECRET_PATTERN.test(body.code)) return json({ error: "Código inválido" }, 400);
    const client = await pool.connect();
    try {
      await client.query("begin");
      const found = await client.query(
        `update mobile_login_codes set used_at=now()
         where code_hash=$1 and used_at is null and expires_at>now()
         returning user_id,email,display_name`, [hash(body.code)],
      );
      if (found.rowCount === 0) { await client.query("rollback"); return json({ error: "Código expirado" }, 401); }
      const user = found.rows[0];
      const token = randomBytes(32).toString("base64url");
      await client.query(`insert into mobile_sessions(token_hash,user_id,email,display_name,expires_at)
        values($1,$2,$3,$4,now()+interval '90 days')`, [hash(token), user.user_id, user.email, user.display_name]);
      await client.query("commit");
      return json({ token, user: { id: user.user_id, email: user.email, name: user.display_name } });
    } catch (error) { await client.query("rollback"); throw error; }
    finally { client.release(); }
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
