/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at license/ESCIDOC.LICENSE
 * or http://www.escidoc.de/license.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at license/ESCIDOC.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2006-2008 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.  
 * All rights reserved.  Use is subject to license terms.
 */
package de.escidoc.core.test.om.item;

import de.escidoc.core.common.exceptions.remote.application.missing.MissingMethodParameterException;
import de.escidoc.core.common.exceptions.remote.application.notfound.ComponentNotFoundException;
import de.escidoc.core.common.exceptions.remote.application.notfound.ItemNotFoundException;
import de.escidoc.core.test.EscidocAbstractTest;
import de.escidoc.core.test.common.client.servlet.Constants;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * @author Michael Schneider
 */
public class ItemRetrieveComponentPropertiesIT extends ItemTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemRetrieveComponentPropertiesIT.class);

    private static String itemId = null;

    private static String itemXml = null;

    private static Document createdItem = null;

    private static String componentId = null;

    /**
     * Set up servlet test.
     *
     * @throws Exception If anything fails.
     */
    @Before
    public void setUp() throws Exception {
        itemXml =
            EscidocAbstractTest.getTemplateAsString(TEMPLATE_ITEM_PATH + "/rest", "escidoc_item_198_for_create.xml");
        createdItem = EscidocAbstractTest.getDocument(create(itemXml));
        itemId = getObjidValue(createdItem);
        componentId = getObjidValue(createdItem, "/item/components/component[1]");
    }

    /**
     * Test successfully retrieving the properties of a component of an item.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testOMRCP1() throws Exception {
        componentId =
            getObjidValue(createdItem, "/item/components/component[md-records/md-record[@name='escidoc']//description]");

        String properties = retrieveComponentProperties(itemId, componentId);

        Node escidocMdRecordWithDescription =
            selectSingleNode(EscidocAbstractTest.getDocument(itemXml),
                "/item/components/component/md-records/md-record[@name='escidoc' and *//description]");
        String escidocMdRecordWithDescriptionTemplate = toString(escidocMdRecordWithDescription, false);

        String templateProperties =
            toString(selectSingleNode(EscidocAbstractTest.getDocument(itemXml),
                "/item/components/component[md-records/md-record[@name='escidoc']//description]/properties"), true);
        assertComponentProperties(properties, templateProperties, escidocMdRecordWithDescriptionTemplate, "/ir/item/"
            + itemId + "/components/component/" + componentId + "/properties",
            getLastModificationDateValue(createdItem), startTimestamp);

        componentId =
            getObjidValue(createdItem,
                "/item/components/component[not(md-records/md-record[@name='escidoc']//description)]");

        properties = retrieveComponentProperties(itemId, componentId);

        Node escidocMdRecordWithoutDescription =
            selectSingleNode(EscidocAbstractTest.getDocument(itemXml),
                "/item/components/component/md-records/md-record[@name='escidoc' and not(*//description)]");
        String escidocMdRecordWithoutDescriptionTemplate = toString(escidocMdRecordWithoutDescription, false);
        assertComponentProperties(properties, toString(selectSingleNode(EscidocAbstractTest.getDocument(itemXml),
            "/item/components/component[not(md-records/md-record[@name='escidoc']//description)]/properties"), true),
            escidocMdRecordWithoutDescriptionTemplate, "/ir/item/" + itemId + "/components/component/" + componentId
                + "/properties", getLastModificationDateValue(createdItem), startTimestamp);

    }

    /**
     * Test retrieving the properties of a component of an unknown item.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testOMRCP2a() throws Exception {
        Class<?> ec = ItemNotFoundException.class;
        try {
            retrieveComponentProperties("unknown", componentId);
            fail(ec + " expected but no error occurred!");
        }
        catch (final Exception e) {
            assertExceptionType(ec.getName() + " expected.", ec, e);
        }

    }

    /**
     * Test retrieving the properties of an unknown component of an item.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testOMRCP2b() throws Exception {
        Class<?> ec = ComponentNotFoundException.class;
        try {
            retrieveComponentProperties(itemId, "unknown");
            fail(ec + " expected but no error occurred!");
        }
        catch (final Exception e) {
            assertExceptionType(ec.getName() + " expected.", ec, e);
        }

    }

    /**
     * Test retrieving the properties the properties of a component of an item with missing item id.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testOMRCP3a() throws Exception {
        Class<?> ec = MissingMethodParameterException.class;
        try {
            retrieveComponentProperties(null, componentId);
            fail(ec + " expected but no error occurred!");
        }
        catch (final Exception e) {
            assertExceptionType(ec.getName() + " expected.", ec, e);
        }

    }

    /**
     * Test retrieving the properties the properties of a component of an item with missing component id.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testOMRCP3b() throws Exception {
        Class<?> ec = MissingMethodParameterException.class;
        try {
            retrieveComponentProperties(itemId, null);
            fail(ec + " expected but no error occurred!");
        }
        catch (final Exception e) {
            assertExceptionType(ec.getName() + " expected.", ec, e);
        }
    }

    /**
     * Assert the xmlComponentProperties match the expected xmlTemplateComponentProperties.
     *
     * @param xmlComponentProperties         The retrieved properties.
     * @param xmlTemplateComponentProperties The expected properties.
     * @param templateComponentEscidocMdRecord
     *                                       TODO
     * @param expectedHRef                   The expected href.
     * @param expectedLastModificationTimestamp
     *                                       The last-modification timestamp of the item.
     * @param timestampBeforeCreation        A timestamp before the creation of the item/component.
     * @throws Exception If anything fails.
     */
    private void assertComponentProperties(
        final String xmlComponentProperties, final String xmlTemplateComponentProperties,
        final String templateComponentEscidocMdRecord, final String expectedHRef,
        final String expectedLastModificationTimestamp, final String timestampBeforeCreation) throws Exception {

        Document createdProperties = EscidocAbstractTest.getDocument(xmlComponentProperties);
        String href = getRootElementHrefValue(createdProperties);
        if ("".equals(href)) {
            href = null;
        }
        assertNotNull("Component Properties error: href attribute was not set!", href);
        assertEquals("Component Properties error: href has wrong value!", expectedHRef, href);
        String rootLastModificationDate = getLastModificationDateValue(createdProperties);
        if ("".equals(rootLastModificationDate)) {
            rootLastModificationDate = null;
        }
        assertNotNull("Component Properties error: last-modification-date attribute " + "was not set!",
            rootLastModificationDate);
        assertXmlExists("Component Properties error: creation-date was not set!", createdProperties,
            "/properties/creation-date");
        // assertXmlExists(
        // "Component Properties error: last-modification-date was not set "
        // + "in properties!", createdProperties,
        // "/properties/last-modification-date");
        // assertXmlExists(
        // "Component Properties error: valid-status was not set!",
        // createdProperties, "/properties/valid-status");
        assertXmlExists("Component Properties error: visibilty was not set in properties!", createdProperties,
            "/properties/visibility");
        assertXmlExists("Component Properties error: creator was not set in properties!", createdProperties,
            "/properties/created-by");
        assertXmlExists("Component Properties error: content-category was not set in " + "properties!",
            createdProperties, "/properties/content-category");
        // assertXmlExists(
        // "Component Properties error: file-name was not set in properties!",
        // createdProperties, "/properties/file-name");

        // assertXmlExists(
        // "Component Properties error: file-size was not set in properties!",
        // createdProperties, "/properties/file-size");

        String creationDate = selectSingleNode(createdProperties, "/properties/creation-date").getTextContent();
        LOGGER.debug("assertTimestampIsEqualOrAfter( " + creationDate + ", " + timestampBeforeCreation + ")");
        assertTimestampIsEqualOrAfter("Component Properties error: creation-date is not as expected!", creationDate,
            timestampBeforeCreation);

        assertReferencingElement("Invalid created-by. ", createdProperties, "/properties/created-by",
            Constants.USER_ACCOUNT_BASE_URI);

        Document template = EscidocAbstractTest.getDocument(xmlTemplateComponentProperties);
        Document mdRecord = EscidocAbstractTest.getDocument(templateComponentEscidocMdRecord);
        if (selectSingleNode(mdRecord, "/md-record//description") != null) {
            assertXmlExists("Component Properties error: description was not set in properties!", createdProperties,
                "/properties/description");

            String decriptionInProperties =
                selectSingleNode(createdProperties, "/properties/description").getTextContent();
            String decriptionInEscidocmdRecord = selectSingleNode(mdRecord, "/md-record//description").getTextContent();
            assertEquals("Component Properties error: description was changed!", decriptionInProperties,
                decriptionInEscidocmdRecord);
        }
        if (selectSingleNode(mdRecord, "/md-record//title") != null) {
            assertXmlExists("Component Properties error: 'file-name' was not set in properties!", createdProperties,
                "/properties/file-name");

            String fileNameInProperties = selectSingleNode(createdProperties, "/properties/file-name").getTextContent();
            String fileNameInEscidocmdRecord = selectSingleNode(mdRecord, "/md-record//title").getTextContent();
            assertEquals("Component Properties error: description was changed!", fileNameInProperties,
                fileNameInEscidocmdRecord);
        }
        assertXmlEquals("Component Properties error: status was changed!", template, createdProperties,
            "/properties/status");
        assertXmlEquals("Component Properties error: visibility was changed!", template, createdProperties,
            "/properties/visibility");

        assertXmlEquals("Component Properties error: content-category was changed!", template, createdProperties,
            "/properties/content-category");

        if (selectSingleNode(template, "/properties/mime-type") != null) {
            assertXmlExists("Component Properties error: mime-type was not set in properties!", createdProperties,
                "/properties/mime-type");
            assertXmlEquals("Component Properties error: mime-type was changed!", template, createdProperties,
                "/properties/mime-type");
        }
    }

    /**
     * Test if the component properties element wrongly contains conditional root attributes when retrieving a
     * component.
     */
    @Test
    public void testIssue1021() throws Exception {
        final Document component = EscidocAbstractTest.getDocument(retrieveComponent(itemId, componentId));

        assertXmlNotExists("properties element contains conditional root attribute", component,
            "/component/properties/@last-modification-date");

        final Document componentProperties =
            EscidocAbstractTest.getDocument(retrieveComponentProperties(itemId, componentId));

        assertXmlExists("properties element does not contain conditional root attribute", componentProperties,
            "/properties/@last-modification-date");
    }
}
