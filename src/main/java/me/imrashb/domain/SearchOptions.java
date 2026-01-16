package me.imrashb.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Search filter options for course search operations.
 * All fields are optional - null values mean no filtering for that field.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchOptions {
    /**
     * List of programmes to filter by. Courses are matched by sigle prefix.
     * For example, Programme.LOG matches courses with sigle starting with "LOG".
     */
    private List<Programme> programmes;

    /**
     * Minimum credits filter (inclusive).
     * If specified, only courses with credits >= minCredits are included.
     */
    private Integer minCredits;

    /**
     * Maximum credits filter (inclusive).
     * If specified, only courses with credits <= maxCredits are included.
     */
    private Integer maxCredits;
}
