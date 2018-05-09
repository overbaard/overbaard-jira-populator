package org.overbaard.jira.populator;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class UserPopulator {

    private final RestClientFactory factory;

    UserPopulator(RestClientFactory factory) {
        this.factory = factory;
    }

    static UserPopulator createUsers(RestClientFactory factory) {
        UserPopulator populator = new UserPopulator(factory);
        populator.create();
        return populator;
    }

    private void create() {
        List<Integer> avatars = loadAvatars();

        List<UserInfo> userInfos = new ArrayList<>();
        userInfos.add(new UserInfo("kabir", "Kabir Khan"));
        userInfos.add(new UserInfo("rostislav", "Rostislav Svoboda"));
        userInfos.add(new UserInfo("james", "James Perkins"));
        userInfos.add(new UserInfo("jason", "Jason Greene"));
        userInfos.add(new UserInfo("brian", "Brian Stansberry"));
        userInfos.add(new UserInfo("stuart", "Stuart Douglas"));
        userInfos.add(new UserInfo("jeff", "Jeff Mesnil"));

        List<String> users = new ArrayList<>();

        int i = avatars.size() - 1;
        for (UserInfo userInfo : userInfos) {
            if (!userExists(userInfo)) {
                createUser(avatars, userInfo);
                setUserAvatar(userInfo.username, avatars.get(i));
                if (i > 0) {
                    i--;
                } else {
                    i = avatars.size() - 1;
                }
            }
            users.add(userInfo.username);
        }
    }

    private List<Integer> loadAvatars() {
        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("avatar/user/system");


        List<Integer> avatars = new ArrayList<>();
        ModelNode modelNode = ModelNode.fromJSONString(factory.get(builder, true).readEntity(String.class));

        for (ModelNode avatarNode : modelNode.get("system").asList()) {
            avatars.add(avatarNode.get("id").asInt());
        }
        return avatars;
    }

    private boolean userExists(UserInfo userInfo) {
        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("user").queryParam("username", userInfo.username);
        Response response = factory.get(builder, false);
        if (response.getStatus() == 200) {
            // User is already there
            return true;
        } else if (response.getStatus() == 404) {
            // Create the user
            return false;
        } else {
            throw new RuntimeException("Error looking for user " + userInfo.username + ". " + response.getStatus() + " " + response.readEntity(String.class));
        }
    }


    private void createUser(List<Integer> avatars, UserInfo userInfo) {
        ModelNode user = new ModelNode();
        user.get("name").set(userInfo.username);
        user.get("password").set(userInfo.username);
        user.get("emailAddress").set(userInfo.username + "@example.com");
        user.get("displayName").set(userInfo.fullName);

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("user");
        factory.post(builder, user);
    }

    private void setUserAvatar(String username, int avatarId) {

        ModelNode avatar = new ModelNode();
        avatar.get("id").set(avatarId);
        avatar.get("isSystemAvatar").set(true);
        avatar.get("isSelected").set(false);

        UriBuilder builder = factory.getJiraRestUriBuilder();
        builder.path("user").path("avatar").queryParam("username", username);
        factory.put(builder, avatar);
    }
    static class UserInfo {
        final String username;
        final String fullName;

        public UserInfo(String username, String fullName) {
            this.username = username;
            this.fullName = fullName;
        }

    }
}
