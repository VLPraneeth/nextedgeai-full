package com.syncari.connector.service.googlesheets;

import com.syncari.connector.data.Partition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Wither;

import java.time.ZonedDateTime;

@AllArgsConstructor
@Data
@Wither
public class SheetInfo {
    public SheetInfo() {

    }

    String spreadsheetId;
    String spreadsheetName;
    String sheetName;
    Partition partition;
    ZonedDateTime lastModifiedTime;
    ZonedDateTime createdTime;
}
