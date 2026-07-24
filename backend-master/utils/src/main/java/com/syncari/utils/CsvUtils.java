package com.syncari.utils;

import com.syncari.utils.file.PatternFilteringReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class CsvUtils {

	final static String ALPHANUMERIC_REGEX = "^(?![0-9]*$)[a-zA-Z0-9]+$";

	public List<String> validate(InputStream stream, CSVOptions options) {
		List<String> headers;
		try {
			try (CSVParser parser = getCSVParser(stream, options)) {
				int headerColSize = parser.getHeaderNames().size();
				headers = parser.getHeaderNames();
				if(headerColSize <= 0) throw new RuntimeException("Invalid csv file");
				for (CSVRecord csvRecord : parser) {
					// For now do not be strict
//					if (csvRecord.size() != headerColSize) {
//						throw new RuntimeException("Invalid csv file");
//					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
		return headers;
	}

	public Map<String, String> detectDatatypes(InputStream stream, CSVOptions formatOptions) {
		List<String> headers;
		Map<String, String> headerTypeMap = new HashMap<>();
		try {
			try (RewindableCSVParser parser = new RewindableCSVParser(getCSVParser(stream, formatOptions))) {
				headers = getHeaders(parser, formatOptions);
				int headerColSize = headers.size();
				if(headerColSize <= 0) throw new RuntimeException("Invalid csv file");
				int i = 0;
				for (CSVRecord csvRecord : parser) {
					if(i == 100) break;
					Map<String, String> values = toMap(csvRecord, headers);
					for(Map.Entry<String, String> e : values.entrySet()) {
						String key = e.getKey();
						String value =e.getValue();
						if(value == null || StringUtils.isEmpty(value)) continue;
						if("yes".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)
								|| "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
							headerTypeMap.put(key, "boolean");
							continue;
						}
						if (value.matches(ALPHANUMERIC_REGEX)){
							headerTypeMap.put(key, "string");
							continue;
						}
						if ((headerTypeMap.containsKey(key)) && (headerTypeMap.get(key).equals("string")) && (i>0)){
							continue;
						}
						if ((headerTypeMap.containsKey(key)) && (headerTypeMap.get(key).equals("number")) && (i>0)){
							continue;
						}
						try {
							Long.parseLong(value);
							headerTypeMap.put(key, "integer");
							continue;
						} catch (Exception ex) {
							log.debug("For key {} for type integer, Exception occurred {}",key,ex.getMessage());
							if (headerTypeMap.containsKey(key)){
								headerTypeMap.remove(key);
							}

						}
						try {
							Double.parseDouble(value);
							headerTypeMap.put(key, "number");
							continue;
						} catch (Exception ex) {
							log.debug("For key {} for type number, Exception occurred {}",key,ex.getMessage());
							if (headerTypeMap.containsKey(key)){
								headerTypeMap.remove(key);
							}
						}
						try {
							ZonedDateTime dt = DateUtil.convertDateTime(value);
							if(dt != null) {
								headerTypeMap.put(key, "datetime");
								continue;
							}
						} catch (Exception ex) {
							log.debug("For key {} for type datetime, Exception occurred {}",key,ex.getMessage());
							if (headerTypeMap.containsKey(key)){
								headerTypeMap.remove(key);
							}
						}
						try {
							LocalDate dt = DateUtil.convertDate(value);
							if(dt != null) {
								headerTypeMap.put(key, "date");
								continue;
							}
						} catch (Exception ex) {
							log.debug("For key {} for type date, Exception occurred {}",key,ex.getMessage());
							if (headerTypeMap.containsKey(key)){
								headerTypeMap.remove(key);
							}
						}
					}
					i++;
				}
			}
			headers.stream().forEach(h -> {
				if(!headerTypeMap.containsKey(h)) headerTypeMap.put(h, "string");
			});
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw new RuntimeException(e.getMessage());
		}
		return headerTypeMap;
	}

    private Map<String, String> toMap(CSVRecord csvRecord, List<String> headers) {
        final Map<String, String> recordAsMap = new LinkedHashMap<>();
        int recordSize = csvRecord.size();
        for (int i = 0; i < headers.size(); i++) {
            String value = null;
            try {
                if (i < recordSize) {
                    value = csvRecord.get(i);
                }
            } catch (IndexOutOfBoundsException ex) {
                // Defensive: ignore if parser is inconsistent
                log.debug("Index {} out of bounds for record {}", i, csvRecord);
            }
            if (StringUtils.isBlank(value)) value = null;
            recordAsMap.put(headers.get(i), value);
        }
        return recordAsMap;
    }


	public static boolean skipLine(CSVRecord record, String skipLinePattern) {
		return StringUtils.isNotBlank(skipLinePattern) && StringUtils.join(record.iterator(), ",").matches(skipLinePattern);
	}

	public List<String> getHeaders(InputStream stream, CSVOptions options) {
		try {
			try (CSVParser parser = getCSVParser(stream, options)) {
				return getHeaders(new RewindableCSVParser(parser), options);
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public static List<String> getHeaders(RewindableCSVParser parser, CSVOptions options) {
		if (options.isHeaderPresent()) {
			return parser.getHeaderNames();
		}
		final RewindableIterator<CSVRecord> iterator = parser.rewindableIterator();
		boolean isCollecting = iterator.isCollecting();
		iterator.collect(true);
		if (iterator.hasNext()) {
			CSVRecord record = iterator.next();
			//rewind the parser because we don't want to miss the record
			//that was read above. We use this reord only to count the
			//number of fields
			iterator.rewind(1);
			iterator.collect(isCollecting);
			return IntStream.range(0, record.size())
					.mapToObj(i -> "field" + (i + 1))
					.collect(Collectors.toList());
		}
		return List.of();
	}

	public List<List<String>> getRows(InputStream stream, int limit, CSVOptions options) {

		if (stream == null)
			throw new RuntimeException("File stream cannot be null");
		List<List<String>> rows = new ArrayList<List<String>>();
		try {
			try (CSVParser parser = getCSVParser(stream, options)) {
				int i = 0;
				for (CSVRecord csvRecord : parser) {
					if (i >= limit)
						return rows;
					List<String> colList = new ArrayList<String>();
					csvRecord.forEach(col -> {
						colList.add(col);
					});
					rows.add(colList);
					i++;
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException e) {
					log.error("Error closing stream");
				}
		}
		return rows;
	}

	public boolean hasRows(InputStream stream, CSVOptions options) {

		if (stream == null)
			return false;
		try {
			try (CSVParser parser = getCSVParser(stream, options)) {
				return parser.iterator().hasNext();
			}
		} catch (Exception e) {
			return false;
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException e) {
					log.error("Error closing stream");
				}
		}
	}

	public long getRowCount(InputStream stream, CSVOptions options) {
		if (stream == null)
			return 0;
		try {
			try (CSVParser parser = getCSVParser(stream, options)) {
				long count = 0;
				for(var rec : parser) {
					count++;
				}
				return count;
			}
		} catch (Exception e) {
			return 0;
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException e) {
					log.error("Error closing stream");
				}
		}
	}

	public CSVParser getCSVParser(InputStream in, CSVOptions csvFormat) throws IOException {
		Reader reader = createReader(in, csvFormat);
		return CSVParser.parse(reader, csvFormat.getFormat());
	}

	private Reader createReader(InputStream in, CSVOptions csvFormat) {
		final BOMInputStream inputStream = new BOMInputStream(in);
		if (csvFormat.hasSkipLinePattern()) {
			return new PatternFilteringReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), csvFormat.getSkipLinePattern());
		} else {
			return new InputStreamReader(inputStream, StandardCharsets.UTF_8);
		}
	}

	public CSVParser getCSVParser(InputStream in) throws IOException {
		return getCSVParser(in, new CSVOptions());
	}

	public InputStream truncateData(InputStream in, CSVOptions options) throws IOException {
		StringWriter writer = new StringWriter();
		var parser = getCSVParser(in, options);
		CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT);
		printer.printRecord(parser.getHeaderNames());
		parser.close();
		printer.close();
		byte[] barray = writer.toString().getBytes();
		return new ByteArrayInputStream(barray);
	}

	public boolean isStreamParsable(InputStream stream, CSVOptions options) throws IOException {
		if (stream == null)
			return false;
		try {

			try (CSVParser parser = getCSVParser(stream, options)) {
				for(var rec : parser) {};
				return true;
			}
		} catch (Exception e) {
			log.error("Not parsable csv", e);
			return false;
		} finally {
			try {
				stream.close();
			} catch (IOException e) {
				log.error("Error closing stream");
			}
		}
	}
}
