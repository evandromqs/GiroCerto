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

comment on table cloud_backups is 'Opaque user backups authenticated by a locally generated recovery secret';
