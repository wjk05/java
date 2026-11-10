-- 首次初始化数据库时执行（仅在数据卷为空时运行）
-- 数据库本身由 POSTGRES_DB 环境变量自动创建
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;