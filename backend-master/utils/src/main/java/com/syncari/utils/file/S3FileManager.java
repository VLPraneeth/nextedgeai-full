package com.syncari.utils.file;

import static java.lang.String.format;

import java.io.*;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class S3FileManager implements FileManager , com.syncari.utils.Storage{
	private String bucketName;
	private String accessKey;
	private String secretKey;
	private String clientRegion;
	private AmazonS3 s3Client;

	public S3FileManager(String location, String accessKey, String secretKey) {
		extractData(location);
		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}
	
	public S3FileManager(String bucketName, String clientRegion,  String accessKey, String secretKey) {
	    this.bucketName = bucketName;
        this.clientRegion = clientRegion;
        this.accessKey = accessKey;
	    this.secretKey = secretKey;
	}

	public S3FileManager(String bucketName, String clientRegion,  String accessKey, String secretKey, AmazonS3 s3Client) {
		this.bucketName = bucketName;
		this.clientRegion = clientRegion;
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.s3Client = s3Client;
	}

	@Override
	public String uploadFile(InputStream fileStream, final String fileName) throws IOException {
		return write(fileStream, fileName);
	}

	@Override
	public InputStream readFile(final String fileName) throws IOException {
		return read(fileName);
	}

	@Override
	public void deleteFile(final String fileName) throws IOException {
		delete(fileName);
	}

	@Override
	public void createDirectory(String name) throws IOException {
		throw new RuntimeException("Create directory not supported for S3");
	}

	private AmazonS3 getClient() {
		BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKey, secretKey);
		AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(clientRegion)
				.withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();
		return s3Client;
	}

	public static AmazonDynamoDB getDDBClient(String accessKey, String clientSecret, String region){
		BasicAWSCredentials creds = new BasicAWSCredentials(accessKey,clientSecret);
		AmazonDynamoDB db = AmazonDynamoDBClientBuilder.standard().withRegion(region).withCredentials(new AWSStaticCredentialsProvider(creds))
				.build();
		return db;

	}

	private void extractData(String url) {
		// Example https://syncaridevtest.s3-us-west-1.amazonaws.com/city_names.csv
		try {
			String copied = String.copyValueOf(url.toCharArray());
			String noProtocolUrl = copied.replace("https://", "");
			String host = noProtocolUrl.split("/")[0];
			String[] parts = host.split("\\.");
			clientRegion = parts[1].replace("s3-", "");
			bucketName = parts[0];
		} catch (Exception e) {
			throw new RuntimeException(
					"Invalid S3 location. Should be in the format 'https://<bucket-name>.s3-<region>.amazonaws.com/<file-name>'");
		}
	}

	private String extractFileName(String url) {
		// Example https://syncaridevtest.s3-us-west-1.amazonaws.com/syncari/city_names.csv
	    if(!url.contains("amazonaws.com")) return url;
		try {
			String copied = String.copyValueOf(url.toCharArray());
			String noProtocolUrl = copied.replace("https://", "");
			return noProtocolUrl.split("/")[1];
		} catch (Exception e) {
			throw new RuntimeException(
					"Invalid S3 location. Should be in the format 'https://<bucket-name>.s3-<region>.amazonaws.com/<file-name>'");
		}
	}

	@Override
	public String write(InputStream fileStream, String uri)  {
		AmazonS3 s3Client = this.s3Client != null ? this.s3Client : getClient();
		String[] parts = uri.split("/");
		String folder = parts[0]+"/"+parts[1]+"/";
		PutObjectResult folderResult = s3Client.putObject(bucketName, folder, "");
		log.info("Created folder in S3 {}", folder);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentEncoding("text/csv; charset=utf-8");
		PutObjectResult fileResult = s3Client.putObject(new PutObjectRequest(bucketName, uri, fileStream, metadata));
		log.info("Uploaded file into S3 uri {}", uri);
		return fileResult.getVersionId();
	}

	@Override
	public InputStream read(String uri) {
		AmazonS3 s3Client = this.s3Client != null ? this.s3Client : getClient();
		S3Object file = s3Client.getObject(new GetObjectRequest(bucketName, extractFileName(uri)));
		log.info(format("File with name %s successfully read", uri));
		return file.getObjectContent();

	}

	@Override
	public void delete(String uri)  {
		AmazonS3 s3Client = this.s3Client != null ? this.s3Client : getClient();
		s3Client.deleteObject(bucketName, uri);
		log.info(format("File with name %s successfully deleted", uri));
	}

	@Override
	public long lastModified(String uri) {
		AmazonS3 s3Client = this.s3Client != null ? this.s3Client : getClient();
		S3Object file = s3Client.getObject(new GetObjectRequest(bucketName, extractFileName(uri)));
		return file.getObjectMetadata().getLastModified().toInstant().toEpochMilli();
	}

	@Override
    public String writeToFolder(InputStream fileStream, String fileName, String folderName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void delete(String fileName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }
}
