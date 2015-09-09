package org.wso2.carbon.appserver.integration.test.server.security.manager;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.appserver.integration.common.clients.WebAppAdminClient;
import org.wso2.appserver.integration.common.utils.ASIntegrationTest;
import org.wso2.appserver.integration.common.utils.WebAppDeploymentUtil;
import org.wso2.appserver.integration.common.utils.WebAppTypes;
import org.wso2.carbon.automation.engine.FrameworkConstants;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.engine.exceptions.AutomationFrameworkException;
import org.wso2.carbon.automation.engine.frameworkutils.enums.OperatingSystems;
import org.wso2.carbon.automation.test.utils.common.TestConfigurationProvider;
import org.wso2.carbon.integration.common.admin.client.UserManagementClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import static org.testng.Assert.assertTrue;

/**
 * Test class to verify the ability to invoke user store operations from deployed
 * webapps when java security manager is enabled.
 */

public class CARBON3340JavaSecurityManager extends ASIntegrationTest {


    private static final Log log = LogFactory.getLog(CARBON3340JavaSecurityManager.class);

    private WebAppAdminClient webAppAdminClient;
    private String webAppUrl;
    private final String hostName = "localhost";
    private final String webAppFileName = "carboncontext-test-app.war";
    private final String webAppName = "carboncontext-test-app";

    private String user1 = "UserStoreTestUser1";
    private String user2 = "UserStoreTestUser2";

    private TestUserMode userMode;

    @Factory(dataProvider = "userModeDataProvider")
    public CARBON3340JavaSecurityManager(TestUserMode userMode) {
        this.userMode = userMode;
    }

    @DataProvider
    public static Object[][] userModeDataProvider() {
        return new Object[][]{
                new Object[]{TestUserMode.SUPER_TENANT_ADMIN},
        };
    }

    @BeforeClass(alwaysRun = true, description = "Deploying the Web application")
    public void init() throws Exception {
        if (System.getProperty(FrameworkConstants.SYSTEM_PROPERTY_OS_NAME).toLowerCase()
                .contains(OperatingSystems.WINDOWS.toString().toLowerCase())) {
            throw new SkipException("Skipping this test case in windows");
        }
        super.init(userMode);
        webAppAdminClient = new WebAppAdminClient(backendURL, sessionCookie);
        webAppAdminClient.uploadWarFile(TestConfigurationProvider.getResourceLocation("AS")
                + File.separator + "security"
                + File.separator + "manager" + File.separator + "webapp"
                + File.separator + webAppFileName);
        //let webapp to deploy
        Thread.sleep(2000);
        assertTrue(WebAppDeploymentUtil.isWebApplicationDeployed(backendURL, sessionCookie, webAppName)
                , webAppName + " Web Application Deployment failed");
        webAppUrl = getWebAppURL(WebAppTypes.WEBAPPS) + "/" + webAppName;

        if (userMode == TestUserMode.TENANT_ADMIN || userMode == TestUserMode.TENANT_USER) {
            if (!webAppUrl.contains("/t/")) {
                throw new AutomationFrameworkException("Web App Url is not correct for tenants when running test " +
                        "for tenants " + userInfo.getUserName() + " > " + webAppUrl);
            }
        }
    }

    @Test(groups = "wso2.as", description = "Invoke user store operations through web application")
    public void testWebAppUserStoreOperations() throws Exception {

        UserManagementClient userMgtClient = new UserManagementClient(backendURL, sessionCookie);
        userMgtClient.addUser(user1, "passWord1@", null, "default");
        userMgtClient.addUser(user2, "passWord2@", null, "default");

        userMgtClient.addRole("umRole1", new String[]{user1, user2}, new String[]{"login"}, false);
        userMgtClient.addRole("umRole2", new String[]{user1}, new String[]{"login"}, false);

        log.info("Users and Roles Added Successfully ");
        String webAppContext = "/TenantServlet?user=UserStoreTestUser1";
        String finalURL = webAppUrl + webAppContext;

        URL url = new URL(finalURL);
        URLConnection connection = url.openConnection();
        connection.setDoOutput(true);

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        char[] buffer = new char[Integer.parseInt(connection.getHeaderField("Content-Length"))];
        int count = buffer.length;
        String value = null;
        while ((count = br.read(buffer)) > 0) {
            value = new String(buffer, 0, count);
        }

        JSONArray arr = new JSONArray(new String(value));

        ArrayList<String> stringArray = new ArrayList<String>();
        for (int i = 0; i < arr.length(); i++) {
            stringArray.add(arr.getString(i));
        }
        assertTrue(!(stringArray.contains("Java Security Manager Exception")),
                "User Store Operations Cannot be Invoked with Java Security Manager");
    }

    @AfterClass(alwaysRun = true)
    public void clean() throws Exception {
        if (System.getProperty(FrameworkConstants.SYSTEM_PROPERTY_OS_NAME).toLowerCase()
                .contains(OperatingSystems.WINDOWS.toString().toLowerCase())) {
            throw new SkipException("Skipping this test case in windows");
        }
        webAppAdminClient.deleteWebAppFile(webAppFileName, hostName);
        //let webapp to undeploy
        Thread.sleep(2000);
        assertTrue(WebAppDeploymentUtil.isWebApplicationUnDeployed(backendURL, sessionCookie, webAppName),
                webAppName + " Web Application unDeployment failed");
    }

}
