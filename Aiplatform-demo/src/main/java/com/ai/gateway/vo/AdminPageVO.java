package com.ai.gateway.vo;

import lombok.Data;

import java.util.List;

@Data
public class AdminPageVO<T> {
    private List<T> records;
    private long total;
    private long page;
    private long size;
}
