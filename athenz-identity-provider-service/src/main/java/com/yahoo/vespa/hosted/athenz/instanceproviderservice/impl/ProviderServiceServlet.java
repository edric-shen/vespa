// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.impl.model.InstanceConfirmation;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A Servlet implementing the Athenz Service Provider InstanceConfirmation API
 *
 * @author bjorncs
 */
public class ProviderServiceServlet extends HttpServlet {

    private static final Logger log = Logger.getLogger(ProviderServiceServlet.class.getName());

    private final InstanceValidator instanceValidator;
    private final IdentityDocumentGenerator identityDocumentGenerator;

    public ProviderServiceServlet(InstanceValidator instanceValidator, IdentityDocumentGenerator identityDocumentGenerator) {
        this.instanceValidator = instanceValidator;
        this.identityDocumentGenerator = identityDocumentGenerator;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO Validate that request originates from ZTS
        try {
            String confirmationContent = toString(req.getReader());
            log.log(LogLevel.DEBUG, () -> "Confirmation content: " + confirmationContent);
            InstanceConfirmation instanceConfirmation =
                    Utils.getMapper().readValue(confirmationContent, InstanceConfirmation.class);
            log.log(LogLevel.DEBUG, () -> "Parsed confirmation content: " + instanceConfirmation.toString());
            if (!instanceValidator.isValidInstance(instanceConfirmation)) {
                log.log(LogLevel.ERROR, "Invalid instance: " + instanceConfirmation);
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            } else {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType("application/json");
                resp.getWriter().write(Utils.getMapper().writeValueAsString(instanceConfirmation));
            }
        } catch (JsonParseException | JsonMappingException e) {
            log.log(LogLevel.ERROR, "InstanceConfirmation is not valid JSON", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // TODO verify tls client cert
        String hostname = req.getParameter("hostname");
        try {
            String signedIdentityDocument = identityDocumentGenerator.generateSignedIdentityDocument(hostname);
            resp.setContentType("application/json");
            PrintWriter writer = resp.getWriter();
            writer.print(signedIdentityDocument);
            writer.flush();
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, String.format("Unable to generate identity doument [%s]", e.getMessage()));
        }
    }

    private static String toString(Reader reader) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            return bufferedReader.lines().collect(Collectors.joining("\n"));
        }
    }

}
