package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.WRITE_FILE_DATA;
import static com.syncari.core.security.Permissions.READ_FILE_DATA;
import static com.syncari.core.security.Permissions.DELETE_FILE_DATA;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.FileDataFileMeta;
import com.syncari.api.rest.controllers.data.FileDataFolderMeta;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.FileDataFile;
import com.syncari.core.model.misc.FileDataContent;
import com.syncari.core.service.FileDataService;
import com.syncari.utils.file.FileUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/fileData")
public class FileDataController {
    private static final String TEXT_CSV = "text/csv";
    private static final String APP_CSV = "application/csv";
    private static final String APP_OCTET_STREAM = "application/octet-stream"; // Host unknown content type. Windows with no associated application.
    private static final String APP_EXCEL_STREAM = "application/vnd.ms-excel"; // Host with excel
    private static final List<String> supportedContentType = List.of(TEXT_CSV, APP_CSV, APP_OCTET_STREAM, APP_EXCEL_STREAM);

	private static final String CSV = ".csv";
	
	@Autowired
	private FileDataService service;
	@Autowired
    private ObjectTransformer transformer;
	@Autowired
    private FileUtil fileUtil;

    @Secured(READ_FILE_DATA)
    @GetMapping("/folder")
    public List<FileDataFolderMeta> getAllFolder() {
		var folders = service.getAllFolder().stream().map(f -> transformer.toImportDataFolder(f))
				.collect(Collectors.toList());
		folders.forEach(f -> {
			f.setFiles(service.getAllFilesByFolder(f.getId()).stream()
					.map(file -> transformer.toImportDataFile(service.getFile(file.getId())))
					.collect(Collectors.toList()));
		});
		return folders;
    }
    
    @Secured(READ_FILE_DATA)
    @GetMapping("/folder/{folderId}")
    public FileDataFolderMeta getFolder( @PathVariable String folderId) {
        return transformer.toImportDataFolder(service.getFolder(folderId));
    }
    
    @Secured(WRITE_FILE_DATA)
    @PostMapping("/folder")
    public FileDataFolderMeta createFolder(@RequestBody FileDataFolderMeta folderInfo) {
    	log.info("In arcade Start creating folder {}", folderInfo.getName());
    	folderInfo.setName(folderInfo.getName());
    	return transformer.toImportDataFolder(service.createFolder(folderInfo.getName(), folderInfo.getDescription()));
    }
    
    @Secured(WRITE_FILE_DATA)
    @PutMapping("/folder/{folderId}")
    public FileDataFolderMeta editFolder( @PathVariable String folderId, @RequestBody FileDataFolderMeta folderInfo) {
        return transformer.toImportDataFolder(service.editFolder(folderId, folderInfo.getDescription()));
    }
    
    @Secured(READ_FILE_DATA)
    @GetMapping("/file/{fileId}")
    public FileDataFileMeta getFile(@PathVariable String fileId) {
    	return transformer.toImportDataFile(service.getFile(fileId));
    }
    
    @Secured(WRITE_FILE_DATA)
    @PutMapping("/file/{fileId}")
    public FileDataFileMeta editFile(@PathVariable String fileId, @RequestBody FileDataFileMeta fileInfo) {
    	return transformer.toImportDataFile(service.editFile(fileId, fileInfo.getName(), fileInfo.getTags()));
    }

    @Secured(WRITE_FILE_DATA)
    @PostMapping("/file")
    public ResponseEntity<Object> createFile(@RequestParam(name = "folderId") String folderId, @RequestParam("name") String name,
                                             @RequestParam("idColumn") String idColumn, @RequestParam("tags") List<String> tags,
                                             @RequestParam(name = "file", required = false) MultipartFile file,
                                             @RequestParam(name = "withTrim", required = false, defaultValue = "true") boolean withTrim) throws IOException{
    	validateCondition(StringUtils.isBlank(name), i18n("invalid_imp_data_file_name"));
    	validateFile(file);
    	var folder = service.getFolder(folderId);
    	String fixedFileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "_" + fileUtil.sanitizeFileName(name);
    	String fullyQualifiedFileName = SyncariContext.getSyncariId() + "/FileData/" + folder.getFolderName() + "/" + fixedFileName;
    	FileDataFile fileMeta = FileDataFile.builder().idColumn(idColumn).name(name).tags(tags).folderId(folderId).withTrim(withTrim).build();
    	fileMeta.setFilePath(fullyQualifiedFileName);
    	
    	FileDataFile createdFile = service.createFile(fileMeta, file);
    	
    	return ResponseEntity.ok(
    			transformer.toImportDataFile(createdFile)
    			);
    }

    private void validateFile(MultipartFile file) {
        if (file == null)
            throw new SyncariValidationException(i18n("file_required"));
        if(!file.getOriginalFilename().endsWith(CSV)) {
            throw new SyncariValidationException(i18n("unsupported_file_ext"));
        }
        if (!supportedContentType.stream().anyMatch((contentType -> contentType.equalsIgnoreCase(file.getContentType())))) {
            throw new SyncariValidationException(String.format(i18n("unsupported_content_type"), file.getContentType()));
        }
    }

    @Secured(READ_FILE_DATA)
	@RequestMapping(method = RequestMethod.GET, value = "/preview/{fileId}")
	public FileDataContent preview(@PathVariable String fileId,
			@RequestParam(name = "numberOfRows", required = false, defaultValue = "25") int numberOfRows) {
		return service.previewData(fileId, numberOfRows);
	}
	
	@Secured(READ_FILE_DATA)
	@GetMapping("/download/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable String fileId) throws IOException {
		var fileMeta = getFile(fileId);
		InputStreamResource resource = new InputStreamResource(service.getFileContent(fileId));
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileMeta.getName() + "\"")
				.body(resource);
	}
	
	@Secured(DELETE_FILE_DATA)
	@RequestMapping(method = RequestMethod.DELETE, value = "/file/{fileId}")
	public Map<String, String> deleteFile(@PathVariable String fileId) {
		var res = service.deleteFile(fileId, false);
		var finalResponse = new HashMap<String, String>(res);
		finalResponse.put("status", "success");
		return finalResponse;
	}

	@Secured(DELETE_FILE_DATA)
	@DeleteMapping("/folder/{folderId}")
	public void deleteFolder(@PathVariable String folderId) {
		service.deleteFolder(folderId);
	}
	
}
