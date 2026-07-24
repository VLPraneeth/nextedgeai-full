package com.syncari.utils.file;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Instant;

import org.apache.commons.lang3.StringUtils;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.CopyWriter;
import com.google.cloud.storage.Storage;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GCSFileManager implements FileManager, com.syncari.utils.Storage {
    private Storage storageService;
    private String bucket;
    
    public GCSFileManager(Storage storageService, String bucket) {
    	super();
    	this.storageService = storageService;
    	this.bucket = bucket;
    }

    @Override
    public String write(InputStream fileStream, final String fileName) {
        return write(fileStream, fileName, bucket);
    }


	@Override
    public String writeToFolder(InputStream fileStream, final String fileName, final String folderName, String bucketName) {
        return write(fileStream, folderName + "/" + fileName, bucketName);
    }

    public String write(InputStream fileStream, final String fileName, String bucketName) {
        if (fileStream == null)
            throw new RuntimeException("File stream cannot be blank");
        validate(fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName).build();
        try (WriteChannel writer = storageService.writer(blobInfo)) {
            ReadableByteChannel newChannel = Channels.newChannel(fileStream);
            // 1MB chunks
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            int read = newChannel.read(buffer);
            while (read > 0) {
                buffer.position(0);
                buffer.limit(read);
                writer.write(buffer);
                buffer.clear();
                read = newChannel.read(buffer);
            }
            log.info(format("File with name %s successfully uploaded", fileName));
            return blobInfo.getMediaLink();
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException("Unable to uplaod file " + fileName, e);
        }
    }

    @Override
    public InputStream read(final String fileName) {
        return read(fileName, bucket);
    }

    public InputStream read(final String fileName, final String bucketName) {
        validate(fileName);
        Blob blob = storageService.get(bucketName, fileName);
        if (blob == null) throw new RuntimeException("File with name " + fileName + " not found");
        ReadChannel reader = blob.reader();
        log.info(format("File with name %s successfully read", fileName));
        return Channels.newInputStream(reader);
    }

    @Override
    public void delete(final String fileName) {
        delete(fileName, bucket);
    }

    @Override
    public void delete(final String fileName, final String bucketName) {
        validate(fileName);
        storageService.delete(BlobId.of(bucketName, fileName));
        log.info(format("File with name %s deleted successfully", fileName));
    }

    @Override
    public long lastModified(String fileName) {
        validate(fileName);
        Blob blob = storageService.get(bucket, fileName);
        return blob == null ? Instant.EPOCH.toEpochMilli() : blob.getUpdateTime();
    }

    private void validate(String fileName) {
        if (StringUtils.isBlank(fileName))
            throw new RuntimeException("Filename cannot be blank");
    }

    @Override
    public String uploadFile(InputStream fileStream, String uri) {
        return write(fileStream, uri);
    }

    @Override
    public InputStream readFile(String uri)  {
        return read(uri);
    }

    public boolean hasFile(String bucketName, String fileName){
        Blob blob = storageService.get(bucketName, fileName);
        return blob != null && blob.exists();
    }

    @Override
    public void deleteFile(String uri)  {
        delete(uri);
    }

    @Override
    public void createDirectory(String name) throws IOException {
        throw new RuntimeException("Create directory not supported for GCSFileManager");
    }

    public void copyFile(String srcBucketName, String srcFileName, String destBucketName, String destFileName){
        Blob blob = storageService.get(srcBucketName, srcFileName);
        CopyWriter copyWriter = blob.copyTo(destBucketName, destFileName);
        copyWriter.getResult();
    }
}
