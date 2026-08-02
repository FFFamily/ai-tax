package com.rcszh.tax.postprocess;

import com.rcszh.tax.postprocess.dividend.model.DividendCandidateRecord;

import java.util.List;

/** 单次记录后处理调用中的临时上下文，不参与结果序列化。 */
public class RecordPostProcessContext {
    private List<DividendCandidateRecord> dividendCandidates = List.of();
    private boolean dividendRecordsPrepared;

    public List<DividendCandidateRecord> getDividendCandidates() {
        return dividendCandidates;
    }

    public void setDividendCandidates(List<DividendCandidateRecord> dividendCandidates) {
        this.dividendCandidates = dividendCandidates == null ? List.of() : List.copyOf(dividendCandidates);
    }

    public boolean isDividendRecordsPrepared() {
        return dividendRecordsPrepared;
    }

    public void setDividendRecordsPrepared(boolean dividendRecordsPrepared) {
        this.dividendRecordsPrepared = dividendRecordsPrepared;
    }
}
