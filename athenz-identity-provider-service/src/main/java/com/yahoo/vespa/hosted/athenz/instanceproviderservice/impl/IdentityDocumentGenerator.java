// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.config.AthenzProviderServiceConfig;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.IdentityDocument;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.ProviderUniqueId;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.SignedIdentityDocument;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

/**
 * @author mortent
 */
public class IdentityDocumentGenerator {

    private final NodeRepository nodeRepository;
    private final Zone zone;
    private final KeyProvider keyProvider;
    private final String dnsSuffix;
    private final String providerService;
    private final String ztsUrl;

    public IdentityDocumentGenerator(AthenzProviderServiceConfig config, NodeRepository nodeRepository, Zone zone, KeyProvider keyProvider) {
        this.nodeRepository = nodeRepository;
        this.zone = zone;
        this.keyProvider = keyProvider;
        this.dnsSuffix = config.certDnsSuffix();
        this.providerService = config.serviceName();
        this.ztsUrl = config.ztsUrl();
    }

    public String generateSignedIdentityDocument(String hostname) {
        Node node = nodeRepository.getNode(hostname).orElseThrow(() -> new RuntimeException("Unable to find node " + hostname));
        try {
            IdentityDocument identityDocument = generateIdDocument(node);
            String identityDocumentString = Utils.getMapper().writeValueAsString(identityDocument);

            String encodedIdentityDocument =
                    Base64.getEncoder().encodeToString(identityDocumentString.getBytes());
            Signature sigGenerator = Signature.getInstance("SHA512withRSA");

            // TODO: Get the correct version 0 ok for now
            PrivateKey privateKey = Crypto.loadPrivateKey(keyProvider.getPrivateKey(0));
            sigGenerator.initSign(privateKey);
            sigGenerator.update(encodedIdentityDocument.getBytes());
            String signature = Base64.getEncoder().encodeToString(sigGenerator.sign());

            SignedIdentityDocument signedIdentityDocument = new SignedIdentityDocument(
                    encodedIdentityDocument,
                    signature,
                    SignedIdentityDocument.DEFAULT_KEY_VERSION,
                    identityDocument.providerUniqueId.asString(),
                    dnsSuffix,
                    providerService,
                    ztsUrl,
                    SignedIdentityDocument.DEFAILT_DOCUMENT_VERSION
            );
            return Utils.getMapper().writeValueAsString(signedIdentityDocument);
        } catch (Exception e) {
            throw new RuntimeException("Exception generating identity document: " + e.getMessage(), e);
        }
    }

    private IdentityDocument generateIdDocument(Node node) {
        Allocation allocation = node.allocation().orElseThrow(() -> new RuntimeException("No allocation for node " + node.hostname()));
        ProviderUniqueId providerUniqueId = new ProviderUniqueId(
                allocation.owner().tenant().value(),
                allocation.owner().application().value(),
                zone.environment().value(),
                zone.region().value(),
                allocation.owner().instance().value(),
                allocation.membership().cluster().id().value(),
                allocation.membership().index());

        return new IdentityDocument(
                providerUniqueId,
                "localhost", // TODO: Add configserver hostname
                node.hostname(),
                Instant.now());
    }
}

