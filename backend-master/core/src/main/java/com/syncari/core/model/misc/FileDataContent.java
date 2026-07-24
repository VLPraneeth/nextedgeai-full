package com.syncari.core.model.misc;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileDataContent {
	List<String> headerColumns;
	List<List<String>> rows;

}
