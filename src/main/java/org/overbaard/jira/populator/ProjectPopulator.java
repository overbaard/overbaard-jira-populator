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

    ProjectPopulator(RestClientFactory factory) {
        this.factory = factory;
    }


    public void createProjects() {
        List<ProjectInfo> projects = new ArrayList<>();
        projects.add(new ProjectInfo("FEAT", "Feature"));
        projects.add(new ProjectInfo("SUP", "Support"));
        projects.add(new ProjectInfo("UP", "Upstream"));

        for (ProjectInfo projectInfo : projects) {
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
                }
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

    static class ProjectInfo {
        private final String key;
        private final String name;

        public ProjectInfo(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }
}
