package com.syncari.api.alerts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CsvTempFileCleaner {

    @Scheduled(cron = "0 0 0 * * *")
	public void cleanupTempFiles() {
		log.info("Deleteting temp file data files");
		Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
		try (Stream<Path> walk = Files.walk(tempDir)) {
            walk
               .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".tmp_file_data"))
                .filter(p -> {
                	BasicFileAttributes attr;
					try {
						attr = Files.readAttributes(p, BasicFileAttributes.class);
						FileTime fileTime = attr.creationTime();
						return ChronoUnit.DAYS.between(fileTime.toInstant(), Instant.now()) >= 1;
					} catch (IOException e) {
						log.error("Failed to check time ", e);
					}
					return false;
                }).forEach(p -> {
                	log.debug("deleting {}", p.getFileName());
                	p.toFile().delete();
                });
        } catch(IOException ex){
        	log.error("Failed cleanupTempFiles ", ex);
        }
	}

}
