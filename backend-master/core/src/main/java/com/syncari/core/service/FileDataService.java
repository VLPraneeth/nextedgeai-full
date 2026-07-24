package com.syncari.core.service;

import com.syncari.connector.Constants;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.datatype.IdType;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.FileDataContent;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.util.Status;
import com.syncari.core.repositories.customer.*;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.TextUtil;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@Service
public class FileDataService {
	
	private String buildWarningMessage(List<String> collisions) {
		if (collisions.isEmpty()) {
			return null;
		}
		
		StringBuilder warning = new StringBuilder();
		warning.append("Column name conflict: the following headers match existing columns; mapping will be applied: ");
		
		for (int i = 0; i < collisions.size(); i++) {
			if (i > 0) {
				warning.append(", ");
			}
			warning.append(collisions.get(i));
		}
		return warning.toString();
	}
	
	@Autowired
	private FileDataFolderRepo folderRepo;
	@Autowired
	private FileDataFileRepo fileRepo;
	@Autowired
	private CsvUtils csvUtil;
    @Autowired
    @Qualifier("gcsImportedFilesFileManager")
    private GCSFileManager gcsFileManager;
	@Autowired
	private ConnectorRepo connectorRepo;
	@Autowired
	private EntityDefinitionRepo entityProxyRepo;
	@Autowired
	private AttributeRepo attributeProxyRepo;
	@Autowired
    private FileUtil fileUtil;
	@Autowired
    private SchemaService schemaService;
	@Autowired
    private MappingGraphService mappingGraphService;
	@Autowired
	private TextUtil textUtil;
	@Autowired
	CsvUtils csvUtils;
	@Autowired
    private TagService tagService;

	public FileDataFolder getFolder(String folderId) {
		var folder =  folderRepo.findById(folderId);
		validateCondition(folder.isEmpty(), i18n("file_data_invalid_folder_id", folderId));
		return folder.get();
	}
	
	public FileDataFolder editFolder(String folderId, String description) {
		var folderOpt =  folderRepo.findById(folderId);
		validateCondition(folderOpt.isEmpty(), i18n("file_data_invalid_folder_id", folderId));
		var folder = folderOpt.get();
		folder.setDescription(description);
		folder = folderRepo.save(folder);
		return folder;
	}
	
	public List<FileDataFolder> getAllFolder() {
		return folderRepo.findAll();
	}
	
	public List<FileDataFile> getAllFilesByFolder( String folderId) {
		return fileRepo.findByFolderId(folderId);
	}
	
	public FileDataFolder createFolder(String name, String description) {
		log.info("Start creating folder {}", name);
		validateCondition(StringUtils.isEmpty(name), i18n("file_data_invalid_folder_name", name));
		validateCondition(!StringUtils.isAlphanumeric(name), i18n("file_data_invalid_folder_name_alpha_numeric", name));
		validateCondition(folderRepo.findByName(name).isPresent(), i18n("file_data_folder_name_exist", name));
		String folderName = textUtil.createApiName(name);
		FileDataFolder folder = new FileDataFolder(name,folderName ,description);
		folder =  folderRepo.save(folder);
		createEntity(name, true);
		return folder;
	}
	
	private Optional<EntityDefinition> createEntity(String name, boolean withTrim) {
		log.info("Creating entity for folder {} and connector {}", name, Constants.IMPORTED_FILES);
		EntityDefinition entity = null;
		var connector = connectorRepo.findByName(Constants.IMPORTED_FILES);
		if(connector.isPresent()) {
			entity = new EntityDefinition();
			entity.setApiName(textUtil.createApiName(name));
			entity.setConnectorId(connector.get().getId());
			entity.setConnectorTypeId(connector.get().getMetadataId());
			entity.setDataStoreName(name);
			entity.setDescription(name);
			entity.setDisplayName(name);
			entity.setDraftStatus(DraftStatus.APPROVED);
			entity.setAttributes(List.of());
			entity.getAdditionalProperties().put(Constants.WITH_TRIM, withTrim);
			entity.setStatus(Status.ACTIVE);
			entity = entityProxyRepo.save(entity);
			log.info("Entity for folder {} and connector {} created", name, Constants.IMPORTED_FILES);
		} else {
			log.info("Connector {} not present. Skipping entity creation for folder {} ", Constants.IMPORTED_FILES, name);
		}
		return Optional.ofNullable(entity);
	}

	public FileDataFile createFile(FileDataFile data, InputStreamSource source) {
		List<String> warningsList = new ArrayList<>();
		try {
			var folder = folderRepo.findById(data.getFolderId());
			if(folder.isEmpty()) {
				throw new RuntimeException("File is required");
			}
			Map<String, String> columns = validate(folder.get().getFolderName(), data, source, warningsList);
			gcsFileManager.uploadFile(source.getInputStream(), data.getFilePath());
			data = fileRepo.save(data);
			if (data.getTags() != null && !data.getTags().isEmpty()) {
				var file = data;
				var tags = data.getTags().stream().map(name -> new Tag(name, true, Taggable.fileData, file.getId()))
						.collect(Collectors.toList());
				tagService.addTags(tags);
			}
			if (!warningsList.isEmpty()) {
				data.setWarnings(buildWarningMessage(warningsList));
			}
			createAttributes(folder.get(), data, columns);
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
		return data;
	}
	
	private void createAttributes(FileDataFolder folder, FileDataFile data, Map<String, String> columns) {
		log.info("Creating attributes for file {}", data.getFilePath());
		var connector = connectorRepo.findByName(Constants.IMPORTED_FILES);
		if(connector.isPresent()) {
			var entity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(connector.get().getId(), textUtil.createApiName(folder.getName()));
			if(entity.isEmpty()) {
				entity = createEntity(folder.getFolderName(), data.isWithTrim());
			}
			entity.get().getAdditionalProperties().put(Constants.WITH_TRIM, data.isWithTrim());
			entityProxyRepo.save(entity.get());
			var attribs = attributeProxyRepo.findByEntityId(entity.get().getId());
			List<AttributeDefinition> attribsList = new ArrayList<>();
			Map<String, AttributeDefinition> attribMap = new HashMap<>();
			attribs.forEach(a -> attribMap.put(a.getApiName().toLowerCase(), a));

			// Track API names in current batch to prevent duplicates
			Set<String> currentBatchApiNames = new HashSet<>(attribMap.keySet());

			for(String col: columns.keySet()) {
				String createdApiName = textUtil.createApiName(col).toLowerCase();
				if(attribMap.containsKey(createdApiName)) {
					log.info("Skipping attribute {} for file {} since attribute already present", col, data.getFilePath());
					AttributeDefinition existingAttributeDefinition = attribMap.get(createdApiName);
					if (!existingAttributeDefinition.isActive()){
						existingAttributeDefinition.setStatus(Status.ACTIVE);
						existingAttributeDefinition.setDataType(DatatypeFactory.getDatatype(columns.get(col)));
						attributeProxyRepo.save(existingAttributeDefinition);
					}
					continue;
				}

				// Handle duplicates within the current batch using SchemaService logic
				while(currentBatchApiNames.contains(createdApiName)) {
					createdApiName = schemaService.populateApiNameWithCounter(createdApiName);
				}
				currentBatchApiNames.add(createdApiName);

				AttributeDefinition ad = new AttributeDefinition();
				ad.setEntityId(entity.get().getId());
				ad.setApiName(createdApiName);
				ad.setCalculated(false);
				// Check if this is the ID column by comparing the ORIGINAL column name (not normalized)
			// This ensures the exact column the user selected gets marked as ID field
			if(StringUtils.isNotBlank(data.getIdColumn()) && col.equals(data.getIdColumn())) {
					ad.setDataType(new IdType());
					ad.setIdField(true);
				} else if(col.equals("lastModifiedTime")) {
					ad.setDataType(new DatetimeType());
					ad.setWatermarkField(true);
					ad.setNillable(false);
				} else {
					ad.setDataType(DatatypeFactory.getDatatype(columns.get(col)));
					ad.setWatermarkField(false);
				}
				ad.setDescription(col);
				ad.setDisplayName(col);
				ad.setStatus(Status.ACTIVE);
				ad.setDraftStatus(DraftStatus.APPROVED);
				ad.setUpdatable(true);
				attribsList.add(ad);
			}
			if(!columns.keySet().contains("lastModifiedTime") && !currentBatchApiNames.contains("lastmodifiedtime")) {
				AttributeDefinition ad = new AttributeDefinition();
				ad.setEntityId(entity.get().getId());
				ad.setApiName("lastModifiedTime");
				ad.setCalculated(false);
				ad.setDataType(new DatetimeType());
				ad.setDescription("Last Modified Time");
				ad.setDisplayName("Last Modified Time");
				ad.setStatus(Status.ACTIVE);
				ad.setDraftStatus(DraftStatus.APPROVED);
				ad.setNillable(false);
				ad.setUpdatable(true);
				ad.setWatermarkField(true);
				attribsList.add(ad);
			}
			attributeProxyRepo.saveAll(attribsList);
			log.info("Attributes for file {}", data.getFilePath());

		} else {
			log.info("Connector {} not present. Skipping attributes creation for file {} ", Constants.IMPORTED_FILES, data.getFilePath());
		}

	}

	public FileDataFile getFile(String fileId) {
		var fileObj =  fileRepo.findById(fileId);
		validateCondition(fileObj.isEmpty(), i18n("file_data_invalid_file_id", fileId));
		var file = fileObj.get();
		if(file.getRowsCount() == null) {
			var filePath = file.getFilePath();
			var fileData = gcsFileManager.read(filePath);
			file.setRowsCount(csvUtil.getRowCount(fileData, new CSVOptions()));
			fileRepo.save(file);
		}
		file.setTags(List.copyOf(tagService.getTagNames(Taggable.fileData, fileId)));
		return file;
	}
	
	public FileDataFile editFile(String fileId, String fileName, List<String> tags) {
		var fileObj =  fileRepo.findById(fileId);
		validateCondition(fileObj.isEmpty(), i18n("file_data_invalid_file_id", fileId));
		var file = fileObj.get();
		if(!fileName.equals(file.getName())) {
			validateCondition(fileRepo.existsByNameAndFolderId(fileName, file.getFolderId()), "file_data_file_name_exist", fileName);
		}
		if(file.getRowsCount() == null) {
			var filePath = file.getFilePath();
			var fileData = gcsFileManager.read(filePath);
			file.setRowsCount(csvUtil.getRowCount(fileData, new CSVOptions()));
		}
		if (tags != null && !tags.isEmpty()) {
			var tagsIncoming = tags.stream().map(name -> new Tag(name, true, Taggable.fileData, fileId))
					.collect(Collectors.toList());
			tagService.updateTagsFor(fileId, Taggable.fileData, tagsIncoming);
			file.setTags(tags);
		}
		file.setName(fileName);
		fileRepo.save(file);
		
		return file;
	}
	
	private Map<String, String> validate(String folderName, FileDataFile dataset, InputStreamSource source) throws IOException {
		return validate(folderName, dataset, source, null);
	}
	
	private Map<String, String> validate(String folderName, FileDataFile dataset, InputStreamSource source, List<String> warningsList) throws IOException {
			if (source == null) {
				throw new RuntimeException("File is required.");
			}
			if (StringUtils.isBlank(dataset.getFilePath())) {
				throw new RuntimeException("FileName is required.");
			}
			//validateCondition(StringUtils.isAlphanumeric(dataset.getName()), i18n("file_data_invalid_file_name_alpha_numeric", dataset.getName()));
			var fileCount = fileRepo.findByFolderId(dataset.getFolderId()).stream().filter(file -> file.getName().equals(dataset.getName())).count();
			validateCondition(fileCount > 0, i18n("file_data_already_exists", dataset.getName(), folderName));
		InputStream stream1 = source.getInputStream();
		validateCondition(!csvUtil.isStreamParsable(stream1, new CSVOptions()), i18n("file_data_corrupted_file", dataset.getName(), folderName));
		InputStream stream = source.getInputStream();
		var headers = csvUtil.detectDatatypes(stream, new CSVOptions());
            if(headers != null) {
            	var connector = connectorRepo.findByName(Constants.IMPORTED_FILES);
            	if(connector.isPresent()) {
            		// retrieve approved version of entity
            		var entity = entityProxyRepo.findActiveEntityByConnectorIdAndApiName(connector.get().getId(), textUtil.createApiName(folderName));
            		if(entity.isPresent()) {
            			var attribs = attributeProxyRepo.findByEntityId(entity.get().getId());
            			if(!attribs.isEmpty()) {
							var fromDb = attribs.stream().map(a -> a.getApiName())
									.filter(name -> !name.equalsIgnoreCase("lastModifiedTime")).sorted()
									.collect(Collectors.toList());
							var idField = attribs.stream()
									.filter(a -> a.isIdField())
									.map(a -> a.getApiName()).findAny();
							var fromFile = headers.keySet().stream().map(name -> textUtil.createApiName(name)).sorted()
									.collect(Collectors.toList());
            				log.info("List of attributes {}", fromDb);
            				log.info("List of columns in new file {}", fromFile);
            				
            				if (warningsList != null) {
            					for (String originalHeader : headers.keySet()) {
            						String apiName = textUtil.createApiName(originalHeader);
            						if (fromDb.contains(apiName) && !originalHeader.equals(apiName)) {
            							warningsList.add(originalHeader + " -> " + apiName);
            						}
            					}
            				}
            				
            				validateUpload(fromDb, fromFile, idField.orElse(""), warningsList != null);
            			} else {
            				var fromFile = headers.keySet().stream().map(name -> textUtil.createApiName(name)).sorted()
									.collect(Collectors.toList());
            				validateCondition(!fromFile.contains(textUtil.createApiName(dataset.getIdColumn())), i18n("file_data_is_not_found", dataset.getIdColumn()));
            			}
            		}
            	}
            }
            return headers;
	}

	private void validateUpload(List<String> headersFromDb, List<String> headersFromFile, String idField) {
		validateUpload(headersFromDb, headersFromFile, idField, false);
	}
	
	private void validateUpload(List<String> headersFromDb, List<String> headersFromFile, String idField, boolean skipColumnMatchValidation) {
		//db []
		validateCondition(CollectionUtils.isEmpty(headersFromDb), i18n("file_data_empty_schema"));
		//file []
		validateCondition(CollectionUtils.isEmpty(headersFromFile), i18n("file_data_empty_file"));
		//db [a b c] and file [x y z]
		if (!skipColumnMatchValidation) {
			validateCondition(headersFromDb.stream().filter(headersFromFile::contains).count() == 0, i18n("file_data_invalid_columns", String.join(", ", headersFromFile)));
		}
		//file not having id field
		validateCondition(!headersFromFile.contains(idField), i18n("file_data_is_not_found", idField));
	}
	
	public void seedTrailUserData(String instanceId) {
		seedEntity(instanceId, "lead", "dataset/leads-sample-06152022.csv", "id");
		seedEntity(instanceId, "account", "dataset/deduped-accounts-sample-06152022.csv", "id");
	}
	
	private void seedEntity(String instanceId, String name, String filePath, String idColumn) {
		var folder = createFolder(name, name);

		Resource res = new ClassPathResource(filePath);
		String fixedFileName = fileUtil.sanitizeFileName(filePath.substring(filePath.lastIndexOf("/") + 1));
		String fullyQualifiedFileName = instanceId + "/FileData/" + folder.getFolderName() + "/"
				+ fixedFileName;

		FileDataFile fileMeta = FileDataFile.builder().idColumn(idColumn).name(fixedFileName).tags(List.of())
				.folderId(folder.getId()).build();
		fileMeta.setFilePath(fullyQualifiedFileName);
		try {
			createFile(fileMeta, res);
			var connector = connectorRepo.findByName(Constants.IMPORTED_FILES);
			if(connector.isPresent()) {
				var sourceEntity = schemaService.getEntityByName(connector.get().getId(), name);
				if(sourceEntity.isPresent()) {
					var syncariEntity = schemaService.createEntityLike(sourceEntity.get(), List.of());
					var mappingGraph = mappingGraphService.retrieveEntityGraph(syncariEntity.getId());
			        var graphToApprove = mappingGraph.flatMap(g -> g.isDraft() ? Optional.of(g) : mappingGraphService.findDraft(g))
			                .orElseThrow(() -> new RuntimeException(String.format("A draft Entity Pipeline for entity with id %s not found", syncariEntity.getId())));
			        mappingGraphService.validateGraph(graphToApprove.getId(), false);
			        log.info("Approving {} draft for {}", syncariEntity.getId(), SyncariContext.getSyncariId());
			        mappingGraphService.approveDraft(graphToApprove);
				}
			}
		}catch (Exception e) {
			log.error("Failed to upload file {}", fixedFileName, e);
		}
	}
	
	public Map<String, String> deleteFile(String fileId, boolean forced) {
		var fileData = getFile(fileId);
		var folderData = folderRepo.findById(fileData.getFolderId());
		
		if(folderData.isPresent()) {
			var folder = folderData.get();
			var fileCount = fileRepo.findByFolderId(folder.getId()).size();
			if (!forced && fileCount == 1) {
				try {
					truncateFileContent(fileData.getFilePath());
					fileData.setRowsCount(0L);
					fileRepo.save(fileData);
					return Map.of("message", i18n("file_data_delete_file_warning_header_retain"));
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
			}
		}
		try {
			gcsFileManager.deleteFile(fileData.getFilePath());
			fileRepo.deleteById(fileId);
			tagService.removeTagsFor(Taggable.fileData, fileId);
		} catch (Exception e) {
			log.error("Failed to delete import data file {} ", fileData.getFilePath(), e);
			throw new RuntimeException(e.getMessage());
		}
		return Map.of();
	}

	private void truncateFileContent(String filePath) throws IOException {
		var fileContent = gcsFileManager.read(filePath);
		var in = csvUtil.truncateData(fileContent, new CSVOptions());
		gcsFileManager.write(in, filePath);
		in.close();
	}

	public void deleteFolder(String folderId) {
		folderRepo.findById(folderId).ifPresent(folder -> {
			connectorRepo.findByName(Constants.IMPORTED_FILES).ifPresent(connector -> {
				entityProxyRepo
						.findAllByConnectorId(connector.getId()).stream().filter(e -> e.getApiName() != null && e.getApiName().equals(textUtil.createApiName(folder.getName())))
						.forEach(entity -> {
							if (mappingGraphService.getMappedEntities(connector.getId()).containsKey(entity.getId())) {
								throw new SyncariValidationException(i18n("file_data_delete_folder_pipeline_error"));
							} else {
								schemaService.archiveEntity(entity);
							}
						});
			});
			fileRepo.findByFolderId(folderId).forEach(file -> {
				deleteFile(file.getId(), true);
			});
			folderRepo.deleteById(folderId);
		});
	}

	public FileDataContent previewData(String fileId, int numberOfRows) {
		var fileObj = fileRepo.findById(fileId);
		validateCondition(fileObj.isEmpty(), i18n("file_data_invalid_file_id", fileId));
		var file = fileObj.get();
		try {
			var headerStream = gcsFileManager.readFile(file.getFilePath());
			var headerColumns = csvUtil.getHeaders(headerStream, new CSVOptions());
			headerStream.close();
			var contentStream = gcsFileManager.readFile(file.getFilePath());
			var values = csvUtil.getRows(contentStream, numberOfRows, new CSVOptions().withTrim(file.isWithTrim()));
			contentStream.close();
			return new FileDataContent(headerColumns, values);
		} catch (IOException e) {
			log.error("Failed to load file preview for {} from {}", file.getName(), file.getFilePath(), e);
			throw new RuntimeException(e.getMessage());
		}
	}
	
	public InputStream getFileContent(String fileId) throws IOException {
		var fileObj = fileRepo.findById(fileId);
		validateCondition(fileObj.isEmpty(), i18n("file_data_invalid_file_id", fileId));
		var file = fileObj.get();
	    return sanitizeFile(gcsFileManager.readFile(file.getFilePath()), file.isWithTrim());
	}

	private InputStream sanitizeFile(InputStream file, boolean withTrim) {
		try {
			StringWriter csvBuffer = new StringWriter();
			CSVPrinter csvPrinter = new CSVPrinter(csvBuffer, CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL));
			CSVParser parser = csvUtil.getCSVParser(file, new CSVOptions()
					.withTrim(withTrim)
			);
			csvPrinter.printRecord(parser.getHeaderNames());
			csvPrinter.printRecords(parser.getRecords());
			return new ByteArrayInputStream(csvBuffer.toString().getBytes());
		} catch (Exception e) {
			log.error("Error while reading file ", e);
			return InputStream.nullInputStream();
		}
	}

	public InputStream getFileContentByFilePaths(String filePath) throws IOException {
		validateCondition(filePath.isEmpty(), i18n("file_path_invalid", filePath));
		return gcsFileManager.readFile(filePath);
	}

}
