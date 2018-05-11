/*
 *
 *  JBoss, Home of Professional Open Source
 *  Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 *  by the @authors tag.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.overbaard.jira.populator;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.DatatypeConverter;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.jboss.dmr.ModelNode;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;

/**
 * @author Kabir Khan
 */
class RestClientFactory implements AutoCloseable {

    private static final Charset CHARACTER_SET = Charset.forName("iso-8859-1");

    private final String jiraUri;
    private final String username;
    private final String password;

    //Set this for each request
    private volatile long lastAccess = System.currentTimeMillis();
    private volatile long nextRefresh = 0;
    //Lazy initialize this for requests which need it, and clear it when the request is done'
    private volatile Client client = null;

    RestClientFactory(String jiraUri, String username, String password) {
        this.jiraUri = jiraUri.charAt(jiraUri.length() - 1) == '/' ? jiraUri : jiraUri + "/";
        this.username = username;
        this.password = password;
    }

    private Client getClient() {
        if (client == null) {
            HttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            HttpClient httpClient = HttpClientBuilder.create().build();
            ApacheHttpClient4Engine engine = new ApacheHttpClient4Engine(httpClient);
            client = ((ResteasyClientBuilder) ClientBuilder.newBuilder())
                    .httpEngine(engine)
                    .register(new Authenticator())
                    .build();
        }
        return client;
    }

    String getJiraUri() {
        return this.jiraUri;
    }

    UriBuilder getJiraUriBuilder() {
        return UriBuilder.fromUri(jiraUri);
    }

    UriBuilder getJiraRestUriBuilder() {
        return getJiraUriBuilder().path("rest").path("api").path("2");
    }


    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
        client = null;
    }

    Response get(UriBuilder builder, boolean error) {
        try {
            final WebTarget target = getClient().target(builder);
            final Response response = target.request(MediaType.APPLICATION_JSON_TYPE).get();

            if (error && response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new RuntimeException("Error looking up the avatars: " + response.readEntity(String.class));
            }
            return response;
        } finally {
            // Close the client
            close();
        }
    }

    public Response post(UriBuilder builder, ModelNode payload) {
        try {
            final WebTarget target = getClient().target(builder);
            final Response response =
                    target.request(MediaType.APPLICATION_JSON_TYPE).post(Entity.json((payload.toJSONString(true))));

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new RuntimeException(response.readEntity(String.class));
            }
            return response;
        } finally {
            // Close the client
            close();
        }
    }

    public Response put(UriBuilder builder, ModelNode payload) {
        try {
            final WebTarget target = getClient().target(builder);
            final Response response =
                    target.request(MediaType.APPLICATION_JSON_TYPE).put(Entity.json((payload.toJSONString(true))));

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new RuntimeException(response.readEntity(String.class));
            }
            return response;
        } finally {
            // Close the client
            close();
        }
    }

    public Response delete(UriBuilder builder) {
        try {
            final WebTarget target = getClient().target(builder);
            final Response response =
                    target.request(MediaType.APPLICATION_JSON_TYPE).delete();

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new RuntimeException(response.readEntity(String.class));
            }
            return response;
        } finally {
            // Close the client
            close();
        }
    }

    private class Authenticator implements ClientRequestFilter {

        public Authenticator() {
        }

        public void filter(ClientRequestContext requestContext) throws IOException {
            MultivaluedMap<String, Object> headers = requestContext.getHeaders();
            final String basicAuthentication = getBasicAuthentication();
            headers.add("Authorization", basicAuthentication);

        }

        private String getBasicAuthentication() {
            String token = username + ":" + password;
            try {
                return "Basic " +
                        DatatypeConverter.printBase64Binary(token.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                throw new IllegalStateException("Cannot encode with UTF-8", ex);
            }
        }
    }
}
