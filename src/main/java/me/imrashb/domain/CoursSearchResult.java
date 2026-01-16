package me.imrashb.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursSearchResult {
    private String sigle;
    private String titre;
    private String description;
    private Integer credits;
    private Double score; // BM25 relevance score
    private String url; // URL to the course page on ETS website
}
