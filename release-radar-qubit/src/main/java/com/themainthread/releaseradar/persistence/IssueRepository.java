package com.themainthread.releaseradar.persistence;

import static io.quarkiverse.qubit.Subqueries.subquery;

import java.time.LocalDateTime;
import java.util.List;

import com.themainthread.releaseradar.api.BlockerView;
import com.themainthread.releaseradar.api.ImpactOutlier;
import com.themainthread.releaseradar.api.ServiceHotspot;
import com.themainthread.releaseradar.domain.Issue;
import com.themainthread.releaseradar.domain.IssueSeverity;
import com.themainthread.releaseradar.domain.IssueStatus;

import io.quarkiverse.qubit.Group;
import io.quarkiverse.qubit.QubitRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class IssueRepository implements QubitRepository<Issue, Long> {

    public List<BlockerView> findBlockers(
            LocalDateTime cutoff,
            List<IssueSeverity> severities,
            int limit) {
        return where(issue -> issue.status == IssueStatus.OPEN)
                .where(issue -> severities.contains(issue.severity))
                .where(issue -> issue.openedAt.isBefore(cutoff))
                .sortedBy(issue -> issue.openedAt)
                .limit(limit)
                .select(issue -> new BlockerView(
                        issue.key,
                        issue.service,
                        issue.severity,
                        issue.openedAt,
                        issue.affectedUsers))
                .toList();
    }

    public List<ServiceHotspot> findHotspots(long minimumOpen) {
        return where(issue -> issue.status == IssueStatus.OPEN)
                .groupBy(issue -> issue.service)
                .having((Group<Issue, String> group) -> group.count() >= minimumOpen)
                .sortedDescendingBy((Group<Issue, String> group) -> group.count())
                .select((Group<Issue, String> group) -> new ServiceHotspot(
                        group.key(),
                        group.count(),
                        group.avg(issue -> issue.affectedUsers)))
                .toList();
    }

    public List<ImpactOutlier> findImpactOutliers() {
        return where(issue -> issue.status == IssueStatus.OPEN
                && issue.affectedUsers > subquery(Issue.class)
                        .where(candidate -> candidate.status == IssueStatus.OPEN)
                        .avg(candidate -> candidate.affectedUsers))
                .sortedDescendingBy(issue -> Integer.valueOf(issue.affectedUsers))
                .select(issue -> new ImpactOutlier(
                        issue.key,
                        issue.service,
                        issue.affectedUsers))
                .toList();
    }
}
