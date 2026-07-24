package com.syncari.connector;

public enum Capability {
	create,
	update,
	delete,
	search,
	getById,
	getByWatermark,
	noWatermark,
	compositeId,
	schemaEditInSyncari,
	userEditableId,
	userEditableWm,
	schemaCreateField,
	userEditableReadOnly,
}
