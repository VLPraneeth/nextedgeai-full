package com.syncari.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.Settings;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.StringType;
import com.syncari.core.event.EventTypes;
import com.syncari.core.event.Message;
import com.syncari.core.event.Publisher;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.FileManagerFactory;
import com.syncari.core.functions.LookupReferenceDataFunction;
import com.syncari.core.model.Event;
import com.syncari.core.model.Notification;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.*;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.repositories.customer.ReferenceDataMetaRepo;
import com.syncari.core.repositories.syncari.SyncariReferenceDataMetaRepo;
import com.syncari.core.utils.CustomerMongoUtils;
import com.syncari.core.utils.MongoUtils;
import com.syncari.core.utils.ReferenceLookupCriteriaVisitor;
import com.syncari.core.utils.SyncariMongoUtils;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.I18n;
import com.syncari.utils.file.FileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Ref;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;
import static java.lang.String.format;

@Slf4j
@Service
public class ReferenceDataService {
	@Autowired
	private ReferenceDataMetaRepo refDataMetaRepo;
	@Autowired
	private SyncariReferenceDataMetaRepo syncariRefDataMetaRepo;
	@Autowired
	CsvUtils csvUtil;
	@Autowired
	CustomerMongoUtils customerMongoUtils;
	@Autowired
	SyncariMongoUtils syncariMongoUtils;
	@Autowired
	Publisher publisher;
	@Autowired
	EventService eventService;
	@Autowired
	FileManagerFactory fileManagerFactory;
	@Autowired
	NotificationService notificationService;
	@Autowired
	UserService userService;
	@Autowired
	ObjectMapper mapper;
	@Autowired
	ComponentDependencyService dependencyService;
	@Autowired
	FeatureService featureService;

	// instance id + data set id + lookup key +
	private final LoadingCache<String, Optional<ReferenceDataMeta>> referenceMetadataCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS)
			.build(new CacheLoader<String, Optional<ReferenceDataMeta>>() {
		@Override
		public Optional<ReferenceDataMeta> load(String s) throws Exception {
			//
			String[] parts = s.split("_");
			if (parts.length < 2) {
				throw new RuntimeException("Invalid cache key");
			}
			return getReferenceMetadata(parts[parts.length-1]);
		}
	});

	// instance id + data set id + lookup key + destination field
	private LoadingCache<String, Map<String,String>> lookupCache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).build(new CacheLoader<String, Map<String,String>>() {
		@Override
		public Map<String,String> load(String s) throws Exception {
			//
			String[] parts = s.split("#");
			if (parts.length != 6) {
				log.error("Invalid Lookup Reference cache key {}", s);
				return null;
			}

			String datasetId = parts[1];
			String lookupKey = parts[2];
			String destinationField = parts[3];
			boolean ignoreCase = Boolean.parseBoolean(parts[4]);
			String operator = parts[5];


			var meta = referenceMetadataCache.get(SyncariContext.getSyncariId() + "_" + datasetId).orElseThrow(() -> new RuntimeException("Invalid dataset id"));
			var mongoUtils = meta.getSource().getType() == ReferenceDataSourceType.syncari ? syncariMongoUtils : customerMongoUtils;

			int pageSize = 500;
			Optional<String> cursor = Optional.empty();
			Map<String, String> lookupValues = new LinkedHashMap<>();
			List<Map<String, String>> results;
			do {
				results = mongoUtils.readMany(getCollectionName(meta), pageSize, cursor);
				Map<String, String> tmpMap = results.stream().filter(r -> r.containsKey(lookupKey) && r.containsKey(destinationField)).collect(Collectors.toMap(r -> ignoreCase || operator.equals(LookupReferenceDataFunction.CONTAINS) || operator.equals(LookupReferenceDataFunction.IN)
						? r.get(lookupKey).toLowerCase(Locale.ROOT) : r.get(lookupKey), r -> r.get(destinationField), (a, b) -> a, LinkedHashMap::new));
				// this maintains insertion order across pages in case of duplicate keys
				tmpMap.putAll(lookupValues);
				lookupValues = tmpMap;
				cursor = !results.isEmpty() ? Optional.of(results.get(results.size() -1).get("_id")) : Optional.empty();
			} while (results.size() > 0);
			return lookupValues;
		}
	});


	private LoadingCache<String, Optional<ReferenceDataMeta>> lookupRefDataCache = CacheBuilder.newBuilder().build(new CacheLoader<String, Optional<ReferenceDataMeta>>() {
		@Override
		public Optional<ReferenceDataMeta> load(String s) throws Exception {
			//
			String[] parts = s.split("_");
			if (parts.length != 2) {
				throw new RuntimeException("Invalid cache key");
			}
			return getReferenceMetadata(parts[1]);
		}
	});

	public static final String BOM_CHAR = "\uFEFF";

	public static final int INMEMORY_REFERENCE_DATA_LIMIT = 20000;

	public List<ReferenceDataMeta> listMeta(int pageNumber) {
		PageRequest page = PageRequest.of(pageNumber, Settings.pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
		return Stream.concat(syncariRefDataMetaRepo.findAll(page).getContent().stream(),
				refDataMetaRepo.findAll(page).getContent().stream()).collect(Collectors.toList());
	}

	// TODO change this signature. The duplicate is needed to avoid copying the file
	public ReferenceDataMeta createMeta(ReferenceDataMeta dataset,
										 InputStream inputStream,
										 InputStream inputStream1,
										 boolean sendImport) {
		validateDataMeta(dataset);
		dataset.setStatus(DataImportStatus.NEW);
		try {
			List<String> headers = validate(dataset, inputStream);
			headers.stream().forEach(h -> dataset.getFields().put(h, new StringType()));
			getFileManager(dataset).uploadFile(inputStream1, dataset.getSource().getLocation());
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
		ReferenceDataMeta saved = dataset.getSource().getType() == ReferenceDataSourceType.syncari?
				syncariRefDataMetaRepo.save(dataset):refDataMetaRepo.save(dataset);
		if(sendImport)
			sendImportRequest(saved);
		log.info(format("Successfully created dataset %s", dataset.getName()));
		return saved;
	}

	private void validateDataMeta(ReferenceDataMeta dataset) {
		if (dataset == null)
			throw new RuntimeException(i18n("invalid_dataset_null"));
		if(SyncariContext.getInstance() != null) {
			validateCondition(findReferenceDataByName(dataset.getName()).isPresent(), i18n("duplicate_dataset_name", dataset.getName()));
		}
	}

	public ReferenceDataMeta getReferenceData(String refMetaId) {
		return findReferenceData(refMetaId)
				.orElseThrow(() -> new NotFoundException(ReferenceDataMeta.class, "id", refMetaId));
	}

	public Optional<ReferenceDataMeta> findReferenceData(String refMetaId){
		return syncariRefDataMetaRepo.findById(refMetaId).or(() -> refDataMetaRepo.findById(refMetaId));
	}

	public List<ReferenceDataMeta> findAll(){
		return syncariRefDataMetaRepo.findAll();
	}

	public Optional<ReferenceDataMeta> findReferenceDataByName(String name){
	    return syncariRefDataMetaRepo.findByName(name).or(() -> refDataMetaRepo.findByName(name));
	}

	public InputStream getFile(String refMetaId) throws IOException {
	    ReferenceDataMeta dataset = getReferenceData(refMetaId);
	    FileManager fileManager = getFileManager(dataset);
	    return fileManager.readFile(dataset.getSource().getLocation());
	}

	public List<ReferenceDataMeta> updateMeta(List<ReferenceDataMeta> refDataMetaList) {
		return refDataMetaRepo.saveAll(refDataMetaList);
	}

	public ReferenceDataMeta updateMeta(ReferenceDataMeta dataset, InputStream inputStream, InputStream inputStream1) {
		return updateMeta(dataset, inputStream, inputStream1, true);
	}
	// TODO change this signature. The duplicate is needed to avoid copying the file
	public ReferenceDataMeta updateMeta(ReferenceDataMeta dataset, InputStream inputStream, InputStream inputStream1, boolean sendImportRequest) {
	    if (dataset == null)
	        throw new RuntimeException("Dataset cannot be null");
	    log.info("Setting import status to processing!");
	    dataset.setStatus(DataImportStatus.PROCESSING);
	    try {
	        List<String> headers = validate(dataset, inputStream);
	        Map<String, Datatype> datasetFields = dataset.getFields();
	        Map<String, Datatype> headersMap = new LinkedHashMap<String, Datatype>();
			headers.stream().forEach(h -> headersMap.put(removeBOM(h), new StringType()));
	        Collection<String> datasetKeys = datasetFields.keySet().stream().map(k -> removeBOM(k)).collect(Collectors.toSet());
	        Collection<String> headerKeys = headersMap.keySet();
	        Boolean isSubCollection = CollectionUtils.isSubCollection(datasetKeys, headerKeys);
	        if (!isSubCollection) {
				log.info("Datasetkeys in metadata are {} and headerKeys from CSV are {}",datasetKeys, headerKeys);
				Collection filteredFields = CollectionUtils.removeAll(datasetKeys, headerKeys);
	            throw new RuntimeException(format("The column(s) %s are missing in the uploaded CSV file", filteredFields.toString()));
	        }
	        // use existing column name if already exists
			// This is needed because field can contain BOM char and if same field is used in lookupRefData function we don't want to break the search
			Map<String, Datatype> updatedColumns = new LinkedHashMap<String, Datatype>(datasetFields);
			headersMap.forEach((k, v) -> {
	        	if(!datasetKeys.contains(k)){
	        		updatedColumns.put(k, v);
				}
	        });
	        dataset.setFields(updatedColumns);
	        getFileManager(dataset).uploadFile(inputStream1, dataset.getSource().getLocation());
	    } catch (IOException e) {
	        throw new RuntimeException(e.getMessage());
	    }
		ReferenceDataMeta saved = dataset.getSource().getType() == ReferenceDataSourceType.syncari?
				syncariRefDataMetaRepo.save(dataset): refDataMetaRepo.save(dataset);
		if (sendImportRequest) {
			sendImportRequest(saved);
		}
	    log.info(format("Successfully created dataset for updation %s", dataset.getName()));
	    return saved;
	}
	
	public long deleteItems(String datasetId, List<String> ids) {
		Optional<ReferenceDataMeta> meta = findDataset(datasetId);
		if (meta.isPresent() && ids != null && !ids.isEmpty()) {
			return customerMongoUtils.deleteMany(getCollectionName(meta.get()), ids);
		}
		return 0;
	}
	
	public List<String> addItems(String datasetId, List<Map<String, Object>> rows) {
		Optional<ReferenceDataMeta> meta = findDataset(datasetId);
		if (meta.isPresent() && rows != null && !rows.isEmpty()) {
			return customerMongoUtils.insertMany(getCollectionName(meta.get()), rows);
		}
		return List.of();
	}
	/*
	This needs to be transactional
	 */
	public List<String> replaceItems(String datasetId, List<Map<String, Object>> rows) {
		Optional<ReferenceDataMeta> meta = findDataset(datasetId);
		if (meta.isPresent() && rows != null && !rows.isEmpty()) {
			customerMongoUtils.deleteAll(getCollectionName(meta.get()));
			return customerMongoUtils.insertMany(getCollectionName(meta.get()), rows);
		}
		return List.of();
	}

	public long updateItems(String datasetId, Map<String, Map<String, Object>> rows) {
		Optional<ReferenceDataMeta> meta = findDataset(datasetId);
		if (meta.isPresent() && rows != null && !rows.isEmpty()) {
			return customerMongoUtils.updateMany(getCollectionName(meta.get()), rows);
		}
		return 0;
	}
	
	private Optional<ReferenceDataMeta> findDataset(String datasetId) {
		if (StringUtils.isBlank(datasetId)) {
			throw new SyncariValidationException("Reference data id is required");
		}
		Optional<ReferenceDataMeta> meta = syncariRefDataMetaRepo.findById(datasetId);
		if (!meta.isPresent()) {
			meta = refDataMetaRepo.findById(datasetId);
			if (!meta.isPresent()) {
				log.error("Reference data meta with id {} not found", datasetId);
				throw new SyncariValidationException("Reference data meta not found");
			}
			if(meta.get().getSource().getType() == ReferenceDataSourceType.syncari) {
				throw new SyncariValidationException("Syncari Reference data meta cannot be edited");
			}
		}
		return meta;
	}

	private static String removeBOM(String s) {
		if (s.startsWith(BOM_CHAR)) {
			s = s.substring(1);
		}
		return s;
	}

	public ReferenceDataMeta extract(String refMetaId, boolean sendNotification) {
		Optional<ReferenceDataMeta> metaoption = syncariRefDataMetaRepo.findById(refMetaId);
		if (!metaoption.isPresent()) {
			metaoption = refDataMetaRepo.findById(refMetaId);
			if (!metaoption.isPresent()) {
				log.error(format("Reference data meta with id not found", refMetaId));
				return null;
			}
		}
		ReferenceDataMeta meta = metaoption.get();
		ReferenceDataSourceType type = meta.getSource().getType();

		boolean update = false;
		String currentCollection = getCollectionName(meta);
		String newCollection = null;
		if (meta.getStatus() != DataImportStatus.NEW) {
			update = true;
			newCollection = renameCustomerCollection(currentCollection,type.toString());
			log.info("Updating ref dataset: Old collection {}, New collection {}", currentCollection, newCollection);
		}
		meta.setStatus(DataImportStatus.PROCESSING);
		meta = type == ReferenceDataSourceType.syncari?
				syncariRefDataMetaRepo.save(meta): refDataMetaRepo.save(meta);
		log.info(format("Starting extraction of %s", refMetaId));

		long totalRecords = 0;
		try (InputStream in = getFileManager(meta).readFile(meta.getSource().getLocation())) {
			totalRecords = importData(newCollection == null ? currentCollection : newCollection, in,
					type == ReferenceDataSourceType.syncari?syncariMongoUtils:customerMongoUtils);
			meta.setStatus(DataImportStatus.ACTIVE);
			meta.setUpdatedAt(new Date());
			meta.setTotalRecords(totalRecords);
			if (update && !StringUtils.isBlank(newCollection)) {
				meta.setDatasetCollectionName(newCollection);
				log.info("Updated meta to point to renamed collection {}", newCollection);
			} else {
				meta.setDatasetCollectionName(currentCollection);
			}
			meta = type == ReferenceDataSourceType.syncari?
					syncariRefDataMetaRepo.save(meta): refDataMetaRepo.save(meta);
			if (update && !StringUtils.isBlank(newCollection)) {
				if(meta.getSource().getType() == ReferenceDataSourceType.syncari)
					syncariMongoUtils.dropCollection(currentCollection);
				else
					customerMongoUtils.dropCollection(currentCollection);
			}
			if(sendNotification)
				sendNotification(meta.getName(), meta.getTotalRecords(), "Import Completed");
			log.info(format("Successfully extracted %s", refMetaId));
		} catch (Exception e) {
			String stackTrace = ExceptionUtils.getStackTrace(e);
			meta.setStatus(DataImportStatus.ERROR);
			meta.setImportDetails(stackTrace);
			meta = type == ReferenceDataSourceType.syncari?
					syncariRefDataMetaRepo.save(meta): refDataMetaRepo.save(meta);
			log.info(format("Error while extracting %s, details: %s", refMetaId, stackTrace));
		}
		return meta;
	}

	private long importData(String collectionName, InputStream in, MongoUtils mongoUtils) throws IOException {
		long totalRecords = 0;
		try (CSVParser parser = csvUtil.getCSVParser(in)) {
			int i = 0;
			List<Map<String, Object>> rows = new ArrayList<>();
			for (CSVRecord record : parser) {
				Map<String, String> map = record.toMap();
				Map<String, Object> data = new HashMap<String, Object>();
				data.putAll(map);
				rows.add(data);
				i++;
				totalRecords++;
				if (i >= 100) {
					mongoUtils.insertMany(collectionName, rows);
					rows = new ArrayList<>();
					i = 0;
				}
			}
			if (i < 100 && !rows.isEmpty()) {
				mongoUtils.insertMany(collectionName, rows);
			}
		}
		return totalRecords;
	}

	public ReferenceDataMeta deactivateMeta(String refMetaId) {
		ReferenceDataMeta meta = refDataMetaRepo.findById(refMetaId)
				.orElseThrow(() -> new RuntimeException("Reference data meta not found"));
		meta.setStatus(DataImportStatus.INACTIVE);
		return refDataMetaRepo.save(meta);
	}

	public void deleteMeta(String refMetaId) {
		List<String> pipelineIds = dependencyService.findDependencies(refMetaId,
				ComponentType.referencedata, ComponentType.pipeline);
		if(!pipelineIds.isEmpty()) {
			throw new SyncariValidationException(I18n.i18n("cannot_delete_referenced_dataset"));
		}
		ReferenceDataMeta meta = getReferenceData(refMetaId);
		ReferenceDataSourceType type = meta.getSource().getType();
		Map<String, Object> body = Map.of("Dataset id", meta.getId(), "Dataset Name", meta.getName(), "Dataset Source",
				meta.getSource().getType().name());
		Event event = new Event().setType(EventTypes.DELETE_REFERENCE_DATA).setClient("application").setDetails(body)
				.setComponent("referenceData").setOccuredTime(new Date());
		switch(type){
			case syncari:
				syncariRefDataMetaRepo.delete(meta);
				break;
			default:
				refDataMetaRepo.delete(meta);
				break;
		}
		try {
			getFileManager(meta).deleteFile(meta.getSource().getLocation());
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
		String collectionName = getCollectionName(meta);
		if(meta.getSource().getType() == ReferenceDataSourceType.syncari)
			syncariMongoUtils.dropCollection(collectionName);
		else
			customerMongoUtils.dropCollection(collectionName);
		eventService.log(event);
	}

	public ReferenceData previewData(String refMetaId, int numberOfRows) {
		ReferenceDataMeta meta = getReferenceData(refMetaId);
		// If the import is complete, preview from mongo, else preview from original
		// file
		List<String> headerColumns = List.copyOf(meta.getFields().keySet());
		if (meta.getStatus() == DataImportStatus.ACTIVE) {
			List<Map<String, String>> values =
					meta.getSource().getType() == ReferenceDataSourceType.syncari ?
					syncariMongoUtils.readMany(getCollectionName(meta), numberOfRows, Optional.empty()):
					customerMongoUtils.readMany(getCollectionName(meta), numberOfRows, Optional.empty());
			List<List<String>> result = new ArrayList<List<String>>();
			for (Map<String, String> map : values) {
				List<String> row = new ArrayList<>();
				headerColumns.forEach(h -> {
					row.add(map.get(h));
				});
				result.add(row);
			}
			return new ReferenceData(headerColumns, result);
		} else {
			try {
				InputStream stream = getFileManager(meta).readFile(meta.getSource().getLocation());
				List<List<String>> values = csvUtil.getRows(stream, numberOfRows, new CSVOptions());
				return new ReferenceData(headerColumns, values);
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage());
			}
		}
	}
	
	public List<Map<String,String>> query(String refMetaId, String cursor, int numberOfRows) {
		ReferenceDataMeta meta = getReferenceData(refMetaId);
		return meta.getSource().getType() == ReferenceDataSourceType.syncari ?
				syncariMongoUtils.readMany(getCollectionName(meta), numberOfRows, Optional.ofNullable(cursor)):
				customerMongoUtils.readMany(getCollectionName(meta), numberOfRows, Optional.ofNullable(cursor));
	}

	public long countActiveRefData(){
		return refDataMetaRepo.countByStatus("ACTIVE");
	}

  	public ReferenceData previewData(String refMetaId) {
  	return previewData(refMetaId, 25);
  }


  	public Optional<ReferenceDataMeta> getReferenceMetadata(String datasetId, Map<String, Object> metadataCache) {
		metadataCache.computeIfAbsent("dataset_" + datasetId, k -> Optional.of(getReferenceData(datasetId)));
		return (Optional<ReferenceDataMeta>) metadataCache.get("dataset_" + datasetId);
	}

	public Optional<ReferenceDataMeta> getReferenceMetadata(String datasetId) {
		Optional<ReferenceDataMeta> meta = syncariRefDataMetaRepo.findById(datasetId);
		if (!meta.isPresent()) {
			meta = refDataMetaRepo.findById(datasetId);
		}
		return meta;
	}

	public long count(String datasetId, String lookupKey, Object lookupFieldValue, boolean ignoreCase) {
		Optional<ReferenceDataMeta> syncariMetaOpt = this.findReferenceDataByName(datasetId);
		Optional<ReferenceDataMeta> metaOpt = syncariMetaOpt.isEmpty() ? getReferenceMetadata(datasetId) : syncariMetaOpt;
		if (metaOpt.isEmpty()) {
			log.error(
					"Reference data meta with id {} not found. lookupKey: {}, lookupFieldValue: {}",
					datasetId, lookupKey, lookupFieldValue);
			throw new RuntimeException("Reference data meta not found");
		}
		String lookUpValue = lookupFieldValue != null ? String.valueOf(lookupFieldValue) : "";
		var meta = metaOpt.get();
		var mongoUtils = meta.getSource().getType() == ReferenceDataSourceType.syncari ? syncariMongoUtils : customerMongoUtils;
		Map<String, Object> searchFilters = new HashMap<>();
		searchFilters.put(lookupKey, ignoreCase ? new Document("$regex", Pattern.quote(lookUpValue)).append("$options", "i") : lookUpValue);
        return mongoUtils.count(getCollectionName(meta), Optional.of(new Document(searchFilters)), ignoreCase);
	}

	public Object lookUp(String datasetId, String lookupKey, String lookupFieldValue, String destinationFieldName, String operator, boolean ignoreCase, Map<String, Object> metadataCache) {

		Optional<ReferenceDataMeta> meta = getReferenceMetadata(datasetId, metadataCache);
		if (!meta.isPresent()) {
			log.error(
					"Reference data meta with id {} not found. lookupKey: {}, lookupFieldValue: {}, destinationFieldName: {}",
					datasetId, lookupKey, lookupFieldValue, destinationFieldName);
			throw new RuntimeException("Reference data meta not found");
		}
		Object retValue = null;
		if (meta.isPresent() && !StringUtils.isBlank(lookupKey) && !StringUtils.isBlank(lookupFieldValue)) {
			Function<Document, Map<String,String>> toMap = document -> {
				Map<String, String> row = new HashMap<String, String>();
				for (Map.Entry<String, Object> entry : document.entrySet()) {
					if("_id".equalsIgnoreCase(entry.getKey())) continue;
					row.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
				}
				return row;
			};

			// lookup db or memory

			metadataCache.computeIfAbsent("lookupRef_dataset_count_" + datasetId, (key) -> getDatasetSize(meta.get()));
			long datasetSize = (long) metadataCache.get("lookupRef_dataset_count_" + datasetId);

			if (datasetSize <= INMEMORY_REFERENCE_DATA_LIMIT) {
				retValue = lookupInMemory(meta.get(), lookupKey, lookupFieldValue, destinationFieldName, operator, ignoreCase);
			} else {
				retValue = lookupInDB(meta.get(), lookupKey, lookupFieldValue, destinationFieldName, operator, ignoreCase, toMap);
			}
		}

		if (retValue == null) {
			log.debug(format(
					"Empty value returned for datasetId: %s, lookupKey: %s, lookupFieldValue: %s, destinationFieldName: %s",
					datasetId, lookupKey, lookupFieldValue, destinationFieldName));
		}
		return retValue;
	}

	private Object lookupInDB(ReferenceDataMeta meta, String lookupKey, String lookupFieldValue, String destinationFieldName, String operator, boolean ignoreCase, Function<Document, Map<String,String>> toMap) {

		try {
			Expression expression;
			switch (operator) {
				case LookupReferenceDataFunction.CONTAINS:
					expression = Expression.contains(Expression.var(lookupKey), Expression.lit(lookupFieldValue));
					break;
				case LookupReferenceDataFunction.IN:
					expression = Expression.in(Expression.var(lookupKey), Expression.lit(lookupFieldValue));
					break;
				default:
					expression = !ignoreCase ? Expression.eq(Expression.var(lookupKey), Expression.lit(lookupFieldValue)) :
							Expression.ieq(Expression.var(lookupKey), Expression.lit(lookupFieldValue));
			}

			var mongoUtils = meta.getSource().getType() == ReferenceDataSourceType.syncari ? syncariMongoUtils : customerMongoUtils;

			var visitor = new ReferenceLookupCriteriaVisitor(expression);
			var criteria = visitor.createCriteria();

			List<Map<String, String>> rows = mongoUtils.searchPaged(getCollectionName(meta), Optional.ofNullable(criteria), new Document("_id", 1), toMap, 1, ignoreCase);
			for (Map<String, String> cols : rows) {
				return cols.get(destinationFieldName);
			}
		} catch (Exception e) {
			log.error("Error while looking up reference data", e);
		}
		return null;
	}

	private Object lookupInMemory(ReferenceDataMeta referenceDataMeta, String lookupKey, String lookupFieldValue, String destinationFieldName, String operator, boolean ignoreCase) {

		String lookupCacheKey = String.format("%s#%s#%s#%s#%b#%s", SyncariContext.getSyncariId(), referenceDataMeta.getId(), lookupKey, destinationFieldName, ignoreCase, operator);
		var referenceMetadataMaybe = referenceMetadataCache.getUnchecked(SyncariContext.getSyncariId() + "_" + referenceDataMeta.getId());
		if (referenceMetadataMaybe.isPresent()) {
			ReferenceDataMeta cachedMetadata = referenceMetadataMaybe.get();
			if (cachedMetadata.getUpdatedAt().before(referenceDataMeta.getUpdatedAt())) {
				// invalidate lookup cache
				log.info("Invalidating lookup reference cache for dataset: {}", cachedMetadata.getId());
				lookupCache.invalidate(lookupCacheKey);
				referenceMetadataCache.put(SyncariContext.getSyncariId() + "_" + referenceDataMeta.getId(), Optional.of(referenceDataMeta));
			}
			return lookup(lookupCache.getUnchecked(lookupCacheKey), lookupFieldValue, operator, ignoreCase);
		}
		return null;
	}

	private Object lookup(Map<String, String> lookupRefData, String lookupFieldValue, String operator, boolean ignoreCase) {
		final String nullSafeValue = lookupFieldValue == null ? "" : lookupFieldValue;

		switch (operator) {
			case LookupReferenceDataFunction.EXACTMATCH:
				return ignoreCase ? lookupRefData.get(nullSafeValue.toLowerCase(Locale.ROOT)) : lookupRefData.get(nullSafeValue);
			case LookupReferenceDataFunction.CONTAINS:
				return lookupRefData.entrySet().stream().filter(entry -> entry.getKey().contains(nullSafeValue.toLowerCase(Locale.ROOT))).findFirst().map(Map.Entry::getValue).orElse(null);
			case LookupReferenceDataFunction.IN:
				return lookupRefData.entrySet().stream().filter(entry -> nullSafeValue.toLowerCase(Locale.ROOT).contains(entry.getKey())).findFirst().map(Map.Entry::getValue).orElse(null);
			default:
				log.error("Invalid operator: {}. Default to exact match", operator);
				return ignoreCase ? lookupRefData.get(nullSafeValue.toLowerCase(Locale.ROOT)) : lookupRefData.get(nullSafeValue);
		}
	}

	private long getDatasetSize(ReferenceDataMeta referenceDataMeta) {
		if (referenceDataMeta.getSource().getType() == ReferenceDataSourceType.syncari) {
			return syncariMongoUtils.count(getCollectionName(referenceDataMeta), Optional.empty());
		} else {
			return customerMongoUtils.count(getCollectionName(referenceDataMeta), Optional.empty());
		}
	}

	public void sendImportRequest(ReferenceDataMeta saved) {
		Event event = new Event().setType(EventTypes.IMPORT_REFERENCE_DATA)
		        .setLoggedTime(new Date())
		        .setDetails(Map.of("refMetaId", saved.getId()));
        Message message = new Message(SyncariContext.getInstance().getSyncariId(), event);
		try {
			String eventString = mapper.writeValueAsString(message);
			log.info(String.format("Sending Dataset Import Message: %s", eventString));
			publisher.publishToGenericQueue(eventString);
			log.info(format("Successfully sent message to start extracting %s", saved.getId()));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	String getCollectionName(ReferenceDataMeta meta) {
	    if(StringUtils.isBlank(meta.getDatasetCollectionName())) {
	        return "dataset_" + meta.getName().replace(" ", "_");
	    }
	    return meta.getDatasetCollectionName();
	}

    private String renameCustomerCollection(String collectionName, String type) {
        String name = collectionName;
        int i = 0;
        boolean exists = type.equalsIgnoreCase(ReferenceDataSourceType.syncari.toString())?
				syncariMongoUtils.existsCollection(collectionName):
				customerMongoUtils.existsCollection(collectionName);
        while(exists) {
            name = collectionName + "_" + i;
            exists = type.equalsIgnoreCase(ReferenceDataSourceType.syncari.toString())?
					syncariMongoUtils.existsCollection(name):
					customerMongoUtils.existsCollection(name);
            i++;
        }
        return name;
    }

	private FileManager getFileManager(ReferenceDataMeta dataset) {
		return fileManagerFactory.getFileManager(dataset.getSource());
	}

	private void sendNotification(String datasetName, long numberImported, String subject) {
		String message = String
				.format("Import into dataset %s completed successfully. This dataset can now be used in pipelines."
						+ " %s records were imported.", datasetName, numberImported);
		Iterable<User> admins = userService.getAdmins();
		admins.forEach(a -> {
		    Notification notification = new Notification(subject, message, NotificationType.INFO, a.getId());
			notificationService.send(notification);
			log.info(format("Successfully sent import notification to %s admin", a.getId()));
		});
	}

	private List<String> validate(ReferenceDataMeta dataset, InputStream inputStream) throws IOException {
		switch (dataset.getSource().getType()) {
		case upload:
		case syncari:
			if (inputStream == null)
				throw new RuntimeException("File is required");
			if (StringUtils.isBlank(dataset.getSource().getLocation()))
				throw new RuntimeException("FileName is required");
			return csvUtil.validate(inputStream, new CSVOptions());
			case s3:
			if (StringUtils.isBlank(dataset.getSource().getLocation()))
				throw new RuntimeException("Fully qualified FileName is required for S3");
			if (StringUtils.isBlank(dataset.getSource().getAccessKey()))
				throw new RuntimeException("Access Key is required for S3");
			if (StringUtils.isBlank(dataset.getSource().getSecretKey()))
				throw new RuntimeException("Secret Key is required for S3");
				InputStream stream = getFileManager(dataset).readFile(dataset.getSource().getLocation());
				return csvUtil.validate(stream, new CSVOptions());
			default:
			throw new RuntimeException("Unsupported type");
		}
	}

	public void setFileManagerFactory(FileManagerFactory fileManagerFactory) {
		this.fileManagerFactory = fileManagerFactory;
	}
}
