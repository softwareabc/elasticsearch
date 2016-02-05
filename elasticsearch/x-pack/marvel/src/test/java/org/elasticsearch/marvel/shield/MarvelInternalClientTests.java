/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.marvel.shield;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.action.ActionRequestBuilder;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.marvel.agent.exporter.MarvelTemplateUtils;
import org.elasticsearch.marvel.agent.settings.MarvelSettings;
import org.elasticsearch.marvel.test.MarvelIntegTestCase;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.shield.InternalClient;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.is;

public class MarvelInternalClientTests extends MarvelIntegTestCase {

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder()
                .put(super.nodeSettings(nodeOrdinal))
                .put(NetworkModule.HTTP_ENABLED.getKey(), false)
                .put(MarvelSettings.INTERVAL_SETTING.getKey(), "-1")
                .build();
    }

    public void testAllowedAccess() {
        InternalClient internalClient = internalCluster().getInstance(InternalClient.class);

        assertAccessIsAllowed(internalClient.admin().cluster().prepareHealth());
        assertAccessIsAllowed(internalClient.admin().cluster().prepareClusterStats());
        assertAccessIsAllowed(internalClient.admin().cluster().prepareState());
        assertAccessIsAllowed(internalClient.admin().cluster().prepareNodesInfo());
        assertAccessIsAllowed(internalClient.admin().cluster().prepareNodesStats());
        assertAccessIsAllowed(internalClient.admin().cluster().prepareNodesHotThreads());

        assertAccessIsAllowed(internalClient.admin().indices().prepareGetSettings());
        assertAccessIsAllowed(internalClient.admin().indices().prepareSegments());
        assertAccessIsAllowed(internalClient.admin().indices().prepareRecoveries());
        assertAccessIsAllowed(internalClient.admin().indices().prepareStats());

        assertAccessIsAllowed(internalClient.admin().indices().prepareDelete(MarvelSettings.MARVEL_INDICES_PREFIX));
        assertAccessIsAllowed(internalClient.admin().indices().prepareCreate(MarvelSettings.MARVEL_INDICES_PREFIX + "test"));

        assertAccessIsAllowed(internalClient.admin().indices().preparePutTemplate("foo")
                .setSource(MarvelTemplateUtils.loadTimestampedIndexTemplate()));
        assertAccessIsAllowed(internalClient.admin().indices().prepareGetTemplates("foo"));
    }

    public void testDeniedAccess() {
        InternalClient internalClient = internalCluster().getInstance(InternalClient.class);
        assertAcked(internalClient.admin().indices().preparePutTemplate("foo")
                .setSource(MarvelTemplateUtils.loadDataIndexTemplate()).get());

        if (shieldEnabled) {
            assertAccessIsDenied(internalClient.admin().indices().prepareDeleteTemplate("foo"));
            assertAccessIsDenied(internalClient.admin().cluster().prepareGetRepositories());
        } else {
            assertAccessIsAllowed(internalClient.admin().indices().prepareDeleteTemplate("foo"));
            assertAccessIsAllowed(internalClient.admin().cluster().prepareGetRepositories());
        }
    }

    public void assertAccessIsAllowed(ActionRequestBuilder request) {
        try {
            request.get();
        } catch (IndexNotFoundException e) {
            // Ok
        } catch (ElasticsearchSecurityException e) {
            fail("unexpected security exception: " + e.getMessage());
        }
    }

    public void assertAccessIsDenied(ActionRequestBuilder request) {
        try {
            request.get();
            fail("expected a security exception");
        } catch (IndexNotFoundException e) {
            // Ok
        } catch (ElasticsearchSecurityException e) {
            // expected
            assertThat(e.status(), is(RestStatus.FORBIDDEN));
        }
    }
}

