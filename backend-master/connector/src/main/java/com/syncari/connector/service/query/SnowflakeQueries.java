package com.syncari.connector.service.query;

public class SnowflakeQueries {
	public static final String DESCRIBE_ENTITY = "SELECT TABLE_NAME, TABLE_TYPE FROM information_schema.tables "
			+ "WHERE table_schema = '%s' AND TABLE_TYPE in ('BASE TABLE','VIEW') ORDER BY TABLE_NAME;";
	public static final String DESCRIBE_FIELD = "SELECT column_name,data_type,is_nullable,column_default,"
			+ "       case when character_maximum_length is not null"
			+ "            then character_maximum_length"
			+ "            else numeric_precision end as max_length,numeric_precision,numeric_scale FROM information_schema.columns "
			+ "WHERE table_name = '%s' AND table_schema = '%s';";
	public static final String SELECT_BY_IDS = "SELECT %s FROM %s WHERE %s IN (%s);";
	public static final String SHOW_IMPORTED_KEYS = "SHOW IMPORTED KEYS in table \"%s\".\"%s\".\"%s\"";

}
