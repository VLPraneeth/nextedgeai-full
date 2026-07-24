package com.syncari.analytics;

public class QueryConstant {

	public static String totalApiCallToday = "SELECT sum(recordCount) totalApiCallToday "
										+ "FROM `%s` WHERE syncariEntityApiName IS NOT NULL AND operation IS NOT NULL "
										+ "AND occuredDate BETWEEN @startDate AND @endDate;";
	
	public static String topSynapsesByWeek = "SELECT connectorName, sum(recordCount) recordCountSum "
	                                    + "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate "
	                                    + "GROUP BY connectorName ORDER BY recordCountSum DESC LIMIT 5; ";
	
	public static String syncThroughputByDay = "SELECT connectorName, occuredDate, sum(recordCount) recordCountSum "
	                                    + "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate %s %s "
	                                    + "GROUP BY connectorName, occuredDate ORDER BY occuredDate; ";

	public static String syncThroughputByHour = "SELECT connectorName, occuredDateHour, sum(recordCount) recordCountSum "
										+ "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate %s %s "
										+ "GROUP BY connectorName, occuredDateHour ORDER BY occuredDateHour; ";

	public static String syncLatencyByDay = "SELECT connectorName, direction, occuredDate, timeTaken "
										+ "FROM ( SELECT connectorName, direction, occuredDate, PERCENTILE_CONT(latency, 0.5) OVER(PARTITION BY connectorName, occuredDate) AS timeTaken "
										+ "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate ) "
										+ "GROUP BY connectorName, direction, occuredDate, timeTaken ORDER BY occuredDate; ";
	
	public static String syncLatencyByHour = "SELECT connectorName, direction, occuredDateHour, timeTaken "
										+ "FROM ( SELECT connectorName, direction, occuredDateHour, PERCENTILE_CONT(latency, 0.5) OVER(PARTITION BY connectorName, occuredDateHour) AS timeTaken "
										+ "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate ) "
										+ "GROUP BY connectorName, direction, occuredDateHour, timeTaken ORDER BY occuredDateHour; ";
	
	public static String syncErrors = "SELECT connectorId, connectorName, syncariEntityName, externalEntityName, syncariRecordId, externalRecordId, batchId, operation, errorCode, errorDetails, occuredTime "
            + "FROM `%s` WHERE occuredTime BETWEEN @startDate AND @endDate %s %s %s %s "
            + "ORDER BY occuredTime desc limit @limit offset @offset";

	public static String syncErrorsBySyncCycleAndError =  "SELECT connectorId, connectorName, syncariEntityName, externalEntityName, syncariRecordId, externalRecordId, batchId, operation, errorCode, errorCode, occuredTime "
										+ "FROM `%s` WHERE batchId = '%s' AND connectorId = '%s' AND externalEntityName = '%s' AND errorCode = '%s' "
										+ "ORDER BY occuredTime desc limit @limit offset @offset";


	public static String synapseUsage = "SELECT connectorName, operation, syncariEntityApiName, latency, occuredDateHour "
										+ "FROM `%s` WHERE connectorName IS NOT NULL AND operation IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate %s %s "
										+ "ORDER BY occuredTime; ";

	public static String synapseLatencyByDay = "SELECT connectorName, occuredDate, timeTaken "
										+ "FROM (SELECT connectorName, occuredDate, PERCENTILE_CONT(latency, 0.5) OVER(PARTITION BY connectorName, occuredDate) AS timeTaken "
										+ "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate %s ) "
										+ "GROUP BY connectorName, occuredDate, timeTaken ORDER BY occuredDate; ";
	
	public static String synapseLatencyByHour = "SELECT connectorName, occuredDateHour, timeTaken "
										+ "FROM ( SELECT connectorName, occuredDateHour, PERCENTILE_CONT(latency, 0.5) OVER(PARTITION BY connectorName, occuredDateHour) AS timeTaken "
										+ "FROM `%s` WHERE connectorName IS NOT NULL AND occuredDate BETWEEN @startDate AND @endDate %s ) "
										+ "GROUP BY connectorName, occuredDateHour, timeTaken ORDER BY occuredDateHour; ";

	public static String dedupeByDay = "SELECT sum(duplicateCount) AS duplicates, sum(dedupeCount) AS deduped, occuredDate "
										+ "FROM `%s` WHERE occuredDate BETWEEN @startDate AND @endDate AND stageName = '%s' AND stageType = 'sink' "
										+ "GROUP BY occuredDate ORDER BY occuredDate; ";

	public static String dedupeByHour = "SELECT sum(duplicateCount) AS duplicates, sum(dedupeCount) AS deduped, occuredDate "
										+ "FROM `%s` WHERE occuredDate BETWEEN @startDate AND @endDate AND stageName = '%s' AND stageType = 'sink' "
										+ "GROUP BY occuredDateHour ORDER BY occuredDateHour; ";
	
	public static String enrichedByDay = "SELECT sum(changeCount) AS enriched, occuredDate "
	                                    + "FROM `%s` WHERE occuredDate BETWEEN @startDate AND @endDate AND stageName = '%s' "
	                                    + "GROUP BY occuredDate ORDER BY occuredDate; ";
	
	public static String enrichedByHour = "SELECT sum(changeCount) AS enriched, occuredDate "
	                                    + "FROM `%s` WHERE occuredDate BETWEEN @startDate AND @endDate AND stageName = '%s' "
	                                    + "GROUP BY occuredDateHour ORDER BY occuredDateHour; ";
	
	public static String syncStreamErrors =  "SELECT connectorId, connectorName, syncariEntityName, externalEntityName, syncariRecordId, externalRecordId, batchId, operation, errorCode, errorCode, occuredTime "
			+ "FROM `%s` WHERE syncariEntityName = '%s' AND batchId = (SELECT batchId FROM `%s` WHERE syncariEntityName = '%s' ORDER BY occuredDate DESC LIMIT 1) ";


	public static String transactionCountByDateRange = "SELECT count(1) as transactionCount from `%s` where occurredTime between @startDate AND @endDate";
	public static String transactionCountByEntityNameAndDateRange = "SELECT count(1) as transactionCount from `%s` where occurredTime between @startDate AND @endDate AND entityName='%s'";
	public static String transactionCountNewByRange = "SELECT count(1) as transactionCount from `%s` where occurredTime between @startDate AND @endDate AND operation='create'";
	public static String transactionCountUpdateByRange = "SELECT count(1) as transactionCount from `%s` where occurredTime between @startDate AND @endDate AND operation in ('update','merge')";
	public static String transactionCountNewByEntityNameAndRange = "SELECT count(1) as transactionCount from `%s` where occurredTime between @startDate AND @endDate AND operation='create' AND entityName='%s'";
	public static String transactionCountUpdateByEntityNameAndRange = "SELECT count(1) as transactionCount from `%s` where occurredTime between @startDate AND @endDate AND operation in ('update','merge') AND entityName='%s'";
	public static String topActiveEntitiesWithCount = "SELECT count(1) transactionCount, entityName from `%s` where occurredTime between @startDate AND @endDate group by entityName";
	public static String mostActiveEntity = "SELECT count(1) AS transactionCount, entityName from `%s` where occurredTime between @startDate AND @endDate group by entityName order by 1 desc limit 1";
	public static String syncErrorCount = "SELECT count(1) as errorCount from `%s` where occuredTime between @startDate AND @endDate %s %s %s %s";

	/*
	public static String syncErrorsBySyncCycleAndError =  "SELECT connectorId, connectorName, syncariEntityName, externalEntityName, syncariRecordId, externalRecordId, batchId, operation, errorCode, errorDetails, occuredTime "
										+ "FROM `%s` WHERE batchId = '%s' AND connectorId = '%s' AND externalEntityName = '%s' AND errorDetails = '%s' "
										+ "ORDER BY occuredTime desc limit @limit offset @offset";
	 */
	public static String syncErrorCountBySyncCycleIdAndEntity = "SELECT count(1) as errorCount from `%s`  WHERE batchId = '%s' AND connectorId = '%s' AND externalEntityName = '%s' AND errorCode = '%s'";


}
