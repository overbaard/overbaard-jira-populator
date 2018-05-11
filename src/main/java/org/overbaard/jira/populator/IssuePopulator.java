package org.overbaard.jira.populator;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jboss.dmr.ModelNode;
import org.overbaard.jira.populator.ProjectPopulator.ProjectInfo;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class IssuePopulator {
    private final RestClientFactory factory;
    private final int numberIssues;
    private final ProjectInfo projectInfo;
    private final String[] assignees;

    private String[] issueKeys;

    private static final String[] SUMMARY_SNIPPETS = {
            "Implement and test",
            "Implement, test and document",
            "This is a necessary feature wanted by several customers all over the world",
            "Popular demand is big for this",
            "Figure it out"
    };

    private IssuePopulator(RestClientFactory factory, int numberIssues, ProjectInfo projectInfo, String[] assignees) {
        this.factory = factory;
        this.numberIssues = numberIssues;
        this.projectInfo = projectInfo;
        this.assignees = assignees;
    }

    static IssuePopulator createIssues(RestClientFactory factory, int numberIssues, ProjectInfo projectInfo, String[] assignees) {
        IssuePopulator populator = new IssuePopulator(factory, numberIssues, projectInfo, assignees);
        populator.createIssues(projectInfo);
        return populator;
    }

    private void createIssues(ProjectInfo projectInfo) {
        //For most of these we can get away with the string variety, but project seemingly needs to be id

        // Run http://localhost:2990/jira/rest/api/2/issue/createmeta?projectKeys=FEAT&expand=projects.issuetypes.fields to find all the fields needed

        // TODO
        // Custom Fields (Tester + Writer)
        // Parallel Tasks
        // TODO linked issues

        List<String> issueKeys = new ArrayList<>();
        for (int i = 0; i < numberIssues; i++) {
            IssueInfo issueInfo = createIssueInfo(projectInfo, i);
            issueKeys.add(createIssue(projectInfo, issueInfo));
        }
        this.issueKeys = issueKeys.toArray(new String[issueKeys.size()]);
    }

    private IssueInfo createIssueInfo(ProjectInfo projectInfo, int issueIndex) {
        String summary = "Issue number " + (issueIndex + 1) + ". ";
        summary += getFieldFromIssueIndex(SUMMARY_SNIPPETS, issueIndex);

        String[] components = getPossiblyNoneOrMultiple(projectInfo.getComponents(), issueIndex, 7, 10);
        String[] labels = getPossiblyNoneOrMultiple(projectInfo.getLabels(), issueIndex, 4, 5);

        IssueInfo info = new IssueInfo(
                summary,
                getFieldFromIssueIndex(projectInfo.getIssueTypes(), issueIndex),
                getFieldFromIssueIndex(assignees, issueIndex),
                "admin",
                getFieldFromIssueIndex(projectInfo.getPriority(), issueIndex),
                components,
                labels
        );

        return info;
    }

    private String[] getPossiblyNoneOrMultiple(String[] values, int issueIndex, int none, int multiple) {
        List<String> ret = new ArrayList<>();
        if (values != null) {
            if (issueIndex % none == 0) {
                // Skip entries for some
            } else {
                ret.add(getFieldFromIssueIndex(values, issueIndex));
                if (issueIndex % multiple == 0) {
                    //Add another entry for every few of these
                    ret.add(getFieldFromIssueIndex(values, (issueIndex / multiple) + 1));
                }
            }
        }
        return ret.toArray(new String[ret.size()]);
    }

    private String getFieldFromIssueIndex(String[] values, int issueIndex) {
        int i = issueIndex % values.length;
        return values[i];
    }

    private String createIssue(ProjectInfo projectInfo, IssueInfo issueInfo) {
        System.out.println("Creating issue...");
        //For most of these we can get away with the string variety, but project seemingly needs to be id
        ModelNode issue = new ModelNode();
        issue.get("fields", "project", "id").set(projectInfo.getId());
        issue.get("fields", "summary").set(issueInfo.summary);
        issue.get("fields", "issuetype", "name").set(issueInfo.issueType);
        issue.get("fields", "assignee", "name").set(issueInfo.assignee);
        issue.get("fields", "reporter", "name").set(issueInfo.reporter);
        issue.get("fields", "priority", "name").set(issueInfo.priority);

        for (String component : issueInfo.components) {
            ModelNode componentEntry = new ModelNode();
            componentEntry.get("name").set(component);
            issue.get("fields", "components").add(componentEntry);
        }

        for (String label : issueInfo.labels) {
            issue.get("fields", "labels").add(label);
        }

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("issue");
        Response response = factory.post(builder, issue);
        ModelNode issueNode = ModelNode.fromJSONString(response.readEntity(String.class));
        String issueKey = issueNode.get("key").asString();
        System.out.println("Created issue " + issueKey);
        return issueKey;
    }


    static class IssueInfo {
        private final String summary;
        private final String issueType;
        private final String assignee;
        private final String reporter;
        private final String priority;
        private final String[] components;
        private final String[] labels;

        public IssueInfo(String summary, String issueType, String assignee, String reporter, String priority, String[] components, String[] labels) {
            this.summary = summary;
            this.issueType = issueType;
            this.assignee = assignee;
            this.reporter = reporter;
            this.priority = priority;
            this.components = components;
            this.labels = labels;
        }
    }
}
