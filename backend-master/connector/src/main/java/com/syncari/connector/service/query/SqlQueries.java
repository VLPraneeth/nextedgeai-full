package com.syncari.connector.service.query;

public class SqlQueries {
	public static final String DESCRIBE_ENTITY = "SELECT table_name, table_type FROM information_schema.tables "
			+ "WHERE table_schema = '%s' AND table_type in ('BASE TABLE','VIEW')  ORDER BY table_name;";

	public static final String DESCRIBE_LATE_BINDING_VIEWS = "SELECT * FROM pg_get_late_binding_view_cols() " +
			"cols(VIEW_SCHEMA name, VIEW_NAME name, COLUMN_NAME name, DATA_TYPE varchar, COLUMN_NUM int) WHERE VIEW_SCHEMA = '%s';";
	public static final String DESCRIBE_FIELD = "SELECT column_name,data_type,is_nullable,column_default,"
			+ "       case when character_maximum_length is not null"
			+ "            then character_maximum_length"
			+ "            else numeric_precision end as max_length,numeric_precision,numeric_scale FROM information_schema.columns "
			+ "WHERE table_name = '%s' AND table_schema = '%s';";
	public static final String INSERT = "INSERT INTO %s %s VALUES %s;";
	public static final String UPDATE_BY_ID = "UPDATE %s SET %s WHERE %s";
	public static final String DELETE = "DELETE FROM %s WHERE %s;";
	public static final String SELECT = "SELECT * FROM %s WHERE %s;";
	public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS %s (%s);";
	public static final String DROP_TABLE = "DROP TABLE IF EXISTS %s;";
	public static final String ADD_COLUMN = "ALTER TABLE %s ADD COLUMN %s;";
	public static final String RENAME_COLUMN = "ALTER TABLE %s RENAME COLUMN %s TO %s;";
	public static final String ALTER_LENGTH = "ALTER TABLE %s ALTER COLUMN %s TYPE VARCHAR(%s);";
    public static final String ALTER_TYPE = "ALTER TABLE %s ALTER COLUMN %s TYPE %s;";
    public static final String ALTER_NUMERIC = "ALTER TABLE %s ALTER COLUMN %s TYPE NUMERIC;";
	public static final String RENAME_TABLE = "ALTER TABLE %s RENAME TO %s;";
	public static final String DROP_COLUMN = "ALTER TABLE %s DROP COLUMN %s;";
    public static final String DROP_COLUMN_IF_EXISTS = "ALTER TABLE %s DROP COLUMN IF EXISTS %s;";
	public static final String SELECT_BY_IDS = "SELECT %s FROM %s WHERE %s IN (%s);";
	public static final String CREATE_GROUP = "CREATE GROUP %s;";
	public static final String SELECT_GROUP = "SELECT * FROM pg_catalog.pg_group where groname = '%s';";
	public static final String SELECT_USER = "SELECT * FROM pg_catalog.pg_user where usename = '%s';";
	public static final String SELECT_DBS = "SELECT datname FROM pg_database WHERE datistemplate = false;";
	public static final String ALTER_GROUP = "ALTER GROUP %s ADD USER %s;";
	public static final String DROP_GROUP = "DROP GROUP IF EXISTS %s;";
	public static final String GRANT_USAGE = "GRANT USAGE ON SCHEMA \"%s\" TO GROUP %s;";
	public static final String GRANT_DB_USAGE = "GRANT ALL ON DATABASE \"%s\" TO GROUP %s;";
	public static final String GRANT_SELECT = "GRANT SELECT ON ALL TABLES IN SCHEMA \"%s\" TO GROUP %s;";
	public static final String ALTER_DEFAULT = "ALTER DEFAULT PRIVILEGES IN SCHEMA \"%s\" GRANT SELECT ON TABLES TO GROUP %s;";
	public static final String REVOKE_CREATE = "REVOKE CREATE ON SCHEMA \"%s\" FROM GROUP %s;";
	public static final String CREATE_USER = "CREATE USER %s PASSWORD '%s';";
	public static final String DROP_USER = "DROP USER IF EXISTS %s;";
	public static final String CREATE_SCHEMA = "CREATE SCHEMA IF NOT EXISTS %s;";
	public static final String DROP_SCHEMA = "DROP SCHEMA IF EXISTS %s CASCADE;";
	public static final String CREATE_DB = "CREATE DATABASE %s;";
	public static final String DROP_DB = "DROP DATABASE IF EXISTS %s;";
	public static final String SELECT_BY_ORDERED_KEYS = "SELECT * FROM %s %s";
	public static final String SELECT_ALL = "SELECT %s FROM %s";
	public static final String TRUNCATE_TABLE = "TRUNCATE TABLE %s;";
	public static final String DROP_INDEX = "DROP INDEX IF EXISTS %s";
	public static final String GET_INDEXES = "SELECT t.relname AS table_name, "+
			"i.relname AS index_name, " +
			"a.attname AS column_name " +
			"FROM " +
			"pg_class t, " +
			"pg_class i, " +
			"pg_index ix, " +
			"pg_attribute a " +
			"WHERE " +
			"t.oid = ix.indrelid " +
			"and i.oid = ix.indexrelid " +
			"and a.attrelid = t.oid " +
			"and a.attnum = ANY(ix.indkey) " +
			"and t.relkind = 'r' " +
			"and t.relname = '%s';";
	public static final String GET_CONSTRAINTS = "select n.nspname as schema_name,\n" +
			"       t.relname as table_name,\n" +
			"       c.conname as constraint_name\n" +
			"from pg_constraint c\n" +
			"  join pg_class t on c.conrelid = t.oid\n" +
			"  join pg_namespace n on t.relnamespace = n.oid\n" +
			"where t.relname = '%s';";
	public static final String DROP_CONSTRAINT = "ALTER TABLE %s DROP CONSTRAINT %s;";
}
