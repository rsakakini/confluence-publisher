/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisher;
import org.sahli.asciidoc.confluence.publisher.client.ConfluencePublisherListener;
import org.sahli.asciidoc.confluence.publisher.client.PublishingStrategy;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluencePage;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocConfluenceConverter;
import org.sahli.asciidoc.confluence.publisher.converter.AsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.FolderBasedAsciidocPagesStructureProvider;
import org.sahli.asciidoc.confluence.publisher.converter.PageTitlePostProcessor;
import org.sahli.asciidoc.confluence.publisher.converter.PrefixAndSuffixPageTitlePostProcessor;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Map;

import static java.util.Collections.emptyMap;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
@Mojo(name = "publish")
public class AsciidocConfluencePublisherMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.directory}/asciidoc-confluence-publisher", readonly = true)
    private File confluencePublisherBuildFolder;

    @Parameter
    private File asciidocRootFolder;

    @Parameter(defaultValue = "UTF-8")
    private String sourceEncoding;

    @Parameter
    private String rootConfluenceUrl;

    @Parameter(defaultValue = "false")
    private boolean skipSslVerification;

    @Parameter(required = true)
    private String spaceKey;

    @Parameter(required = true)
    private String ancestorId;

    @Parameter(defaultValue = "APPEND_TO_ANCESTOR")
    private PublishingStrategy publishingStrategy;

    @Parameter
    private String versionMessage;

    @Parameter
    private String username;

    @Parameter
    private String password;

    @Parameter
    private String pageTitlePrefix;

    @Parameter
    private String pageTitleSuffix;

    @Parameter(defaultValue = "false")
    private boolean skip;

    @Parameter
    private Map<String, Object> attributes;

    @Override
    public void execute() throws MojoExecutionException {
        if (this.skip) {
            getLog().info("Publishing to Confluence skipped");
            return;
        }

        try {
            PageTitlePostProcessor pageTitlePostProcessor = new PrefixAndSuffixPageTitlePostProcessor(this.pageTitlePrefix, this.pageTitleSuffix);

            AsciidocPagesStructureProvider asciidocPagesStructureProvider = new FolderBasedAsciidocPagesStructureProvider(this.asciidocRootFolder.toPath(), Charset.forName(this.sourceEncoding));

            AsciidocConfluenceConverter asciidocConfluenceConverter = new AsciidocConfluenceConverter(this.spaceKey, this.ancestorId);
            Map<String, Object> attributes = this.attributes != null ? this.attributes : emptyMap();
            ConfluencePublisherMetadata confluencePublisherMetadata = asciidocConfluenceConverter.convert(asciidocPagesStructureProvider, pageTitlePostProcessor, this.confluencePublisherBuildFolder.toPath(), attributes);

            ConfluenceRestClient confluenceRestClient = new ConfluenceRestClient(this.rootConfluenceUrl, this.skipSslVerification, this.username, this.password);
            ConfluencePublisherListener confluencePublisherListener = new LoggingConfluencePublisherListener(getLog());

            ConfluencePublisher confluencePublisher = new ConfluencePublisher(confluencePublisherMetadata, this.publishingStrategy, confluenceRestClient, confluencePublisherListener, this.versionMessage);
            confluencePublisher.publish();
        } catch (Exception e) {
            getLog().error("Publishing to Confluence failed: " + e.getMessage());
            throw new MojoExecutionException("Publishing to Confluence failed", e);
        }
    }


    private static class LoggingConfluencePublisherListener implements ConfluencePublisherListener {

        private Log log;

        LoggingConfluencePublisherListener(Log log) {
            this.log = log;
        }

        @Override
        public void pageAdded(ConfluencePage addedPage) {
            log.info("Added page '" + addedPage.getTitle() + "' (id " + addedPage.getContentId() + ")");
        }

        @Override
        public void pageUpdated(ConfluencePage existingPage, ConfluencePage updatedPage) {
            log.info("Updated page '" + updatedPage.getTitle() + "' (id " + updatedPage.getContentId() + ", version " + existingPage.getVersion() + " -> " + updatedPage.getVersion() + ")");
        }

        @Override
        public void pageDeleted(ConfluencePage deletedPage) {
            log.info("Deleted page '" + deletedPage.getTitle() + "' (id " + deletedPage.getContentId() + ")");
        }

        @Override
        public void publishCompleted() {
        }

    }

}
