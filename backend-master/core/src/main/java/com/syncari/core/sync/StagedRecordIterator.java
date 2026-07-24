package com.syncari.core.sync;

import com.syncari.core.model.StagedBatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Iterator;
import java.util.function.Function;

public class StagedRecordIterator implements Iterator<Page<StagedBatchRecord>> {

    private Page<StagedBatchRecord> currentDataPage;

    private Function<Pageable, Page<StagedBatchRecord>> pageGenerator;
    Pageable currentPage;

    public StagedRecordIterator(Function<Pageable, Page<StagedBatchRecord>> pageGenerator, int pageSize, Sort sort) {
        this.pageGenerator = pageGenerator;
        currentPage = PageRequest.of(0, pageSize, sort);
        currentDataPage = pageGenerator.apply(currentPage);
    }

    @Override
    public boolean hasNext() {
        return currentDataPage.getNumberOfElements()> 0;
    }

    @Override
    public Page<StagedBatchRecord> next() {
        var temp = currentDataPage;
        var nextPage = currentPage.next();
        currentDataPage = pageGenerator.apply(nextPage);
        currentPage = nextPage;
        return temp;
    }

}
