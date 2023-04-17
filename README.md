# Summer Boot Framework was initiated by a group of developers in 2004 to provide a high performance, free customizable but also lightweight Netty JAX-RS RESTful, WebSocket and gRPC service with JPA and other powerful reusable non-functional features, and since 2011 was adopted by several Toronto law firms to customize their back-end services.

# Its sub-project, jExpress (a.k.a. Summer Boot Framework Core), focuses on solving the following non-functional and operational maintainability requirements, which are (probably) not yet available in Spring Boot.

![Summer Boot Overview](SummerBootOverview.png)

**Open Source History: jExpress was open sourced initially on MS MySpace in Sep 2006, due to the shutdown of MySpace this framework was migrated to a server sponsored by one of the law firms in October 2011, then migrated to GitLab in Dec 2016, eventually migrated to GitHub in Sep 2021.**  

> Disclaimer: We really had a great time with GitLab until 2021 when we realized one of the contributor's employer was also using GitLab at that time, we decided to move to GitHub instead to avoid incur unnecessary hassles.



## 1. Performance: RESTful Web Services (JAX-RS) with Non-blocking I/O (powered by Netty Reactor - *multiplexing* approach)



**1.1 Intent**

- To solve the performance bottleneck of traditional multi-threading mode at I/O layer

- Quickly develop a RESTful Web Services with JAX-RS with minimal code required 

  

**1.2 Motivation**

- Application server is always heavy and some of them are not free, include but not limited to IBM WebSphere, Oracle Glassfish, Payara, Red Hat JBoss or Tomcat

- Netty Reactor - *multiplexing* approach provides an incredible amount of power for developers who need to work down on the socket level, for example when developing custom communication protocols between clients and servers

  

**1.3 Sample Code** - https://github.com/SummerBootFramework/jExpressDemo-HelloSummer

add the jExpress dependency to pom.xml

```
<dependency>
    <groupId>org.summerboot</groupId>
    <artifactId>jexpress</artifactId>
</dependency>
```

or in your pom.xml file you can add the Maven 2 snapshot repository if you want to try out the SNAPSHOT versions:

```
<repositories>
    <repository>
        <id>maven.snapshots</id>
        <name>Maven Snapshot Repository</name>
        <url>https://s01.oss.sonatype.org/content/repositories/snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```


Main.java

```
import org.summerboot.jexpress.boot.SummerApplication;

public class Main {

    public static void main(String... args) {
        SummerApplication.run();
    }
}
```

A RESTful API class with JAX-RS style, and annotate this class with @Controller 

```
import com.google.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.boot.annotation.Log;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;

@Singleton
@Controller
@Path("/hellosummer")
public class MyController {

    @GET
    @Path("/account/{name}")
    @Produces({MediaType.TEXT_PLAIN})
    public String hello(@NotNull @PathParam("name") String myName) {// both Nonnull or NotNull works    
        return "Hello " + myName;
    }

    @POST
    @Path("/account/{name}")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public ResponseDto hello_no_validation_unprotected_logging(@PathParam("name") String myName, RequestDto request) {
        return new ResponseDto();
    }

    /**
     * Three features:
     * <p> 1. auto validation by @Valid and @NotNull annotation 
     * <p> 2. protected user credit card and privacy information from being logged by @Log annotation
     * <p> 3. mark performance POI (point of interest) by using ServiceContext.poi(key), see section#8.3
     *
     * @param myName
     * @param request
     * @param context
     * @return
     */
    @POST
    @Path("/hello/{name}")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Log(hideJsonStringFields = {"creditCardNumber", "clientPrivacy"})
    public ResponseDto hello_auto_validation_protected_logging_markWithPOI(@NotNull @PathParam("name") String myName, @NotNull @Valid RequestDto request, final ServiceContext context) {
        context.poi("DB begin");// about POI, see section8.3
        // DB access and it takes time ...
        context.poi("DB end");

        context.poi("gRPC begin");// about POI, see section8.3
        // gRPC access and it takes time ...
        context.poi("gRPC end");

		context.status(HttpResponseStatus.CREATED);// override default HTTP response status
        return new ResponseDto();
    }

    public static class RequestDto {

        @NotNull
        private String creditCardNumber;

        @Valid
        @NotEmpty
        private List<String> shoppingList;
    }

    public static class ResponseDto {

        private String clientPrivacy;
    }
}
```

**1.4 Sample Code: -use \<implTag\>**

Use @Controller.**implTag** field as below, this controller class will only be available with -**use RoleBased** parameter to launch the application, see *<u>section#9</u>*

```
@Controller(implTag="RoleBased")
```

**1.5 Sample Code: PING** see *section#5*

Make the controller enable the ping api at /hellosummer**/ping**, due to ping will occur every 5 seconds, you do not want to log it at all.

Extends **PingController** or **BootController** as below

```
import org.summerboot.jexpress.nio.server.ws.rs.PingController;

@Controller
@Path("/hellosummer")
public class MyController extends PingController {
	...
}
```

or

```
import org.summerboot.jexpress.nio.server.ws.rs.BootController;

@Controller
@Path("/hellosummer")
public class MyController extends BootController {
	...
}
```

or use @Ping on a GET method, you need to add OpenAPI doc by yourself or copy it from **PingController**

```
import org.summerboot.jexpress.boot.annotation.Ping;

@Controller
@Path("/hellosummer")
public class MyController  {
	@GET
    @Path("/ping)
    @Ping
    public void hello() {
    }
}
```

**1.6 Sample Code: Role Based access**

step1: Extends **BootController** as below

```
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import org.summerboot.jexpress.nio.server.ws.rs.BootController;

@Controller
@Path("/hellosummer")
public class MyController extends BootController {
	@GET
    @Path("/hello/anonymous")
    public void anonymous() {
    }

    @GET
    @Path("/helloAdmin/user")
    @PermitAll
    public void loginedUserOnly() {
    }

    @GET
    @Path("/helloAdmin/admin")
    @RolesAllowed({"AppAdmin"})
    public void adminOnly() {
    }
    
    @GET
    @Path("/helloAdmin/employee")
    @RolesAllowed({"Employee"})
    public void employeeOnly() {
    }
}
```

step2: define an Authenticator service with annotation @Service(binding = Authenticator.class)

simply extends **BootAuthenticator**

```
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpHeaders;
import javax.naming.NamingException;
import org.summerboot.jexpress.boot.annotation.Service;
import org.summerboot.jexpress.nio.server.RequestProcessor;
import org.summerboot.jexpress.nio.server.domain.ServiceContext;
import org.summerboot.jexpress.security.auth.Authenticator;
import org.summerboot.jexpress.security.auth.AuthenticatorListener;
import org.summerboot.jexpress.security.auth.BootAuthenticator;
import org.summerboot.jexpress.security.auth.Caller;
import org.summerboot.jexpress.security.auth.User;

@Singleton
@Service(binding = Authenticator.class)
public class AuthenticatorImpl extends BootAuthenticator<MyClass> {

    @Override
    protected Caller authenticate(String usename, String password, MyClass metaData, AuthenticatorListener listener, ServiceContext context) throws NamingException {
    	// verify username and password against LDAP
        long tenantId = 1;
        String tenantName = "jExpress Org";
        long userId = 456;
        User user = new User(tenantId, tenantName, userId, usename);
        user.addGroup("AdminGroup");//user group will be mapped to role in step#3
        user.addGroup("EmployeeGroup");//user group will be mapped to role in step#3
        return user;
    }

    @Override
    public boolean customizedAuthorizationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, ServiceContext context) throws Exception {
        return true;
    }

}
```

step3: define Role-Group mapping in **cfg_auth.properties**

Format of **role-group mapping**: *roles.\<role name\>.groups*=csv list of groups
Format of **role-user mapping**: *roles.\<role name\>.users*=csv list of users

```
roles.AppAdmin.groups=AdminGroup
#roles.AppAdmin.users=admin1, admin2
roles.Employee.groups=EmployeeGroup
#roles.Employee.users=employee1, employee2
```



## 2. Auto Generated Configuration Files

**2.1 Intent**

- Keep configuration files clean and in sync with your code

**2.2 Motivation**

- With the development of more functions, like document maintenance, the configuration file may be inconsistent with the code
- You need a way to dump a clean configurations template from code

**2.3 Auto generated configuration files - for all applications**

- **log4j2.xml**

  > 1. Requires JVM arg: -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector 
  > 2. Required total disk spece: around 200MB
  > 3. Archive logs: by DAY and split them by MINUTE
  > 4. Default log level is tuned for development

- **cfg_smtp.properties**

  > for sending email alert

- **cfg_auth.properties**

  > Authentication: Sign JWT and parse  JWT
  >
  > Authorization: Role and User Group mapping

**2.4 Auto generated configuration files - application type based**

- **cfg_nio.properties**

  > generated when application contains @Controller (running as RESTFul API service)

- **cfg_grpc.properties**

  > generated when application contains gRPC service (running as gRPC service)

- **All other application specific configurations, which**

  > 1. annotated with @ImportResource
  >
  > 2. extends org.summerboot.jexpress.boot.config.BootConfig or implements org.summerboot.jexpress.boot.config.JExpressConfig
  >
  > 3. implemented as a singleton via Eager Initialization
  >
  >    example#1: public static final AppConfig cfg = BootConfig.instance(AppConfig.class);
  >
  >    example#2: public static final AppConfig cfg = new AppConfig();





## 3. Hot Configuration

**3.1 Intent**

- Need to guarantee service continuity and protect it from configuration changes (3rd party tokens, license keys, etc.)

  

**3.2 Motivation**

- Your Wall Street investors definitely do not want to stop and restart the "cash cow" just because you need to update the config file with a renewed 3rd party license key

  

**3.3 Sample Code**

Once the configuration files changed, the jExpress will automatically load it up and refresh the singleton instance**

MyConfig.java

```
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.File;
import java.util.Properties;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;
import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;

@ImportResource("cfg_app.properties")
public class MyConfig extends BootConfig {

    public static final MyConfig cfg = MyConfig.instance(MyConfig.class);

	or use the following:

	public static final MyConfig cfg = new MyConfig();

    private MyConfig() {
    }

    @ConfigHeader(title = "My Header description")
    @JsonIgnore
    @Config(key = "my.key.name", validate = Config.Validate.Encrypted, required = true)
    protected volatile String licenseKey;

    @Override
    protected void loadCustomizedConfigs(File cfgFile, boolean isNotMock, ConfigUtil helper, Properties props) throws Exception {
    }

    @Override
    public void shutdown() {
    }

    public String getLicenseKey() {
        return licenseKey;
    }
}
```



During application start, it will generate cfg_app.properties if not exist, although the following code will generate cfg_app.properties template at any time:

```
public static void main(String[] args) {
        String template = MyConfig.generateTemplate(MyConfig.class);
        System.out.println(template);
} 
```

cfg_app.properties:

```bash
#########################
# My Header description #
#########################
my.key.name=DEC(plain password)
```



## 4. Protected Configuration

**4.1 Intent**

- Sensitive Data - passwords, license keys, signing key (HS256, HS384, HS512 only) and 3rd party tokens (AWS integration token, etc.) cannot be plain text.

- Protect sensitive data in the config files - just like the "one ring to rule them all" in The Lord of the Rings

  - **One-Way Protection:** application admin can only write to config file with plain text, but not able to read encrypted sensitive data from config file
  - **Two Level Protection:** application root password is managed/protected by root admin, it controls sensitive data encryption/decryption, and it should not be managed application admin

  

**4.2 Motivation**

- You want to protect sensitive data in the config files, and you encrypt them with a key.

- Nobody hangs the key on the door it just locked, so you do need to protect the key, which just locks (encrypt) the sensitive data in your safe box, and you do NOT want to keep it hardcoded in your source code.

- You really do NOT want to enter an endless loop by keep creating a new key to protect the original key, which protects the sensitive data.

- You only need one extra root password to encrypt/decrypt the sensitive data, and this root password is not with your application admin.

- **Two Level Access**: who controls what

  - Level 1: Application Admin - can update application sensitive data as plain text **without knowing the root password nor how to encrypt/decrypt**, and the plain text sensitive data will be automatically encrypted by the running application, or manually encrypted by app admin without knowing the root password

  - Level 2: Root (Linux/Windows) Admin - control the root password in a protected file, which is used to encrypt/decrypt the sensitive data stored inside application config file, and only accessible by root admin but not application admin nor other users.

    Your application launched as system service controlled by root admin, and runs with 

    > “-cfgdir <path to config folder> -authfile <path to root password file>”

    ```
    java -jar my-service.jar -cfgdir dev/configuration -authfile /etc/security/my-service.root_pwd
    ```

    Your root password is stored in file /etc/security/my-service.root_pwd, and has the following format:

    ```
    APP_ROOT_PASSWORD=<base64 encoded my app root password>
    ```



**4.3 Implementation** 

- **Auto Encrypt mode**: 

  - **step1**: Wrap the plain text password with **DEC()** as shown below, Here, DEC() is a marker that tells the app what to encrypt, and the remaining values are untouched:

    ```
    datasource.password=DEC(plain password)
    ```

  - **step2: Save this config file. The application will aromatically pick up the change in 5 seconds and then encrypt it using the app config password stored in <path to a file which contains config password>,  then replace it with **ECN(**encrypted value**)** in the same file:

    ```
    datasource.password=ENC(encrypted password)
    ```

- **Manual Batch Encrypt mode**: 

  the commands below encrypt all values in the format of “DEC(plain text)” in the specified configuration env:

  ```
  java -jar my-service.jar -cfgdir <path to config folder> -encrypt -authfile <path to root pwd file>
  ```

   In case you happens to know the root password (you ware two hats, the app admin and root admin is same person), you can do the same by providing the root password directly:

  ```
  java -jar my-service.jar -cfgdir <path to config folder> -encrypt -auth <my app root password>
  ```

  

- **Manual Batch Decrypt mode**:

  You cannot decrypt without knowing the root password, that is to say, you cannot decrypt with root password file. 

  The command below decrypts all values in the format of “ENC(encrypted text)” in the specified configuration env:

  ```
  java -jar my-service.jar -cfgdir <path to config folder> -decrypt -auth <my app root password>
  ```

  

  > **Note:**  
  >
  > - The comments in the configuration file will not be auto/batch encrypted/decrypted.
  > - “changeit” is the default <app root password> when -authfile or -auth option is specified.





## 5. Ping with Load Balancer

**5.1 Intent**

- Work with load balancer

**5.2 Motivation**

- Need to tell the load balancer my service status but do not affect my application log

**5.3 Sample Code**

Add the following to enable ping on https://host:port/hellosummer/ping

```
@Ping
@GET
```

Full version:

```
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.summerboot.jexpress.boot.annotation.Controller;
import org.summerboot.jexpress.nio.server.ws.rs.BootController;
import org.summerboot.jexpress.boot.annotation.Ping;

@Controller
@Path("/hellosummer")
public class WebController extends BootController {

    @GET
    @Ping
    @Path("/ping")
    public void ping() {// or whatever method name you like
    }
    
    @GET
    @Path("/hello/{name}")
    public String hello(@PathParam("sce") String name) {
        return "Hello " + name;
    }
}
```





## 6. Ping with Health Check/Auto-Shutdown

**6.1 Intent****

- Tell load balancer I'm not at good status 

**6.2 Motivation****

- When one of your application/service's dependency (database, 3rd party service, etc.) is down, the framework will automatically response error to load balancer, so that no upcoming request will route to this node.

**6.3 Sample Code****

Add the following:

```
@Service(binding = HealthInspector.class)
YourClass extends BootHealthInspectorImpl
```

Full version:

```bash
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.annotation.Service;
import org.summerboot.jexpress.boot.instrumentation.BootHealthInspectorImpl;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceError;

@Service(binding = HealthInspector.class)
public class HealthInspectorImpl extends BootHealthInspectorImpl {

    @Override
    protected void healthCheck(@Nonnull ServiceError error, @Nullable Logger callerLog) {
    	if(error detected) {
	        error.addError(new Err(123, "error tag", "error meessage", null));
	    }
    }
}
```





## 7. Auto-Alert

**7.1 Intent**

- Get noticed before someone knocks your door

**7.2 Motivation**

- Support team want to get noticed when some expected issues happens, like database down, network donw
- Development team want get get noticed when some unexpected issues happens, like defect/bug caused runtime exception
- But you don't want to get bombed by those emails

**7.3 Sample Code**

there is a pre-defined config: cfg_smtp.properties

```
####################
# 1. SMTP Settings #
####################
mail.smtp.host=smtpserver
mail.smtp.user=abc_service@email.addr
mail.smtp.userName=ABC Service


###########################################
# 2. Alert Recipients                     #
# Format: CSV format                      #
# Example: johndoe@xx.com, janedoe@xx.com #
###########################################
#email.to.AppSupport=
## use AppSupport if not provided
#email.to.Development=

## use AppSupport if not provided
#email.to.ReportViewer=

## Alert message with the same title will not be sent out within this minutes
debouncing.emailalert_minute=30
```

add the following:

```
nothing, the only thing need to do is the updated the cfg_smtp.properties
```



## 8. Log after response sent

**8.1 Intent**

- Show the request log and response log from the same client together
- Client receives response without waiting for application to finish the logging/reporting as before.
- Save disk space for log files
- Easy to identify where the log file generate and by which server

**8.2 Motivation**

- Request#1 is logged, and its response is logged separately after hundreds of lines
- You don want to keep the client side wait just because your application is doing logging
- Log file will be zipped automatically when file size exceeds the predefined limit.
- Log file name will be automatically filled with server name when created.



**8.3 And there are 2 type of separated logs:**

**1. Request log** - lt contains client request related information. A single log entry contains the following information:

1. Security/Business required information: who did what when, how and from where
2. Performance tuning required information: POI (point of interest) of the key events
3. App support required information: The full conversation between client and service
4. App Debug required information: The full conversation between service and 3rd paty

Log sample:

> 2021-07-24 14:12:36,620 INFO com.dlo.courtfiling.app.http.io.HttpRequestHandler.lambda$channelRead0$4() [pool-4-thread-6] request_6.caller=null
> 	request_6=GET /web-resources/styles/util_fileupload.css, dataSize=0, KeepAlive=true, chn=[id: 0x1e5deb34, L:/0:0:0:0:0:0:0:1:8989 - R:/0:0:0:0:0:0:0:1:1047], ctx=484232802, hdl=com.dlo.courtfiling.app.http.io.HttpRequestHandler@2baf9cd4
> 	responsed_6=200 OK, error=0, queuing=3ms, process=18ms, response=18ms, cont.len=2048bytes
> 	POI: service.begin=4ms, auth.begin=4ms, process.begin=4ms, biz.begin=4ms, biz.end=18ms, process.end=18ms, service.end=18ms, 
> 	1.client_req.headers=DefaultHttpHeaders[Host: localhost:8989, Connection: keep-alive, sec-ch-ua: "Chromium";v="92", " Not A;Brand";v="99", "Google Chrome";v="92", DNT: 1, sec-ch-ua-mobile: ?0, User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 Safari/537.36, Accept: text/css,*/*;q=0.1, Sec-Fetch-Site: same-origin, Sec-Fetch-Mode: no-cors, Sec-Fetch-Dest: style, Referer: https://localhost:8989/, Accept-Encoding: gzip, deflate, br, Accept-Language: en-GB,en-US;q=0.9,en;q=0.8,zh-CN;q=0.7,zh;q=0.6, content-length: 0]
> 	2.client_req.body=null
> 	3.server_resp.headers=DefaultHttpHeaders[content-length: 2048, content-type: text/css]
> 	4.server_resp.body=null

```
POI: service.begin=4ms, auth.begin=4ms, process.begin=4ms, biz.begin=4ms, biz.end=18ms, process.end=18ms, service.end=18ms

This shows service begin process the client request after 4ms from I/O layer process, and business process took 14ms (18 - 4) to finish, and I/O layer took 0ms (18 - 18) to send the response to cleint
```



**2. Application Status/Event log** - l it contains application status related information (version, start event, configuration change event, TPS, etc.), below is a sample:

> 2021-09-24 14:11:06,181 INFO org.summerboot.jexpress.nio.server.NioServer.bind() [main] starting... Epoll=false, KQueue=false, multiplexer=AVAILABLE 
> 2021-09-24 14:11:06,633 INFO org.summerboot.jexpress.nio.server.NioServer.bind() [main] [OPENSSL] [TLSv1.2, TLSv1.3] (30s): [TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256, TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256, TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256] 
> 2021-09-24 14:11:07,987 INFO org.summerboot.jexpress.nio.server.NioServer.bind() [main] Server jExpress.v2.1.4@server1 (Client Auth: NONE) is listening on JDK https://0.0.0.0:8989/service 
> 2021-09-24 14:11:07,988 INFO org.summerboot.jexpress.boot.SummerApplication.start() [main] CourtFiling v1.0.0RC1u1_jExpress.v2.1.4@server1_UTF-8 pid#29768@server1 application launched (success), kill -9 or Ctrl+C to shutdown 
> 2021-09-24 14:12:37,010 DEBUG org.summerboot.jexpress.nio.server.NioServer.lambda$bind$3() [pool-5-thread-1] hps=20, tps=20, activeChannel=2, totalChannel=10, totalHit=20 (ping0 + biz20), task=20, completed=20, queue=0, active=0, pool=9, core=9, max=9, largest=9 
> 2021-09-24 14:12:38,001 DEBUG org.summerboot.jexpress.nio.server.NioServer.lambda$bind$3() [pool-5-thread-1] hps=4, tps=4, activeChannel=2, totalChannel=10, totalHit=24 (ping0 + biz24), task=24, completed=24, queue=0, active=0, pool=9, core=9, max=9, largest=9 



## 9. CLI - A/B/Mock mode 

**9.1 Intent**

- Run in mock mode or switch to different implementation

**9.2 Motivation**

- You need to run you application with mocked implementations
- You need to tell the application which component(s) should use the mocked implementation

**9.3 Sample Code**

Use @Service annotation with implTag attribute

```
@Service(implTag="myTag")
```

Full version:

```bash
@Service //this is the default
public class MyServiceImpl implements MyServcie {
	...
}

@Service(implTag="impl1")
public class MyServiceImpl_1 implements MyServcie {
	...
}

@Service(implTag="impl2")
public class MyServiceImpl_2 implements MyServcie {
	...
}
```

run the following command:

```
java -jar my-service.jar -?
```

you will see the following:

> -use <items>      launch application in mock mode, valid values <impl1, impl2>

the command below will run your application with MyServiceImpl_1 implementation

```
java -jar my-service.jar -use impl1
```





## 10. CLI -  list and check duplicated error code

**10.1 Intent**

- Check your error codes defined in your application

**10.2 Motivation**

- With the development of more functions, you might have duplicated error code
- You may need to have a error code list

**10.3 Sample Code**

Add the following if you define all your error codes in AppErrorCode class:

```
@Unique(name="ErrorCode", type = int.class)
```

Full version:

```
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.annotation.Unique;

@Unique(name="ErrorCode", type = int.class)
public interface AppErrorCode extends BootErrorCode {

    int APP_UNEXPECTED_FAILURE = 1001;
    int BAD_REQUEST = 1002;
    int AUTH_CUSTOMER_NOT_FOUND = 1003;
    int DB_SP_ERROR = 1004;
}
```

Add the following if you define all your String codes in AppPOI class:

```
@Unique(name = "POI", type = String.class)
```

Full version:

```
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.boot.annotation.Unique;

@Unique(name = "POI", type = String.class)
public interface AppPOI extends BootPOI {

    String FILE_BEGIN = "file.begin";
    String FILE_END = "file.end";
}
```

run the following command:

```
java -jar my-service.jar -?
```

you will see the following:

>  -unique <item>         list unique: [ErrorCode, POI]

the command below will show you a list of error codes, or error message indicates the duplicated ones:

```
java -jar my-service.jar -unique ErrorCode
java -jar my-service.jar -unique POI
```





## 11. Plugin -  run with external jar files in plugin foler

**10.1 Intent**

- Once the application is on production, need a way to add new features or override existing logic without changing the exiting code

**10.2 Motivation**

- Make the application focus on interface, and its implements could be developed as external jar files
- Make the visitor pattern available at the application level

**10.3 Supported types**

- Services with @service
- Configurations with @ImportResource (TODO)
- Web API Controllers with @Controller (TODO)