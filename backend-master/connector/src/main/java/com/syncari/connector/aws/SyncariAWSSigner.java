package com.syncari.connector.aws;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.util.BinaryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import uk.co.lucasweb.aws.v4.signer.HttpRequest;
import uk.co.lucasweb.aws.v4.signer.Signer;
import uk.co.lucasweb.aws.v4.signer.credentials.AwsCredentials;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
public class SyncariAWSSigner {

    private String regionName;
    private String serviceName;

    public SyncariAWSSigner(String region, String serviceName) {
        this.regionName = region;
        this.serviceName = serviceName;
    }

    public HttpHeaders buildHttpHeaders(URI endpoint, AWSCredentials credentials, String requestBody, String targetAPI){
        HttpHeaders headers = new HttpHeaders();
        headers.add("content-length", "" + requestBody.length());
        headers.add("content-type","application/x-amz-json-1.0");

        String timestamp = getTimeStamp(OffsetDateTime.now(ZoneOffset.UTC));
        headers.add("host", endpoint.getHost());
        headers.add("x-amz-date", timestamp);
        headers.add("x-amz-target",targetAPI);
        HttpRequest requestH = new HttpRequest("POST", endpoint);
        String contentSha256 = BinaryUtils.toHex(new AWS4Signer().hash(requestBody));
        String signatureN = Signer.builder()
                .awsCredentials(new AwsCredentials(credentials.getAWSAccessKeyId(),credentials.getAWSSecretKey()))
                .header("Host", endpoint.getHost())
                .header("x-amz-date", timestamp)
                .header("x-amz-target",targetAPI)
                .region(regionName)
                .build(requestH, serviceName, contentSha256)
                .getSignature();

        headers.add("Authorization",signatureN);
        return headers;
    }

    public String getTimeStamp(OffsetDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String formatDateTime = dateTime.format(formatter);
        return formatDateTime;
    }


}
