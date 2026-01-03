package com.file.manager.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class SummaryResponse {
    private String summary;
    private List<String> tags;
    private boolean isSensitive;
    private boolean isConfidential;
}
