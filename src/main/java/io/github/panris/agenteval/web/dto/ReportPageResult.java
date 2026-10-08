package io.github.panris.agenteval.web.dto;

import java.util.List;
import java.util.Map;

/**
 * 报告分页查询结果强类型封装。
 * 取代 ReportService/QueryService 中散落的 {@code Map<String, Object>}，
 * JSON 序列化形状与旧 Map 完全一致（reports/total/filtered/page/size/totalPages），
 * 因此前端无需改动。
 */
public class ReportPageResult {

    private List<Map<String, Object>> reports;
    private long total;
    private long filtered;
    private int page;
    private int size;
    private int totalPages;

    public ReportPageResult() {
    }

    public ReportPageResult(List<Map<String, Object>> reports, long total, long filtered,
                            int page, int size, int totalPages) {
        this.reports = reports;
        this.total = total;
        this.filtered = filtered;
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
    }

    public List<Map<String, Object>> getReports() {
        return reports;
    }

    public void setReports(List<Map<String, Object>> reports) {
        this.reports = reports;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getFiltered() {
        return filtered;
    }

    public void setFiltered(long filtered) {
        this.filtered = filtered;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
