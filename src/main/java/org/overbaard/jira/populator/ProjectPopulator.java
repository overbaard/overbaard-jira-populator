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
    private final String[] assignees;
    private final boolean deleteExistingProjects;

    private static final String[] FEAT_LABELS = {"ExtraTesting", "NeedsInfo", "Support", "Approved", "Retrospective"};
    private static final String[] SUP_LABELS = {"ExtraTesting", "Customer", "NeedsInfo", "Documentation"};

    private ProjectPopulator(RestClientFactory factory, String[] assignees, boolean deleteExistingProjects) {
        this.factory = factory;
        this.assignees = assignees;
        this.deleteExistingProjects = deleteExistingProjects;
    }


    public static ProjectPopulator createProjects(RestClientFactory factory, String[] assignees, boolean deleteExistingProjects) {
        ProjectPopulator populator = new ProjectPopulator(factory, assignees, deleteExistingProjects);
        populator.create();
        return populator;
    }

    private void create() {
        System.out.println("Creating projects....");
        List<ProjectInfo> projects = new ArrayList<>();
        projects.add(new ProjectInfo("UP", "Upstream", new String[]{"1.0.0", "2.0.0", "3.0.0"}));
        projects.add(new ProjectInfo("FEAT", "Feature", new String[]{"1.0.0", "2.0.0", "2.0.2"}));
        projects.add(new ProjectInfo("SUP", "Support", new String[]{"1.0.0", "1.0.1", "1.0.2"}));

        String[] upIssueKeys = null;
        for (ProjectInfo projectInfo : projects) {
            System.out.println("====== " + projectInfo.key);
            if (projectExists(projectInfo)) {
                if (!deleteExistingProjects) {
                    continue;
                }
                deleteProject(projectInfo);
            }
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
                projectInfo.components = components.toArray(new String[components.size()]);
                for (String component : components) {
                    createComponent(projectInfo, component);
                }
                for (String fixVersion : projectInfo.versions) {
                    createFixVersion(projectInfo, fixVersion);
                }
                projectInfo.labels = projectInfo.key.equals("FEAT") ? FEAT_LABELS : SUP_LABELS;
            }
            IssuePopulator issuePopulator = IssuePopulator.createIssues(factory, 30, projectInfo, assignees);
            String[] issueKeys = issuePopulator.getIssueKeys();
            if (projectInfo.key.equals("UP")) {
                // This is the first one in the list
                upIssueKeys = issueKeys;
            } else {
                linkIssues(projectInfo, issueKeys, upIssueKeys);
            }
        }
        System.out.println("Created projects");
    }

    private boolean projectExists(ProjectInfo projectInfo) {
        System.out.println("Checking if " + projectInfo.key + " exists...");
        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("project").path(projectInfo.key);
        Response response = factory.get(builder, false);
        if (response.getStatus() == 200) {
            System.out.println("Project " + projectInfo.key + " exists");
            return true;
        } else if (response.getStatus() == 404) {
            System.out.println("Project " + projectInfo.key + " does not exist");
            return false;
        } else {
            throw new RuntimeException("Error looking for user " + projectInfo.key + ". " + response.getStatus() + " " + response.readEntity(String.class));
        }
    }

    private void deleteProject(ProjectInfo projectInfo) {
        System.out.println("Deleting project " + projectInfo.key + "...");
        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("project").path(projectInfo.key);
        factory.delete(builder);
        System.out.println("Deleted project " + projectInfo.key);
    }

    private void createProject(ProjectInfo projectInfo) {
        System.out.println("Creating project " + projectInfo.key + "...");

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

        System.out.println("Created project " + projectInfo.key + "(" + projectInfo.id + ")");
    }

    private void createComponent(ProjectInfo projectInfo, String componentName) {
        System.out.println("Creating component " + componentName + "...");
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
        System.out.println("Created component " + componentName);
    }

    private void createFixVersion(ProjectInfo projectInfo, String fixVersion) {
        System.out.println("Creating fix version " + fixVersion + "...");
        ModelNode version = new ModelNode();
        version.get("project").set(projectInfo.key);
        version.get("name").set(fixVersion);
        version.get("description").set("Version " + fixVersion);

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("version");
        factory.post(builder, version);
        System.out.println("Created fix version " + fixVersion);
    }

    private void linkIssues(ProjectInfo projectInfo, String[] issueKeys, String[] upIssueKeys) {
        int start = projectInfo.key.equals("FEAT") ? 0 : 1;
        for (int i = start ; i < issueKeys.length ; i++) {
            ModelNode link = new ModelNode();
            link.get("type", "name").set("Blocks");
            link.get("inwardIssue", "key").set(upIssueKeys[i]);
            link.get("outwardIssue", "key").set(issueKeys[i]);

            System.out.println("Linking " + issueKeys[i] + " to " + upIssueKeys[i] + "...");
            UriBuilder builder = factory.getJiraRestUriBuilder();
            builder.path("issueLink");
            factory.post(builder, link);
            System.out.println("Linked " + issueKeys[i] + " to " + upIssueKeys[i]);
        }
    }


    static class ProjectInfo {
        private final String key;
        private final String name;
        private final String[] versions;
        private int id;
        // Since we're working on a fresh Jira instance we can just hardcode the issue types, priorities etc.
        private final String[] issueTypes = {"Task", "Story", "Bug"/*, "Epic"*/}; // Epic is a bit weird so leave that out
        private final String[] priority = {"Lowest", "Low", "Medium", "High", "Highest"};
        private String[] components;
        private String[] labels;

        public ProjectInfo(String key, String name, String[] versions) {
            this.key = key;
            this.name = name;
            this.versions = versions;
        }

        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }

        public String[] getVersions() {
            return versions;
        }

        public int getId() {
            return id;
        }

        public String[] getIssueTypes() {
            return issueTypes;
        }

        public String[] getPriority() {
            return priority;
        }

        public String[] getComponents() {
            return components;
        }

        public String[] getLabels() {
            return labels;
        }
    }
}
