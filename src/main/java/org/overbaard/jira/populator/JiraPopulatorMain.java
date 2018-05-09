package org.overbaard.jira.populator;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class JiraPopulatorMain {
    final static String JIRA_URL_PROP_NAME = "ob.setup.jira.url";
    final static String JIRA_URL_DEFAULT = "http://localhost:2990/jira";
    final static String JIRA_USERNAME_PROP_NAME = "ob.setup.jira.username";
    final static String JIRA_USERNAME_DEFAULT = "admin";
    final static String JIRA_PASSWORD_PROP_NAME = "ob.setup.jira.password";
    final static String JIRA_PASSWORD_DEFAULT = "admin";

    public static void main(String[] args) throws Exception {
        final String jiraUrl = System.getProperty(JIRA_URL_PROP_NAME, JIRA_URL_DEFAULT);
        final String username = System.getProperty(JIRA_USERNAME_PROP_NAME, JIRA_USERNAME_DEFAULT);
        final String password = System.getProperty(JIRA_PASSWORD_PROP_NAME, JIRA_PASSWORD_DEFAULT);

        try (RestClientFactory factory = new RestClientFactory(jiraUrl, username, password)) {
            UserPopulator userPopulator = new UserPopulator(factory);
            userPopulator.createUsers();
            ProjectPopulator projectPopulator = new ProjectPopulator(factory);
            projectPopulator.createProjects();
        }
    }


}
