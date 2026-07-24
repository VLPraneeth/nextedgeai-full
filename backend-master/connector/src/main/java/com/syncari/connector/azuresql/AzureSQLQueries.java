package com.syncari.connector.azuresql;

public class AzureSQLQueries {

    //PLEASE USE PREPARED STATEMENTS EVERYWHERE!!! No string replacements!
    public static final String GET_TABLES = "select sys.columns.*,sys.types.name as datatype, sys.tables.name as table_name," +
            "             sys.schemas.name as schema_name  from sys.columns" +
            "             join sys.tables on sys.columns.object_id=sys.tables.object_id" +
            "             join sys.schemas on sys.schemas.schema_id =sys.tables.schema_id" +
            "             join sys.types on sys.types.user_type_id=sys.columns.user_type_id" +
            "             where sys.schemas.name=?";
    public static final String DESCRIBE_ENTITY = "select sys.columns.*,sys.types.name as datatype, sys.tables.name as table_name," +
            "             sys.schemas.name as schema_name  from sys.columns" +
            "             join sys.tables on sys.columns.object_id=sys.tables.object_id" +
            "             join sys.schemas on sys.schemas.schema_id =sys.tables.schema_id" +
            "             join sys.types on sys.types.user_type_id=sys.columns.user_type_id" +
            "             where sys.schemas.name='%s' and sys.tables.name='%s'";

    public static final String SELECT_BY_IDS = "SELECT * FROM %s WHERE %s IN (%s);";

    public static final String SELECT_BY_IDS_COMPOSITE = "SELECT * FROM %s WHERE %s;";

    public static final String SELECT = "SELECT * FROM %s %s;";

}
