/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the Common Development and Distribution License, Version 1.0
 * only (the "License"). You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the license at license/ESCIDOC.LICENSE or http://www.escidoc.de/license. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each file and include the License file at
 * license/ESCIDOC.LICENSE. If applicable, add the following below this CDDL HEADER, with the fields enclosed by
 * brackets "[]" replaced with your own identifying information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 * Copyright 2006-2011 Fachinformationszentrum Karlsruhe Gesellschaft fuer wissenschaftlich-technische Information mbH
 * and Max-Planck-Gesellschaft zur Foerderung der Wissenschaft e.V. All rights reserved. Use is subject to license
 * terms.
 */

package de.escidoc.core.aa.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.escidoc.core.common.util.service.EscidocUserDetails;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.openid.OpenIDAttribute;
import org.springframework.security.openid.OpenIDAuthenticationToken;

import de.escidoc.core.aa.business.SecurityHelper;
import de.escidoc.core.aa.business.persistence.UserAccount;
import de.escidoc.core.aa.business.persistence.UserAccountDaoInterface;
import de.escidoc.core.aa.business.persistence.UserAttribute;
import de.escidoc.core.aa.business.persistence.UserLoginData;
import de.escidoc.core.aa.ldap.EscidocLdapUserDetails;
import de.escidoc.core.aa.openid.EscidocOpenidUserDetails;
import de.escidoc.core.aa.openid.EscidocOpenidUserDetailsService;
import de.escidoc.core.aa.shibboleth.ShibbolethUser;
import de.escidoc.core.common.exceptions.application.missing.MissingParameterException;
import de.escidoc.core.common.exceptions.system.SqlDatabaseSystemException;
import de.escidoc.core.common.exceptions.system.SystemException;
import de.escidoc.core.common.exceptions.system.WebserverSystemException;
import de.escidoc.core.common.servlet.EscidocServlet;
import de.escidoc.core.common.servlet.UserHandleCookieUtil;
import de.escidoc.core.common.util.IOUtils;
import de.escidoc.core.common.util.configuration.EscidocConfiguration;
import de.escidoc.core.common.util.service.UserContext;
import de.escidoc.core.common.util.string.StringUtility;

/**
 * Login servlet for eSciDoc.
 *
 * @author Michael Schneider
 */
public class Login extends HttpServlet {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Login.class);

    private static final int NUM_CHARS = 10;

    private static final String CHARS = "abcdefghijklmonpqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Random RANDOM = new Random(new Date().getTime());

    /**
     * Get a random string.
     *
     * @return random string
     */
    public static String getUniqueID() {
        final char[] buf = new char[NUM_CHARS];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = CHARS.charAt(RANDOM.nextInt(CHARS.length()));
        }
        return new String(buf);
    }

    /**
     * The serial version uid.
     */
    private static final long serialVersionUID = 1L;

    private static final String COOKIE_SPRING_SECURITY = "JSESSIONID";

    private static final String ERR_MSG_MISSING_AUTHENTICATION =
        "No authentication object found.\n" + " Please check if the login mechanism is properly set up in the"
            + " login configuration file escidoc-login.xml.";

    /**
     * Pattern used to detect splitting blanks in DNs.
     */
    private static final Pattern PATTERN_DN_SPLIT = Pattern.compile(", +([a-zA-Z]+=)");

    /**
     * Pattern used to detect white spaces.
     */
    private static final Pattern PATTERN_WHITESPACE = Pattern.compile("\\s");

    /**
     * Pattern used to detect the redirect url place holder in an http page.
     */
    private static final Pattern PATTERN_REDIRECT_URL = Pattern.compile("\\$\\{REDIRECT_URL\\}");

    /**
     * Pattern used to detect the login servlet url place holder in an http page.
     */
    private static final Pattern PATTERN_LOGIN_SERVLET_URL = Pattern.compile("\\$\\{LOGIN_SERVLET_URL\\}");

    private static final String LOGOUT_POSTFIX = "logout";

    // TODO values was cloned to EsidocServlet (to reduce common package
    // dependencies)
    public static final String AUTHENTICATION = "eSciDocUserHandle";

    private static final int BUFFER_SIZE = 0xFFFF;

    private static final String BASE_PATH_LOGIN = "/aa/login/";

    private static final String BASE_PATH_LOGOUT = "/aa/logout";

    private static final String AUTHENTICATED_FILENAME = BASE_PATH_LOGIN + "authenticated.html";

    private static final String AUTHENTICATED_REDIRECT_FILENAME = BASE_PATH_LOGIN + "authenticated-redirect.html";

    private static final String DEACTIVATED_USER_ACCOUNT_PAGE_FILENAME =
        BASE_PATH_LOGIN + "deactivated-user-account.html";

    private static final String LOGOUT_FILENAME = BASE_PATH_LOGIN + "logout.html";

    private static final String LOGOUT_REDIRECT_FILENAME = BASE_PATH_LOGIN + "logout-redirect.html";

    private final Map<String, String> templates = new HashMap<String, String>();

    /**
     * The user account data access object.
     */
    private transient UserAccountDaoInterface dao;

    /**
     * The Policies-Cache object.
     */
    private transient SecurityHelper securityHelper;

    /**
     * Random generator used for setting random password of new users. FIXME: remove this after password has been
     * removed.
     */
    private final Random random = new Random(new Date().getTime());

    /**
     * See Interface for functional description.
     *
     */
    @Override
    public void init() throws ServletException {
        super.init();
        this.dao = (UserAccountDaoInterface) this.getServletContext().getAttribute("persistence.UserAccountDao");
        this.securityHelper = (SecurityHelper) this.getServletContext().getAttribute("security.SecurityHelper");
        try {
            initFileContent(AUTHENTICATED_FILENAME);
            initFileContent(AUTHENTICATED_REDIRECT_FILENAME);
            initFileContent(LOGOUT_FILENAME);
            initFileContent(LOGOUT_REDIRECT_FILENAME);
            initFileContent(DEACTIVATED_USER_ACCOUNT_PAGE_FILENAME);
        }
        catch (final IOException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    /**
     * This implementation provides the login of the user.<br> The following steps are performed:<br> <ul> <li>First,
     * the existence of a valid cookie containing an escidoc handle is checked. If this cookie is found, the handle is
     * used to identify the user.</li> <li>If no valid cookie identifying the user has been found, a login screen is
     * provided for the user.</li> </ul>
     *
     * @param request  Request.
     * @param response Response.
     * @throws ServletException e.
     * @throws IOException      e.
     */
    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
        IOException {

        if (request.getRequestURL().toString().endsWith(LOGOUT_POSTFIX)) {
            doLogout(request, response);
        }
        else {
            doLogin(request, response);
        }
    }

    /**
     * See Interface for functional description.
     */
    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException,
        IOException {

        if (request.getRequestURL().toString().endsWith(LOGOUT_POSTFIX)) {
            doLogout(request, response);
        }
        else {
            doLogin(request, response);
        }
    }

    /**
     * This method provides the logout of the user.<br> The following steps are performed:<br> <ul> <li>First, the
     * existence of a valid cookie containing an escidoc user handle is checked. If this cookie is not found, no logout
     * action is performed.</li> <li>The logout method of the UserManagementWrapper is called providing the extracted
     * userHandle. </ul>
     *
     * @param request  The {@link HttpServletRequest}.
     * @param response The {@link HttpServletResponse}.
     * @throws IOException      Thrown in case of an IO error.
     * @throws ServletException Thrown in case of any other error.
     */
    private void doLogout(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
        ServletException {

        response.setContentType("text/html");
        // Try to identify the user by the cookie containing the
        // handle that identifies him/her.
        final Cookie escidocHandleCookie = EscidocServlet.getCookie(EscidocServlet.COOKIE_LOGIN, request);
        try {
            if (escidocHandleCookie != null) {
                final String handle = escidocHandleCookie.getValue();
                try {
                    if (StringUtils.isNotEmpty(handle)) {
                        dao.deleteUserLoginData(handle);
                    }
                }
                catch (final SystemException e) {
                    throw new ServletException(e);
                }
            }

            sendLoggedOut(request, response);
        }
        catch (final WebserverSystemException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    /**
     * This method provides the login of the user.<br> The following steps are performed:<br> <ul> <li>FIXME: edit
     * documentation</li> </ul>
     *
     * @param request  Request.
     * @param response Response.
     * @throws ServletException e.
     * @throws IOException      e.
     */
    private void doLogin(final HttpServletRequest request, final HttpServletResponse response) throws IOException,
        ServletException {

        try {
            response.setContentType("text/html");

            // check security context
            final SecurityContext securityContext = UserContext.getSecurityContext();
            final Authentication authentication = securityContext.getAuthentication();
            if (authentication == null) {
                throw new WebserverSystemException(ERR_MSG_MISSING_AUTHENTICATION);
            }

            // FIXME: map provided data to format stored in user accounts
            // (this depends on the authentication providers)
            final Object principal = authentication.getPrincipal();
            final String loginname;
            final String username;
            final Map<String, List<String>> attributes;

            if (principal instanceof EscidocLdapUserDetails) {
                final EscidocLdapUserDetails escidocLdapUserDetails = (EscidocLdapUserDetails) principal;
                // FIXME: the following replacement is done to use this via REST
                // interface. Maybe the interface itself should be changed for
                // using white spaces in login names?
                loginname =
                    escidocLdapUserDetails.getUsername()
                        + ','
                        + PATTERN_WHITESPACE.matcher(
                            PATTERN_DN_SPLIT.matcher(escidocLdapUserDetails.getDn()).replaceAll(",$1")).replaceAll("_");
                // FIXME: cn should be used for user name
                final int index = escidocLdapUserDetails.getDn().indexOf(',');
                if (index == -1) {
                    username = escidocLdapUserDetails.getUsername();
                }
                else {
                    final int index2 = escidocLdapUserDetails.getDn().indexOf('=');

                    username = escidocLdapUserDetails.getDn().substring(index2 + 1, index);
                }
                attributes = escidocLdapUserDetails.getStringAttributes();
            }
            else if (principal instanceof ShibbolethUser) {
                final ShibbolethUser shibUser = (ShibbolethUser) principal;
                loginname = shibUser.getLoginName();
                username = shibUser.getName();
                attributes = shibUser.getStringAttributes();
            }
            else if (principal instanceof EscidocOpenidUserDetails) {
                ((EscidocOpenidUserDetails) principal).addAttributes(((OpenIDAuthenticationToken) authentication)
                    .getAttributes());
                loginname = ((EscidocUserDetails) principal).getId();
                username = loginname;
                attributes = ((EscidocOpenidUserDetails) principal).getStringAttributes();
            }
            else {
                loginname = authentication.getName();
                username = loginname;
                attributes = null;
            }

            UserAccount userAccount = dao.retrieveUserAccountByLoginName(loginname);

            if (userAccount != null) {
                // clear cached userGroups
                securityHelper.clearUserGroups(userAccount.getId());
            }
            else {
                userAccount = new UserAccount();
                userAccount.setLoginname(loginname);
                userAccount.setName(username);
                userAccount.setActive(Boolean.TRUE);
                userAccount.setUserAccountByCreatorId(userAccount);
                userAccount.setUserAccountByModifiedById(userAccount);
                final Date now = new Date();
                userAccount.setCreationDate(now);
                userAccount.setLastModificationDate(now);
                userAccount.setPassword(Long.toString(random.nextLong()));
                dao.save(userAccount);
            }

            // delete old external user attributes
            try {
                deleteExternalAttributes(userAccount);
            }
            catch (final Exception e) {
                throw new ServletException(e);
            }

            // store new external user attributes
            if (attributes != null) {
                try {
                    createExternalAttributes(userAccount, attributes);
                }
                catch (final Exception e) {
                    throw new ServletException(e);
                }
            }
            doLoginOfExistingUser(request, response, userAccount);
        }
        catch (final SystemException e) {
            throw new ServletException(e.getMessage(), e);
        }
    }

    /**
     * Login a user for that a user account resource exists.<br> This method creates the new {@link UserLoginData} and
     * redirects the user to the target.
     *
     * @param request     Request.
     * @param response    Response.
     * @param userAccount The {@link UserAccount} of the user.
     * @throws IOException Thrown in case of an I/O error.
     * @throws de.escidoc.core.common.exceptions.system.WebserverSystemException
     * @throws de.escidoc.core.common.exceptions.system.SqlDatabaseSystemException
     */
    private void doLoginOfExistingUser(
        final HttpServletRequest request, final HttpServletResponse response, final UserAccount userAccount)
        throws IOException, SqlDatabaseSystemException, WebserverSystemException {

        if (Boolean.TRUE.equals(userAccount.getActive())) {

            final long timestamp = System.currentTimeMillis();
            final UserLoginData loginData = new UserLoginData();
            loginData.setUserAccount(userAccount);
            loginData.setHandle("ESCIDOC-" + getUniqueID() + timestamp);
            loginData.setExpiryts(timestamp + getESciDocUserHandleLifetime());
            dao.saveOrUpdate(loginData);
            try {
                sendAuthenticated(request, response, loginData.getHandle());
            }
            catch (final WebserverSystemException e) {
                dao.delete(loginData);
                throw e;
            }
            catch (final IOException e) {
                dao.delete(loginData);
                throw e;
            }
        }
        else {
            sendDeactivatedUserAccount(response);
        }
    }

    /**
     * Retrieves the decoded parameter target from the provided request.
     *
     * @param request The http request to retrieve the parameter from.
     * @return Returns the decoded parameter target or {@code null}.
     * @throws MissingParameterException Thrown if a mandatory parameter is missing.
     */
    private static String retrieveDecodedTarget(final ServletRequest request) throws MissingParameterException {

        try {
            final String targetParameter = request.getParameter(EscidocServlet.PARAM_TARGET);
            if (targetParameter != null) {
                return URLDecoder.decode(targetParameter, EscidocServlet.ENCODING);
            }
            else {
                throw new MissingParameterException("No target parameter provided in URL");
            }
        }
        catch (final UnsupportedEncodingException e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Error on retriving decoded target.");
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error on retriving decoded target.", e);
            }
            return "";
        }
    }

    /**
     * Sends the response in case of successfully authenticating the user.<br> The provided handle that identifies the
     * user in the system is sent back in the {@code Authorization} header (to be used by the redirecting
     * application) and via a cookie to enable handling of later calls to the servlet of the same user and to enable
     * direct accesses of the user to the framework by using a web browser.
     *
     * @param request  The http request.
     * @param response The http response.
     * @param handle   The handle identifying the user.
     * @throws IOException              Thrown in case of an error.
     * @throws WebserverSystemException Thrown in case of an internal error.
     */
    private void sendAuthenticated(
        final HttpServletRequest request, final HttpServletResponse response, final String handle) throws IOException,
        WebserverSystemException {

        response.reset();

        // add escidoc cookie
        response.addCookie(UserHandleCookieUtil.createAuthCookie(handle));

        // FIXME: should method return null instead of exception?
        String redirectUrlWithHandle;
        try {
            redirectUrlWithHandle = createRedirectUrl(request, handle);
        }
        catch (final MissingParameterException e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Error on creating redirect URL.");
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error on creating redirect URL.", e);
            }
            redirectUrlWithHandle = null;
        }

        if (redirectUrlWithHandle == null) {
            sendResponse(response, getAuthenticatedPage(null));
        }
        else {
            sendRedirectingResponse(response, getAuthenticatedPage(redirectUrlWithHandle), redirectUrlWithHandle);
        }
    }

    /**
     * Handles the failure case of a deactivated user account<br> An error page is presented to the user.
     *
     *
     * @param response The http response.
     * @throws IOException              Thrown in case of an I/O error.
     * @throws WebserverSystemException Thrown if cookie creation fails due to an internal error.
     */
    private void sendDeactivatedUserAccount(final HttpServletResponse response) throws IOException,
        WebserverSystemException {

        response.reset();
        response.setContentType("text/html");

        // delete the session cookie of spring security to allow restarting the
        // login process
        response.addCookie(deleteSpringSecurityCookie());

        sendResponse(response, getDeactivatedUserAccountErrorPage());
    }

    /**
     * Sends the response in case of successful logout of the user.<br> <ul> <li>The cookie containing the eSciDoc user
     * handle is deleted.</li> <li>Either <ul> <li>the user is redirected to the provided targetUrl, or</li> <li>a
     * default logout page is presented to the user, if no targetUrl has been provided</li> </ul> </li> </ul>
     *
     * @param request  The http request.
     * @param response The http response.
     * @throws IOException              In case of an error.
     * @throws WebserverSystemException Thrown if cookie creation fails due to an internal error.
     */
    private void sendLoggedOut(final HttpServletRequest request, final HttpServletResponse response)
        throws IOException, WebserverSystemException {

        response.reset();
        response.setContentType("text/html");

        // delete cookies
        response.addCookie(deleteAuthCookie());
        response.addCookie(deleteSpringSecurityCookie());

        String redirectUrl;
        try {
            redirectUrl = retrieveDecodedTarget(request);
        }
        catch (final MissingParameterException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Error on retriving decoded target.", e);
            }
            redirectUrl = null;
        }

        if (redirectUrl == null) {
            sendResponse(response, getLoggedOutPage(null));
        }
        else {
            sendRedirectingResponse(response, getLoggedOutPage(redirectUrl), redirectUrl);
        }
    }

    /**
     * Creates the redirect URL from the provided request information and the provided eSciDoc user handle.
     *
     * @param request    The http request to retrieve the redirect Url from.
     * @param userHandle The eScidoc user handle.
     * @return Returns the redirectUrl containing the eScidoc user handle as a parameter.
     * @throws MissingParameterException Thrown if the target parameter is not found.
     * @throws de.escidoc.core.common.exceptions.system.WebserverSystemException
     */
    private String createRedirectUrl(final HttpServletRequest request, final String userHandle)
        throws MissingParameterException, WebserverSystemException {

        return createRedirectUrl(retrieveDecodedTarget(request), userHandle);
    }

    /**
     * Creates the redirect URL from the provided request information and the provided eSciDoc user handle.
     *
     * @param redirectUrl The URL to that the user shall be redirected.
     * @param userHandle  The eScidoc user handle.
     * @return Returns the redirectUrl containing the eScidoc user handle as a parameter. This is {@code null} if
     *         no redirect Url has been provided.
     * @throws de.escidoc.core.common.exceptions.system.WebserverSystemException
     */
    private static String createRedirectUrl(final String redirectUrl, final String userHandle)
        throws WebserverSystemException {

        if (StringUtils.isEmpty(redirectUrl)) {
            return null;
        }
        else {
            final char delimiter = redirectUrl.indexOf('?') == -1 ? '?' : '&';
            return redirectUrl + delimiter + AUTHENTICATION + '='
                + UserHandleCookieUtil.createEncodedUserHandle(userHandle);
        }
    }

    /**
     * Gets the page presented in case of a successful authentication (HTML).
     *
     * @param redirectUrl The URL to that the user shall be redirected.
     * @return Returns the page content.
     */
    private String getAuthenticatedPage(final String redirectUrl) {

        if (redirectUrl == null) {
            final String pageContent = getFileContent(AUTHENTICATED_FILENAME);
            return PATTERN_REDIRECT_URL.matcher(pageContent).replaceAll(BASE_PATH_LOGOUT);
        }
        else {
            final String pageContent = getFileContent(AUTHENTICATED_REDIRECT_FILENAME);
            return PATTERN_REDIRECT_URL.matcher(pageContent).replaceAll(redirectUrl);
        }

    }

    /**
     * Gets the page presented after a successful log-out of the user (HTML).
     *
     * @param redirectUrl The URL to which the user shall be redirected.
     * @return Returns the page content.
     */
    private String getLoggedOutPage(final String redirectUrl) {

        if (redirectUrl == null) {
            return getFileContent(LOGOUT_FILENAME);
        }
        else {
            final String pageContent = getFileContent(LOGOUT_REDIRECT_FILENAME);
            return PATTERN_REDIRECT_URL.matcher(pageContent).replaceAll(redirectUrl);
        }
    }

    /**
     * Gets the deactivated user account error page (HTML).
     *
     * @return Returns the error page.
     */
    private String getDeactivatedUserAccountErrorPage() {

        final String pageContent = getFileContent(DEACTIVATED_USER_ACCOUNT_PAGE_FILENAME);
        return PATTERN_LOGIN_SERVLET_URL.matcher(pageContent).replaceAll("");
    }

    /**
     * Get the content of the template file as {@code String}.<br> The content is stored in a {@code Map} to
     * prevent unnecessary file resource accesses
     *
     * @param templateFileName The file name of the template that shall be retrieved/loaded.
     * @throws IOException Thrown in case of an I/O error.
     */
    private void initFileContent(final String templateFileName) throws IOException {

        final StringBuilder result = new StringBuilder();
        final InputStream inputStream = Login.class.getResourceAsStream(templateFileName);
        if (inputStream == null) {
            throw new IOException(StringUtility.format("Template not found", templateFileName));
        }
        try {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int length = inputStream.read(buffer);
            while (length != -1) {
                result.append(new String(buffer, 0, length));
                length = inputStream.read(buffer);
            }
        }
        finally {
            IOUtils.closeStream(inputStream);
        }
        templates.put(templateFileName, result.toString());
    }

    /**
     * Get the content of the template file as {@code String} from the {@link Map} of templates.<br>
     *
     * @param templateFileName The file name of the template that shall be retrieved/loaded.
     * @return The content of the template file.
     */
    private String getFileContent(final String templateFileName) {

        return templates.get(templateFileName);
    }

    /**
     * Writes the provided page content to the writer of the {@link HttpServletResponse}.<br> The http status code is
     * set to OK.
     *
     * @param response The {@link HttpServletResponse}.
     * @param page     The page to write.
     * @throws IOException Thrown in case of a failed i/o operation.
     */
    private void sendResponse(final HttpServletResponse response, final String page) throws IOException {

        sendResponse(response, page, HttpServletResponse.SC_OK);
    }

    /**
     * Writes the provided page content to the writer of the {@link HttpServletResponse}. The http status code is set to
     * the provided value.
     *
     * @param response   The {@link HttpServletResponse}.
     * @param page       The page to write.
     * @param statusCode The http status code to set.
     * @throws IOException Thrown in case of a failed i/o operation.
     */
    private static void sendResponse(final HttpServletResponse response, final String page, final int statusCode)
        throws IOException {
        final PrintWriter writer = response.getWriter();
        writer.print(page);
        response.setStatus(statusCode);
    }

    /**
     * Initializes the redirect to the provided URL and writes the provided page content to the writer of the {@link
     * HttpServletResponse}. The http status code is set to SEE_OTHER.
     *
     * @param response    The {@link HttpServletResponse}.
     * @param page        The page to write.
     * @param redirectUrl The URL to that the user shall be redirected.
     * @throws IOException Thrown in case of a failed i/o operation.
     */
    private static void sendRedirectingResponse(
        final HttpServletResponse response, final String page, final String redirectUrl) throws IOException {

        final PrintWriter writer = response.getWriter();
        writer.print(page);
        // TODO: which response/status code? sendRedirect uses 302. 307 must
        // not be used since this results in forwarding the post of login data.
        // Maybe, 303 should be used to force the browser to redirect with GET,
        // as redirect with POST is prohibited?
        // Or should a form be returned and the user posts the handle back to
        // the application (similar to Shibboleth's Browser/Post profile)?
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", redirectUrl);
        //        response.flushBuffer();
    }

    /**
     * Deletes the authentication cookie.
     *
     * @return Returns a cookie that deletes the cookie in the user's cookie storage.
     * @throws WebserverSystemException Thrown if cookie creation fails due to an internal error.
     */
    private Cookie deleteAuthCookie() throws WebserverSystemException {

        return createCookie(EscidocServlet.COOKIE_LOGIN, null, 0);
    }

    /**
     * Delete all external user attributes.
     *
     * @param userAccount user account
     * @throws SqlDatabaseSystemException Thrown in case of a database error.
     * @throws WebserverSystemException   Thrown in case of an internal error.
     */
    private void deleteExternalAttributes(final UserAccount userAccount) throws SqlDatabaseSystemException {
        final List<UserAttribute> attributes = dao.retrieveAttributes(userAccount);

        for (final UserAttribute attribute : attributes) {
            if (!attribute.getInternal()) {
                dao.delete(attribute);
            }
        }
        userAccount.touch();
    }

    /**
     * Deletes the spring security cookie used during authentication.
     *
     * @return Returns a cookie that deletes the cookie in the user's cookie storage.
     * @throws WebserverSystemException Thrown if cookie creation fails due to an internal error.
     */
    private Cookie deleteSpringSecurityCookie() throws WebserverSystemException {

        return createCookie(COOKIE_SPRING_SECURITY, null, 0);
    }

    /**
     * Creates a {@link Cookie} for the provided values.
     *
     * @param name   The name of the {@link Cookie}.
     * @param value  The value of the {@link Cookie}.
     * @param maxAge The maxAge of the {@link Cookie}.
     * @return Returns the created {@link Cookie} with set values and path set to "/".
     * @throws WebserverSystemException Thrown if cookie creation fails due to an internal error.
     */
    private Cookie createCookie(final String name, final String value, final int maxAge)
        throws WebserverSystemException {

        final Cookie cookie = new Cookie(name, value);
        cookie.setVersion((int) getEscidocCookieVersion());
        cookie.setMaxAge(maxAge);
        cookie.setPath("/");
        return cookie;
    }

    /**
     * Store the given set of attributes as external user attributes.
     *
     * @param userAccount user account
     * @param attributes  user attributes (key=attribute name, value=list with attribute values)
     * @throws SqlDatabaseSystemException Thrown in case of a database error.
     * @throws WebserverSystemException   Thrown in case of an internal error.
     */
    private void createExternalAttributes(final UserAccount userAccount, final Map<String, List<String>> attributes)
        throws SqlDatabaseSystemException {
        for (final Entry<String, List<String>> entry : attributes.entrySet()) {
            final List<String> attributeValues = entry.getValue();
            if (attributeValues != null) {
                for (final String attributeValue : attributeValues) {
                    final UserAttribute attribute = new UserAttribute();
                    attribute.setInternal(false);
                    attribute.setName(entry.getKey());
                    attribute.setUserAccountByUserId(userAccount);
                    attribute.setValue(attributeValue);
                    dao.save(attribute);
                }
            }
        }
        userAccount.touch();
    }

    /**
     * The time span during that the eSciDoc user handle is valid (in milli seconds).
     *
     * @return the eSciDocUserHandleLifetime.
     * @throws WebserverSystemException Thrown if access to configuration properties fails.
     */
    public long getESciDocUserHandleLifetime() throws WebserverSystemException {

        try {
            return Long.parseLong(EscidocConfiguration.getInstance().get(
                EscidocConfiguration.ESCIDOC_CORE_USERHANDLE_LIFETIME));
        }
        catch (final Exception e) {
            throw new WebserverSystemException(StringUtility.format("Can't get configuration parameter",
                EscidocConfiguration.ESCIDOC_CORE_USERHANDLE_LIFETIME, e.getMessage()), e);
        }
    }

    /**
     * The cookie spec. version that is used <ul> <li>0: netscape</li> <li>1: rfc 2109</li> </ul>
     *
     * @return the escidocCookieVersion.
     * @throws WebserverSystemException Thrown if access to configuration properties fails.
     */
    public byte getEscidocCookieVersion() throws WebserverSystemException {

        final byte escidocCookieVersion;
        try {
            final String configProperty =
                EscidocConfiguration
                    .getInstance().get(EscidocConfiguration.ESCIDOC_CORE_USERHANDLE_COOKIE_VERSION).toLowerCase(
                        Locale.ENGLISH).trim();
            if ("netscape".equals(configProperty) || "0".equals(configProperty)) {
                escidocCookieVersion = (byte) 0;
            }
            else if ("rfc2109".equals(configProperty) || "rfc 2109".equals(configProperty)
                || "1".equals(configProperty)) {
                escidocCookieVersion = (byte) 1;
            }
            else {
                throw new WebserverSystemException(StringUtility.format("Invalid configuration property value.",
                    EscidocConfiguration.ESCIDOC_CORE_USERHANDLE_COOKIE_VERSION, configProperty));
            }
        }
        catch (final Exception e) {
            throw new WebserverSystemException(StringUtility.format("Can't get configuration parameter",
                EscidocConfiguration.ESCIDOC_CORE_USERHANDLE_COOKIE_VERSION, e.getMessage()), e);
        }
        return escidocCookieVersion;
    }
}
