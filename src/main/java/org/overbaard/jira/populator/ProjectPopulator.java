package org.overbaard.jira.populator;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ProjectPopulator {
    private final RestClientFactory factory;
    private final boolean deleteExistingProjects;

    private ProjectPopulator(RestClientFactory factory, boolean deleteExistingProjects) {
        this.factory = factory;
        this.deleteExistingProjects = deleteExistingProjects;
    }


    public static ProjectPopulator createProjects(RestClientFactory factory, boolean deleteExistingProjects) {
        ProjectPopulator populator = new ProjectPopulator(factory, deleteExistingProjects);
        populator.create();
        return populator;
    }

    private void create() {
        List<ProjectInfo> projects = new ArrayList<>();
        projects.add(new ProjectInfo("FEAT", "Feature", new String[]{"1.0.0", "2.0.0", "2.0.2"}));
        projects.add(new ProjectInfo("SUP", "Support", new String[]{"1.0.0", "1.0.1", "1.0.2"}));
        projects.add(new ProjectInfo("UP", "Upstream", new String[]{"1.0.0", "2.0.0", "3.0.0"}));

        for (ProjectInfo projectInfo : projects) {
            if (projectExists(projectInfo)) {
                if (!deleteExistingProjects) {
                    continue;
                }
                deleteProject(projectInfo);
            }
            if (!projectExists(projectInfo)) {
                createProject(projectInfo);
                if (projectInfo.key.equals("FEAT") || projectInfo.key.equals("SUP")) {
                    List<String> components = new ArrayList<>();
                    components.add("Another Component");
                    components.add("User Experience");
                    components.add("Jira");
                    components.add("Core");
                    components.add("Testsuite");
                    components.add("Backend");
                    if (projectInfo.key.equals("FEAT")) {
                        components.add("FEAT Component");
                    }
                    for (String component : components) {
                        createComponents(projectInfo, component);
                    }
                    for (String fixVersion : projectInfo.versions) {
                        createFixVersion(projectInfo, fixVersion);
                    }
                }
                createIssues(projectInfo);
            }
        }
    }

    private boolean projectExists(ProjectInfo projectInfo) {
        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("project").path(projectInfo.key);
        Response response = factory.get(builder, false);
        if (response.getStatus() == 200) {
            // User is already there
            return true;
        } else if (response.getStatus() == 404) {
            // Create the user
            return false;
        } else {
            throw new RuntimeException("Error looking for user " + projectInfo.key + ". " + response.getStatus() + " " + response.readEntity(String.class));
        }
    }

    private void deleteProject(ProjectInfo projectInfo) {
        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("project").path(projectInfo.key);
        factory.delete(builder);
    }

    private void createProject(ProjectInfo projectInfo) {
        ModelNode project = new ModelNode();
        project.get("key").set(projectInfo.key);
        project.get("name").set(projectInfo.name);
        project.get("projectTypeKey").set("software");
        project.get("projectTemplateKey").set("com.pyxis.greenhopper.jira:gh-kanban-template");
        project.get("lead").set("admin");
        project.get("assigneeType").set("PROJECT_LEAD");

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("project");
        factory.post(builder, project);

        builder = factory.getJiraRestUriBuilder();
        builder.path("project").path(projectInfo.key);
        Response response = factory.get(builder, true);
        ModelNode projectNode = ModelNode.fromJSONString(response.readEntity(String.class));
        projectInfo.id = projectNode.get("id").asInt();
        System.out.println("Project id " + projectInfo.id);
    }

    private void createComponents(ProjectInfo projectInfo, String componentName) {
        ModelNode component = new ModelNode();
        component.get("name").set(componentName);
        component.get("description").set(componentName);
        component.get("leadUserName").set("admin");
        component.get("assigneeType").set("PROJECT_LEAD");
        component.get("isAssigneeTypeValid").set(false);
        component.get("project").set(projectInfo.key);

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("component");
        factory.post(builder, component);
    }

    private void createFixVersion(ProjectInfo projectInfo, String fixVersion) {
        ModelNode version = new ModelNode();
        version.get("project").set(projectInfo.key);
        version.get("name").set(fixVersion);
        version.get("description").set("Version " + fixVersion);

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("version");
        factory.post(builder, version);
    }


    private void createIssues(ProjectInfo projectInfo) {
        //For most of these we can get away with the string variety, but project seemingly needs to be id

        // Run http://localhost:2990/jira/rest/api/2/issue/createmeta?projectKeys=FEAT&expand=projects.issuetypes.fields to find all the fields needed

        // TODO
        // Issue Type
        // Assignee
        // Component
        // Labels
        // Custom Fields (Tester + Writer)
        // Parallel Tasks
        createIssue(projectInfo, new IssueInfo("Testing 123", "Task", "kabir", "admin", "Medium"));

    }

    private String createIssue(ProjectInfo projectInfo, IssueInfo issueInfo) {
        //For most of these we can get away with the string variety, but project seemingly needs to be id
        ModelNode issue = new ModelNode();
        issue.get("fields", "project", "id").set(projectInfo.id);
        issue.get("fields", "summary").set(issueInfo.summary);
        issue.get("fields", "issuetype", "name").set(issueInfo.issueType);
        issue.get("fields", "assignee", "name").set(issueInfo.assignee);
        issue.get("fields", "reporter", "name").set(issueInfo.reporter);
        issue.get("fields", "priority", "name").set(issueInfo.priority);

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("issue");
        Response response = factory.post(builder, issue);
        ModelNode issueNode = ModelNode.fromJSONString(response.readEntity(String.class));
        return issueNode.get("key").asString();
    }

    static class ProjectInfo {
        private final String key;
        private final String name;
        private final String[] versions;
        private int id;
        // Since we're working on a fresh Jira instance we can just hardcode the issue types, priorities etc.
        private final String[] issueTypes = {"Task", "Story", "Bug", "Epic"};
        private final String[] priority = {"Lowest", "Low", "Medium", "High", "Highest"};

        public ProjectInfo(String key, String name, String[] versions) {
            this.key = key;
            this.name = name;
            this.versions = versions;
        }
    }

    static class IssueInfo {
        private final String summary;
        private final String issueType;
        private final String assignee;
        private final String reporter;
        private final String priority;

        public IssueInfo(String summary, String issueType, String assignee, String reporter, String priority) {
            this.summary = summary;
            this.issueType = issueType;
            this.assignee = assignee;
            this.reporter = reporter;
            this.priority = priority;
        }
    }
}
