package com.example.collegeroitool.repository;

import com.example.collegeroitool.model.YcStartupCatalog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface YcStartupCatalogRepository extends JpaRepository<YcStartupCatalog, Long> {

    /** Matches by interest keyword (against industry/subindustry/tags) and, when a location
     *  pattern is given, against all_locations only — the caller (StartupLocatorService) is
     *  expected to pass an already-bounded pattern like ", GA," rather than a bare state name,
     *  since a bare name collides with unrelated text (e.g. "Georgia" the country). Both filters
     *  are optional so a search can be interest-only, location-only, or both. Ranked so
     *  currently-hiring companies surface before dormant listings — freshness on top of the
     *  static seed. */
    @Query("""
        SELECT c FROM YcStartupCatalog c
        WHERE (:keyword IS NULL OR
               LOWER(c.industry) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(c.subindustry) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(c.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:location IS NULL OR LOWER(c.allLocations) LIKE LOWER(CONCAT('%', :location, '%')))
          AND (:hiringOnly = FALSE OR c.hiring = TRUE)
        ORDER BY c.hiring DESC, c.teamSize DESC
        """)
    List<YcStartupCatalog> search(@Param("keyword") String keyword, @Param("location") String location,
                                   @Param("hiringOnly") boolean hiringOnly, Pageable pageable);
}
