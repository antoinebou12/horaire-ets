package me.imrashb.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursAutocompleteResult {
    private String sigle;
    private String titre;
    private Double score; // Similarity or relevance score
}
