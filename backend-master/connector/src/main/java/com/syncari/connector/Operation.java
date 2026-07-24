package com.syncari.connector;

public enum Operation {
	create,
	update,
	delete,
	external_delete,
	external_create,
	external_update,
	disconnect,
	connect,
	syncari_delete,
	merge,
	convert,
	get,
	general,
	merge_report_only,
	purge,
	merge_skip;
  
  public static boolean isEqual(Operation op, String operation) {
    return op.name().equalsIgnoreCase(operation);
  }
}
