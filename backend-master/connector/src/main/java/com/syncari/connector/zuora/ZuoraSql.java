package com.syncari.connector.zuora;

public class ZuoraSql {
	public static final String QUERY_BY_IDS = "select %s from %s where id in (%s)";
	public static final String QUERY_BY_WATERMARK = "SELECT %s FROM %s WHERE "
			+ "UpdatedDate >= %s AND UpdatedDate <= %s ORDER BY UpdatedDate LIMIT %s";
	public static final String QUERY_BY_WATERMARK_NO_END = "SELECT %s FROM %s WHERE UpdatedDate >= %s ORDER BY UpdatedDate limit %s";
	public static final String QUERY_FIRST_RECORD = "select CreatedDate from %s ORDER BY CreatedDate limit 1";
}
