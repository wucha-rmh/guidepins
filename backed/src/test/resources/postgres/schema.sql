-- 仅用于独立本地测试库；运行账号无建表、改表、改角色权限，图钉写入按阶段三授权，其余表保持最小授权。
CREATE TABLE public.city (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text NOT NULL UNIQUE CHECK (code ~ '^[0-9]{6}$'),
    name text NOT NULL CHECK (btrim(name) <> ''),
    basemap_path text NOT NULL CHECK (btrim(basemap_path) <> ''),
    west double precision NOT NULL,
    south double precision NOT NULL,
    east double precision NOT NULL,
    north double precision NOT NULL,
    CHECK (-180 <= west AND west < east AND east <= 180),
    CHECK (-90 <= south AND south < north AND north <= 90)
);
COMMENT ON COLUMN public.city.id IS '地市业务 UUID';
COMMENT ON COLUMN public.city.code IS '唯一六位行政区划代码';
COMMENT ON COLUMN public.city.name IS '地市显示名称';
COMMENT ON COLUMN public.city.basemap_path IS '地市 PMTiles 底图路径';
COMMENT ON COLUMN public.city.west IS 'WGS84 西边界经度';
COMMENT ON COLUMN public.city.south IS 'WGS84 南边界纬度';
COMMENT ON COLUMN public.city.east IS 'WGS84 东边界经度';
COMMENT ON COLUMN public.city.north IS 'WGS84 北边界纬度';

CREATE TABLE public.user_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text UNIQUE,
    phone text UNIQUE,
    password_hash text NOT NULL CHECK (btrim(password_hash) <> ''),
    role text NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'admin')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    avatar_path text CHECK (avatar_path IS NULL OR btrim(avatar_path) <> ''),
    status smallint NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CHECK (email IS NULL OR (email = lower(btrim(email)) AND char_length(email) BETWEEN 1 AND 254)),
    CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{1,14}$')
);
COMMENT ON COLUMN public.user_account.id IS '账号业务 UUID';
COMMENT ON COLUMN public.user_account.email IS '规范化邮箱登录标识，未验证所有权';
COMMENT ON COLUMN public.user_account.phone IS '含国家码的 E.164 手机号，未验证所有权';
COMMENT ON COLUMN public.user_account.password_hash IS 'Argon2id 完整密码编码';
COMMENT ON COLUMN public.user_account.role IS '产品角色 user 或 admin';
COMMENT ON COLUMN public.user_account.created_at IS '账号创建时间点';
COMMENT ON COLUMN public.user_account.avatar_path IS '可空的头像文件相对路径';
COMMENT ON COLUMN public.user_account.status IS '账号状态：1 正常，0 禁用';

CREATE TABLE public.auth_session (
    token_hash text PRIMARY KEY CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    user_id uuid NOT NULL REFERENCES public.user_account(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at)
);
CREATE INDEX ix_auth_session_user_id ON public.auth_session(user_id);
CREATE INDEX ix_auth_session_expires_at ON public.auth_session(expires_at);
COMMENT ON COLUMN public.auth_session.token_hash IS '随机令牌的 SHA-256 十六进制摘要';
COMMENT ON COLUMN public.auth_session.user_id IS '会话所属账号 UUID';
COMMENT ON COLUMN public.auth_session.created_at IS '会话创建时间点';
COMMENT ON COLUMN public.auth_session.expires_at IS '创建后七天的绝对过期时间点';

CREATE TABLE public.travel_map (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    city_id uuid NOT NULL REFERENCES public.city(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    owner_id uuid NOT NULL REFERENCES public.user_account(id) ON DELETE RESTRICT,
    visibility text NOT NULL DEFAULT 'private' CHECK (visibility IN ('private', 'public')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at)
);
CREATE INDEX ix_travel_map_city_id ON public.travel_map(city_id);
CREATE INDEX ix_travel_map_owner_city_updated ON public.travel_map(owner_id, city_id, updated_at DESC, id);
COMMENT ON COLUMN public.travel_map.id IS '地图业务 UUID';
COMMENT ON COLUMN public.travel_map.city_id IS '所属地市 UUID';
COMMENT ON COLUMN public.travel_map.name IS '地图显示名称';
COMMENT ON COLUMN public.travel_map.owner_id IS '地图所有者账号 UUID';
COMMENT ON COLUMN public.travel_map.visibility IS '公开状态 private 或 public';
COMMENT ON COLUMN public.travel_map.created_at IS '地图创建时间点';
COMMENT ON COLUMN public.travel_map.updated_at IS '实际成功修改名称或公开状态的最近时间点';

CREATE TABLE public.map_pin (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id uuid NOT NULL REFERENCES public.travel_map(id) ON DELETE RESTRICT,
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    longitude double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    latitude double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90)
);
CREATE INDEX ix_map_pin_map_id ON public.map_pin(map_id);
COMMENT ON COLUMN public.map_pin.id IS '图钉业务 UUID';
COMMENT ON COLUMN public.map_pin.map_id IS '所属地图 UUID';
COMMENT ON COLUMN public.map_pin.name IS '图钉显示名称';
COMMENT ON COLUMN public.map_pin.description IS '可空的图钉说明';
COMMENT ON COLUMN public.map_pin.longitude IS 'WGS84 经度';
COMMENT ON COLUMN public.map_pin.latitude IS 'WGS84 纬度';

CREATE ROLE guidepins_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
GRANT USAGE ON SCHEMA public TO guidepins_app;
GRANT SELECT ON public.city, public.user_account, public.auth_session, public.travel_map, public.map_pin TO guidepins_app;
GRANT INSERT (email, phone, password_hash) ON public.user_account TO guidepins_app;
GRANT INSERT (token_hash, user_id, created_at, expires_at), DELETE ON public.auth_session TO guidepins_app;
GRANT INSERT (city_id, owner_id, name, visibility), UPDATE (name, visibility, updated_at)
    ON public.travel_map TO guidepins_app;
-- 阶段三图钉编辑只放开 map_pin 的整表写入；权限条件仍由 SQL 语句校验。
GRANT INSERT, UPDATE, DELETE ON public.map_pin TO guidepins_app;
