package com.syncari.utils.file;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.function.Supplier;

import static java.lang.String.format;

@Slf4j
public class SftpFileManager implements FileManager , com.syncari.utils.Storage{
	private SftpClient client;

	public SftpFileManager(SftpClient client) {
	    this.client = client;
	}

	private ChannelSftp getSftpClient() {
		return client.getClient();
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
	public String write(InputStream fileStream, String uri)  {
		return withRetry(() -> {
			try {
				getSftpClient().put(fileStream, uri);
				log.info("Successfully uploaded {}", uri);
			} catch (SftpException e) {
				log.error(ExceptionUtils.getStackTrace(e));
				throw new RuntimeException(e);
			}
			return "";
		});
	}

	@Override
	public InputStream read(String uri) {
		return withRetry(() -> {
			try {
				log.info(format("File with name %s successfully read", uri));
				return getSftpClient().get(uri);
			} catch (Exception e) {
				log.error("Exception occurred, stacktrace is {} with message {}", ExceptionUtils.getStackTrace(e), StringUtils.isNotEmpty(e.getMessage()) ? e.getMessage() : "Empty exception message");
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void delete(String uri)  {
		withRetry(() -> {
			try {
				Collection<ChannelSftp.LsEntry> fileAndFolderList = getSftpClient().ls(uri);
				// Delete all fiels
				for (ChannelSftp.LsEntry item : fileAndFolderList) {
					if (!item.getAttrs().isDir()) {
						getSftpClient().rm(uri + "/" + item.getFilename());
					}
				}
				getSftpClient().rmdir(uri);
				log.info("Successfully deleted {}", uri);
			} catch (SftpException e) {
				log.error(ExceptionUtils.getStackTrace(e));
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public long lastModified(String uri) {
		return withRetry(() -> {
			try {
				Vector vec = getSftpClient().ls(uri);
				if (vec != null && vec.size() == 1) {
					ChannelSftp.LsEntry details = (ChannelSftp.LsEntry) vec.get(0);
					SftpATTRS attrs = details.getAttrs();
					int mTime = attrs.getMTime();
					//SftpATTRS.getAttrs().getMTime() always return an int value hence not a valid epochMilli
					//and we require the lastModified in epochMilli
					return mTime*1000L;
				}
				return 0L;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public void createDirectory(String uri) {
		withRetry(() -> {
			try {
				getSftpClient().mkdir(uri);
			} catch (SftpException e) {
				log.error(ExceptionUtils.getStackTrace(e));
				throw new RuntimeException(e);
			}
		});
	}

	@Override
	public List<File> list(String dir, String pattern) {
		return withRetry(() -> {
			try {
				List<File> files = new ArrayList<>();
				boolean validPattern = StringUtils.isNotBlank(pattern);
				getSftpClient().ls(dir, entry -> {
					if (validPattern) {
						if (entry.getFilename().matches(pattern)) {
							files.add(new File()
									.setPath(entry.getLongname())
									.setName(entry.getFilename())
									.setDirectory(entry.getAttrs().isDir())
									.setLastModified(entry.getAttrs().getMTime() * 1000L)
							);
						}
					} else {
						files.add(new File()
								.setPath(entry.getLongname())
								.setName(entry.getFilename())
								.setDirectory(entry.getAttrs().isDir())
								.setLastModified(entry.getAttrs().getMTime() * 1000L));
					}
					return ChannelSftp.LsEntrySelector.CONTINUE;
				});
				return files;
			} catch (SftpException e) {
				log.error(ExceptionUtils.getStackTrace(e));
				throw new RuntimeException(e);
			}
		});
	}

	@Override
    public String writeToFolder(InputStream fileStream, String fileName, String folderName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void delete(String fileName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }

	@SneakyThrows
	private <T> T withRetry(Supplier<T> supplier) {
		return withRetry(supplier, 3, 1000);
	}

	@SneakyThrows
	private void withRetry(Runnable runnable) {
		withRetry(() -> {
			runnable.run();
			return null;
		}, 3, 1000);
	}

	@SneakyThrows
	private <T> T withRetry(Supplier<T> supplier, int maxRetries, int retryInterval) {
		int retries = 0;
		Exception lastException = null;
		while (retries < maxRetries) {
			try {
				return supplier.get();
			} catch (Exception e) {
				//close the client, so next time it will fetch a fresh connection
				client.close();
				Thread.sleep(retryInterval);
				retries++;
				log.error(e.getMessage(), e);
				lastException = e;
			}
		}
		if (lastException != null) {
			throw new RuntimeException(lastException);
		}
		return null;
	}

}
