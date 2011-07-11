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
package de.escidoc.core.test.aa;

import de.escidoc.core.common.exceptions.remote.application.security.AuthorizationException;
import de.escidoc.core.test.EscidocAbstractTest;
import de.escidoc.core.test.common.client.servlet.Constants;
import de.escidoc.core.test.security.client.PWCallback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.Document;

import java.util.Arrays;
import java.util.Collection;

/**
 * Test suite for the role user-account-inspector. user-account-inspector may -retrieve user-account (s)he has scope on
 *
 * @author Michael Hoppe
 */
@RunWith(Parameterized.class)
public class UserAccountInspectorIT extends GrantTestBase {

    /**
     * Initializes test-class with data.
     *
     * @return Collection with data.
     */
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { USER_ACCOUNT_HANDLER_CODE, PWCallback.ID_PREFIX + PWCallback.TEST_HANDLE },
            { USER_GROUP_HANDLER_CODE, USER_GROUP_WITH_GROUP_LIST_ID },
            { USER_GROUP_HANDLER_CODE, USER_GROUP_WITH_USER_LIST_ID },
            { USER_GROUP_HANDLER_CODE, USER_GROUP_WITH_OU_LIST_ID },
            { USER_GROUP_HANDLER_CODE, USER_GROUP_WITH_EXTERNAL_SELECTOR } });
    }

    protected static final String HANDLE = PWCallback.TEST_HANDLE;

    protected static final String LOGINNAME = HANDLE;

    protected static final String PASSWORD = PWCallback.PASSWORD;

    protected static String grantCreationUserOrGroupId = null;

    private static UserAccountTestBase userAccountTestBase = null;

    private static int methodCounter = 0;

    private static String userId = null;

    private static String userId1 = null;

    /**
     * The constructor.
     *
     * @param handlerCode   handlerCode of either UserAccountHandler or UserGroupHandler.
     * @param userOrGroupId userOrGroupId for grantCreation.
     * @throws Exception If anything fails.
     */
    public UserAccountInspectorIT(final int handlerCode, final String userOrGroupId) throws Exception {
        super(handlerCode);
        grantCreationUserOrGroupId = userOrGroupId;
        userAccountTestBase = new UserAccountTestBase() {
        };
    }

    /**
     * Set up servlet test.
     *
     * @throws Exception If anything fails.
     */
    @Before
    public void initialize() throws Exception {
        if (methodCounter == 0) {
            //revoke all Grants
            revokeAllGrants(grantCreationUserOrGroupId);
            prepare();
        }
    }

    /**
     * Clean up after servlet test.
     *
     * @throws Exception If anything fails.
     */
    @After
    public void deinitialize() throws Exception {
        methodCounter++;
        if (methodCounter == getTestAnnotationsCount()) {
            revokeAllGrants(grantCreationUserOrGroupId);
            methodCounter = 0;
        }
    }

    /**
     * prepare tests (create user-accounts).
     *
     * @throws Exception If anything fails.
     */
    public void prepare() throws Exception {
        String userXml = prepareUserAccount(PWCallback.DEFAULT_HANDLE, STATUS_ACTIVE);
        Document userDocument = EscidocAbstractTest.getDocument(userXml);
        userId = getObjidValue(userDocument);
        String lastModificationDate = getLastModificationDateValue(userDocument);

        userXml = prepareUserAccount(PWCallback.DEFAULT_HANDLE, STATUS_ACTIVE);
        userDocument = EscidocAbstractTest.getDocument(userXml);
        userId1 = getObjidValue(userDocument);

        //create grant user-account-inspector for user grantCreationUserOrGroupId 
        //and scope on userAccount
        doTestCreateGrant(null, grantCreationUserOrGroupId, Constants.USER_ACCOUNT_BASE_URI + "/" + userId,
            ROLE_HREF_USER_ACCOUNT_INSPECTOR, null);
    }

    /**
     * Tests successfully retrieving a user-account.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testRetrieveUserAccount() throws Exception {
        userAccountTestBase.doTestRetrieveUserAccount(HANDLE, userId, null);
    }

    /**
     * Tests declining retrieving a user-account.
     *
     * @throws Exception If anything fails.
     */
    @Test
    public void testDeclineRetrieveUserAccount() throws Exception {
        userAccountTestBase.doTestRetrieveUserAccount(HANDLE, userId1, AuthorizationException.class);
    }

}
