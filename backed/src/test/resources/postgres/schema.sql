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
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES public.user_account(id) ON DELETE CASCADE
);
CREATE INDEX ix_auth_session_user_id ON public.auth_session(user_id);
CREATE INDEX ix_auth_session_expires_at ON public.auth_session(expires_at);
COMMENT ON COLUMN public.auth_session.token_hash IS '随机令牌的 SHA-256 十六进制摘要';
COMMENT ON COLUMN public.auth_session.user_id IS '会话所属账号 UUID';
COMMENT ON COLUMN public.auth_session.created_at IS '会话创建时间点';
COMMENT ON COLUMN public.auth_session.expires_at IS '创建后七天的绝对过期时间点';

CREATE TABLE public.travel_map (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    city_id uuid NOT NULL,
    name text NOT NULL CHECK (btrim(name) <> ''),
    owner_id uuid NOT NULL,
    visibility text NOT NULL DEFAULT 'private' CHECK (visibility IN ('private', 'public')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at),
    CONSTRAINT fk_travel_map_city FOREIGN KEY (city_id) REFERENCES public.city(id) ON DELETE RESTRICT,
    CONSTRAINT fk_travel_map_owner FOREIGN KEY (owner_id) REFERENCES public.user_account(id) ON DELETE RESTRICT
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
    map_id uuid NOT NULL,
    name text NOT NULL CHECK (btrim(name) <> ''),
    description text,
    longitude double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    latitude double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT fk_map_pin_map FOREIGN KEY (map_id) REFERENCES public.travel_map(id) ON DELETE RESTRICT
);
CREATE INDEX ix_map_pin_map_id ON public.map_pin(map_id);
COMMENT ON COLUMN public.map_pin.id IS '图钉业务 UUID';
COMMENT ON COLUMN public.map_pin.map_id IS '所属地图 UUID';
COMMENT ON COLUMN public.map_pin.name IS '图钉显示名称';
COMMENT ON COLUMN public.map_pin.description IS '可空的图钉说明';
COMMENT ON COLUMN public.map_pin.longitude IS 'WGS84 经度';
COMMENT ON COLUMN public.map_pin.latitude IS 'WGS84 纬度';

-- 阶段三：攻略挂在地图上，一篇攻略可提到多个图钉，一个图钉可被多篇攻略提到。
CREATE TABLE public.guide (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    map_id uuid NOT NULL,
    origin text NOT NULL DEFAULT 'manual',
    title text NOT NULL,
    body text NOT NULL,
    source_url text,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT guide_pkey PRIMARY KEY (id),
    CONSTRAINT fk_guide_map FOREIGN KEY (map_id) REFERENCES public.travel_map(id) ON DELETE RESTRICT,
    CONSTRAINT ck_guide_origin CHECK (origin IN ('manual', 'agent')),
    CONSTRAINT ck_guide_title CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    CONSTRAINT ck_guide_body CHECK (char_length(btrim(body)) BETWEEN 1 AND 5000),
    CONSTRAINT ck_guide_source_url CHECK (source_url IS NULL
        OR (char_length(source_url) <= 2048 AND source_url ~ '^https?://[^[:space:]]+$')),
    CONSTRAINT ck_guide_timestamps CHECK (updated_at >= created_at)
);
CREATE INDEX ix_guide_map_id ON public.guide(map_id, created_at, id);
COMMENT ON COLUMN public.guide.id IS '攻略条目业务 UUID';
COMMENT ON COLUMN public.guide.map_id IS '所属地图 UUID，外键关联 travel_map.id，删地图前必须先删攻略';
COMMENT ON COLUMN public.guide.origin IS '攻略来源：manual 手动录入，agent 由外部链接抓取生成';
COMMENT ON COLUMN public.guide.title IS '攻略标题或摘录名，1 至 120 字符';
COMMENT ON COLUMN public.guide.body IS '攻略正文或摘录，1 至 5000 字符';
COMMENT ON COLUMN public.guide.source_url IS '可选的来源链接，必须以 http:// 或 https:// 开头';
COMMENT ON COLUMN public.guide.created_at IS '攻略录入时间点';
COMMENT ON COLUMN public.guide.updated_at IS '攻略内容最近一次实际修改成功的时点';

CREATE TABLE public.pin_guide (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    guide_id uuid NOT NULL,
    pin_id uuid NOT NULL,
    position integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pin_guide_pkey PRIMARY KEY (id),
    CONSTRAINT fk_pin_guide_guide FOREIGN KEY (guide_id) REFERENCES public.guide(id) ON DELETE CASCADE,
    CONSTRAINT fk_pin_guide_pin FOREIGN KEY (pin_id) REFERENCES public.map_pin(id) ON DELETE CASCADE,
    CONSTRAINT uq_pin_guide_guide_pin UNIQUE (guide_id, pin_id),
    CONSTRAINT ck_pin_guide_position CHECK (position >= 0)
);
CREATE INDEX ix_pin_guide_guide_position ON public.pin_guide(guide_id, "position", id);
CREATE INDEX ix_pin_guide_pin_id ON public.pin_guide(pin_id);
COMMENT ON COLUMN public.pin_guide.id IS '攻略与图钉关联行业务 UUID';
COMMENT ON COLUMN public.pin_guide.guide_id IS '所关联的攻略 UUID，外键关联 guide.id，删攻略时级联删除';
COMMENT ON COLUMN public.pin_guide.pin_id IS '被提到的图钉 UUID，外键关联 map_pin.id，删图钉时级联删除';
COMMENT ON COLUMN public.pin_guide.position IS '该图钉在本篇攻略中的站序，从 0 开始，拖动排序时整体重写';
COMMENT ON COLUMN public.pin_guide.created_at IS '关联建立时间点';

CREATE ROLE guidepins_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
GRANT USAGE ON SCHEMA public TO guidepins_app;
GRANT SELECT ON public.city, public.user_account, public.auth_session, public.travel_map, public.map_pin TO guidepins_app;
GRANT INSERT (email, phone, password_hash) ON public.user_account TO guidepins_app;
GRANT INSERT (token_hash, user_id, created_at, expires_at), DELETE ON public.auth_session TO guidepins_app;
GRANT INSERT (city_id, owner_id, name, visibility), UPDATE (name, visibility, updated_at)
    ON public.travel_map TO guidepins_app;
-- 阶段三图钉编辑只放开 map_pin 的整表写入；权限条件仍由 SQL 语句校验。
GRANT INSERT, UPDATE, DELETE ON public.map_pin TO guidepins_app;
-- 阶段三攻略与出处同样整表读写，可见性与写权限仍由 SQL 语句校验。
GRANT SELECT, INSERT, UPDATE, DELETE ON public.guide, public.pin_guide TO guidepins_app;
