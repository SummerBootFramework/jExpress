# Summer Boot Framework 

[View Changelog (CHANGES)](CHANGES)


Summer Boot Framework was initiated by a group of developers in 2004 to provide a high-performance, free, customizable, and lightweight Netty JAX-RS RESTful, WebSocket, and gRPC
service with JPA and other powerful reusable non-functional features. Since 2011, it has been adopted by several Toronto law firms to customize their back-end services.

Its sub-project, jExpress (a.k.a. Summer Boot Framework Core), focuses on solving the following non-functional and operational maintainability requirements, which are (probably)
not yet available in Spring Boot.

![Summer Boot Overview](SummerBootOverview.png)

**Open Source History:** jExpress was initially open-sourced on MS MySpace in Sep 2006. Due to the shutdown of MySpace, this framework was migrated to a server sponsored by one of
the law firms in October 2011, then to GitLab in Dec 2016, and eventually to GitHub in Sep 2021.

> Disclaimer: We really had a great time with GitLab until 2021 when we realized one of the contributor's employers was also using GitLab at that time. We decided to move to GitHub
> instead to avoid incurring unnecessary hassles.

---

## 1. Performance: RESTful Web Services (JAX-RS) with Non-blocking I/O (powered by Netty Reactor - *multiplexing* approach)

### 1.1 Intent

* To solve the performance bottleneck of traditional multi-threading mode at the I/O layer.
* Quickly develop a RESTful Web Service with JAX-RS with minimal code required.

### 1.2 Motivation

* Application servers are always heavy, and some of them are not free, including but not limited to IBM WebSphere, Oracle Glassfish, Payara, Red Hat JBoss, or Tomcat.
* Netty Reactor's *multiplexing* approach provides an incredible amount of power for developers who need to work down on the socket level, for example, when developing custom
  communication protocols between clients and servers.

### 1.3 Sample Code

* **step1** - add the jExpress dependency to pom.xml

    ```xml
    <dependency>
        <groupId>org.summerboot</groupId>
        <artifactId>jexpress</artifactId>
    </dependency>
    ```

  or in your pom.xml file you can add the Maven 2 snapshot repository if you want to try out the SNAPSHOT versions:

    ```xml
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

* **step2** - a main class to launch the application

    ```java
    import org.summerboot.jexpress.boot.SummerApplication;

    public class Main {
    
        public static void main(String... args) {
            SummerApplication.run();
        }
    }
    ```

  or if you need to initialize or run anything before the application starts:

    ```java
    import com.google.inject.Key;
    import com.google.inject.name.Names;
    import java.io.File;
    import org.apache.commons.cli.Options;
    import org.apache.logging.log4j.LogManager;
    import org.apache.logging.log4j.Logger;
    import org.summerboot.jexpress.boot.SummerRunner;
    import org.summerboot.jexpress.boot.annotation.Order;
    import org.summerboot.jexpress.boot.SummerInitializer;
    
    @Order(1)
    public class MainRunner implements SummerInitializer, SummerRunner {
    
        private static final Logger log = LogManager.getLogger(MainRunner.class);
    
        @Override
        public void initCLI(Options options) {
            log.info("");
        }
    
        @Override
        public void initApp(File configDir) {
            log.info(configDir);
        }
    
        @Override
        public void run(RunnerContext context) throws Exception {
            log.debug("beforeStart");
        }
    }
    ```

  or put everything together:

    ```java
    import com.google.inject.Key;
    import com.google.inject.name.Names;
    import java.io.File;
    import org.apache.commons.cli.Options;
    import org.apache.logging.log4j.LogManager;
    import org.apache.logging.log4j.Logger;
    import org.summerboot.jexpress.boot.SummerApplication;
    import org.summerboot.jexpress.boot.SummerRunner;
    import org.summerboot.jexpress.boot.annotation.Order;
    import org.summerboot.jexpress.boot.SummerInitializer;
    
    public class Main implements SummerInitializer, SummerRunner {
    
        private static final Logger log = LogManager.getLogger(Main.class);
    
        @Override
        public void initCLI(Options options) {
            log.info("");
        }
    
        @Override
        public void initApp(File configDir) {
            log.info(configDir);
        }
    
        @Override
        public void run(RunnerContext context) throws Exception {
            log.debug("beforeStart");
        }
        
        public static void main(String... args) {
            SummerApplication.run();
        }
    }
    ```

* **step3** - A RESTful API class with JAX-RS style, and annotate this class with `@Controller`

    ```java
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
    import org.summerboot.jexpress.nio.server.SessionContext;
    
    @Singleton
    @Controller
    @Path("/hellosummer")
    public class MyController {
    
        @GET
        @Path("/account/{name}")
        @Produces({MediaType.TEXT_PLAIN})
        public String hello(@NotNull @PathParam("name") String myName) { // both Nonnull or NotNull works
            return "Hello " + myName;
        }
    
        @POST
        @Path("/account/{name}")
        @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML}) // require request header Content-Type: application/json or Content-Type: application/xml
        @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML}) // require request header Accept: application/json or Accept: application/xml
        public ResponseDto hello_no_validation_unprotected_logging(@PathParam("name") String myName, RequestDto request) {
            return new ResponseDto();
        }
    
        /**
         * Three features:
         * <p> 1. auto validate JSON request by @Valid and @NotNull annotation
         * <p> 2. protected user credit card and privacy information from being logged by @Log annotation
         * <p> 3. mark performance POI (point of interest) by using SessionContext.poi(key), see section#8.3
         *
         * @param myName
         * @param request
         * @param context
         * @return
         */
        @POST
        @Path("/hello/{name}")
        @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML}) // require request header Content-Type: application/json or Content-Type: application/xml
        @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML}) // require request header Accept: application/json or Accept: application/xml
        @Log(maskDataFields = {"creditCardNumber", "clientPrivacy", "secretList"})
        public ResponseDto hello_auto_validation_protected_logging_markWithPOI(@NotNull @PathParam("name") String myName, @NotNull @Valid RequestDto request, final SessionContext context) {
            context.poi("DB begin"); // about POI, see section8.3
            // DB access and it takes time ...
            context.poi("DB end");
    
            context.poi("gRPC begin"); // about POI, see section8.3
            // gRPC access and it takes time ...
            context.poi("gRPC end");
    
           context.status(HttpResponseStatus.CREATED); // override default HTTP response status
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
            private final List<String> secretList = List.of("aa", "bb");
        }
    }
    ```

**Below is the log of `hello_no_validation_unprotected_logging()`:**

```bash
2023-04-20T19:48:12,523 INFO org.summerboot.jexpress.nio.server.BootHttpRequestHandler.() [pool-4-thread-1]
request_1.caller=null
request_1=POST /hellosummer/account1/123, dataSize=71, KeepAlive=true,
chn=[id: 0xac275188, L:/127.0.0.1:8311 - R:/127.0.0.1:22517], ctx=475223663,
hdl=org.summerboot.jexpress.nio.server.BootHttpRequestHandler@578198d9
responsed_1=200 OK, error=0, queuing=7ms, process=34ms, response=36ms, cont.len=68bytes
POI: service.begin=7ms, process.begin=8ms, biz.begin=33ms, biz.end=33ms, process.end=34ms, service.end=36ms,
1.client_req.headers=DefaultHttpHeaders[Connection: keep-alive, Accept: application/json, Content-Type: application/json, Content-Length: 71, Host: localhost:8311, User-Agent: Apache-HttpClient/4.5.13 (Java/17.0.2)]
2.client_req.body={
"creditCardNumber" : "123456",
"shoppingList" : [ "a", "b" ]
}
3.server_resp.headers=DefaultHttpHeaders[X-Reference: 1, X-ServerTs: 2023-04-20T19:48:12.519-04:00]
4.server_resp.body={"clientPrivacy":"secret: mylicenseKey","clientNonPrivacy":"shared","secretList":["aa","bb"]
}
Memo: n/a
````

**Below is the log of with `@Log(maskDataFields = {"creditCardNumber", "clientPrivacy", "secretList"})`**

```bash
2023-04-20T19:53:47,167 INFO org.summerboot.jexpress.nio.server.BootHttpRequestHandler.() [pool-4-thread-2]
request_2.caller=null
request_2=POST /hellosummer/account2/123, dataSize=71, KeepAlive=true,
chn=[id: 0x3748e908, L:/127.0.0.1:8311 - R:/127.0.0.1:22619], ctx=950626969,
hdl=org.summerboot.jexpress.nio.server.BootHttpRequestHandler@578198d9
responsed_2=201 Created, error=0, queuing=1ms, process=217ms, response=219ms, cont.len=68bytes
POI: service.begin=1ms, process.begin=1ms, biz.begin=217ms, db.begin=217ms, db.end=217ms, gRPC.begin=217ms,
gRPC.end=217ms, biz.end=217ms, process.end=217ms, service.end=219ms,
1.client_req.headers=DefaultHttpHeaders[Connection: keep-alive, Accept: application/json, Content-Type: application/json, Content-Length: 71, Host: localhost:8311, User-Agent: Apache-HttpClient/4.5.13 (Java/17.0.2)]
2.client_req.body={
"creditCardNumber" : "***",
"shoppingList" : [ "a", "b" ]
}
3.server_resp.headers=DefaultHttpHeaders[X-Reference: 2, X-ServerTs: 2023-04-20T19:53:47.159-04:00]
4.server_resp.body={"clientPrivacy":"***","clientNonPrivacy":"shared","secretList":["***"]}
Memo: n/a
```

### 1.4 Sample Code: Use `<AlternativeName>`

Use the `@Controller.AlternativeName` field as below. This controller class will only be available with the `-use RoleBased` parameter to launch the application. See *section\#9*.

```java
@Controller(AlternativeName = "RoleBased")
```

### 1.5 Sample Code: PING

See *section\#5*.

To make the controller enable the ping API at `/hellosummer/ping`, you do not want to log it at all, since ping will occur every 5 seconds.

Extends `PingController` or `BootController` as below:

```java
import org.summerboot.jexpress.nio.server.ws.rs.PingController;

@Controller
@Path("/hellosummer")
public class MyController extends PingController {
    // ...
}
```

or

```java
import org.summerboot.jexpress.nio.server.ws.rs.BootController;

@Controller
@Path("/hellosummer")
public class MyController extends BootController {
    // ...
}
```

or use `@Ping` on a `GET` method. You need to add the OpenAPI doc yourself or copy it from `PingController`.

```java
import org.summerboot.jexpress.boot.annotation.Ping;

@Controller
@Path("/hellosummer")
public class MyController {
    @GET
    @Path("/ping")
    @Ping
    public void hello() {
    }
}
```

### 1.6 Sample Code: Role Based Access

* **step1**: Extends `BootController` as below:

  ```java
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

* **step2**: define an `Authenticator` service with annotation `@Service(binding = Authenticator.class)`

  simply extends `BootAuthenticator`:

  ```java
  import com.google.inject.Singleton;
  import io.netty.handler.codec.http.HttpHeaders;
  import javax.naming.NamingException;
  import org.summerboot.jexpress.boot.annotation.Service;
  import org.summerboot.jexpress.nio.server.RequestProcessor;
  import org.summerboot.jexpress.nio.server.SessionContext;
  import org.summerboot.jexpress.security.auth.Authenticator;
  import org.summerboot.jexpress.security.auth.AuthenticatorListener;
  import org.summerboot.jexpress.security.auth.BootAuthenticator;
  import org.summerboot.jexpress.security.auth.Caller;
  import org.summerboot.jexpress.security.auth.User;

  @Singleton
  @Service(binding = Authenticator.class)
  public class MyAuthenticator extends BootAuthenticator<Long> {

      @Override
      protected Caller authenticate(String usename, String password, Long metaData, AuthenticatorListener listener, SessionContext context) throws NamingException {
          // verify username and password against LDAP
          if ("wrongpwd".equals(password)) {
              return null;
          }
          // build a caller to return
          long tenantId = 1;
          String tenantName = "jExpress Org";
          long userId = 456;
          User user = new User(tenantId, tenantName, userId, usename);
          user.addGroup("AdminGroup");
          user.addGroup("EmployeeGroup");
          return user;
      }

      @Override
      public boolean customizedAuthorizationCheck(RequestProcessor processor, HttpHeaders httpRequestHeaders, String httpRequestPath, SessionContext context) throws Exception {
          return true;
      }
  }
  ```

* **step3**: define Role-Group mapping in **cfg\_auth.properties**

  Format of **role-group mapping**: `roles.<role name>.groups`=csv list of groups
  Format of **role-user mapping**: `roles.<role name>.users`=csv list of users

  ```properties
  roles.AppAdmin.groups=AdminGroup
  #roles.AppAdmin.users=admin1, admin2
  roles.Employee.groups=EmployeeGroup
  #roles.Employee.users=employee1, employee2
  ```

-----

## 2\. Auto Generated Configuration Files

### 2.1 Intent

* Keep configuration files clean and in sync with your code.

### 2.2 Motivation

* With the development of more functions, like document maintenance, the configuration file may be inconsistent with the code.
* You need a way to dump a clean configurations template from code.

### 2.3 Auto generated configuration files - for all applications

* **log4j2.xml**

  > 1. Requires JVM arg: `-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector`
  > 2. Required total disk space: around 200MB
  > 3. Archive logs: by DAY and split them by MINUTE
  > 4. Default log level is tuned for development

* **cfg\_smtp.properties**

  > for sending email alerts

* **cfg\_auth.properties**

  > Authentication: Sign JWT and parse JWT

  > Authorization: Role and User Group mapping

### 2.4 Auto generated configuration files - application type based

* **cfg\_nio.properties**

  > generated when application contains `@Controller` (running as RESTFul API service)

* **cfg\_grpc.properties**

  > generated when application contains gRPC service (running as gRPC service)

* **All other application specific configurations, which:**

  > 1. annotated with `@ImportResource`

  > 2. extends `org.summerboot.jexpress.boot.config.BootConfig` or implements `org.summerboot.jexpress.boot.config.JExpressConfig`

  > 3. implemented as a singleton via Eager Initialization

  > ```
    > example\#1: `public static final AppConfig cfg = BootConfig.instance(AppConfig.class);`
    > ```

  > ```
    > example\#2: `public static final AppConfig cfg = new AppConfig();`
    > ```

-----

## 3\. Hot Configuration

### 3.1 Intent

* Need to guarantee service continuity and protect it from configuration changes (3rd party tokens, license keys, etc.).

### 3.2 Motivation

* Your Wall Street investors definitely do not want to stop and restart the "cash cow" just because you need to update the config file with a renewed 3rd party license key.

### 3.3 Sample Code

Once the configuration files have changed, jExpress will automatically load it up and refresh the singleton instance.

**MyConfig.java**

```java
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.File;
import java.util.Properties;

import org.summerboot.jexpress.boot.config.BootConfig;
import org.summerboot.jexpress.boot.config.ConfigUtil;
import org.summerboot.jexpress.boot.config.annotation.Config;
import org.summerboot.jexpress.boot.config.annotation.ConfigHeader;
import org.summerboot.jexpress.boot.config.annotation.ImportResource;

/**
 *
 * @author Changski Tie Zheng Zhang
 */
@ImportResource("cfg_app.properties")
public class MyConfig extends BootConfig {

    public static final MyConfig cfg = new MyConfig();

    private MyConfig() {
    }

    @ConfigHeader(title = "My Header description")
    @JsonIgnore
    @Config(key = "my.licenseKey", validate = Config.Validate.Encrypted, required = true)
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

During application start, it will generate `cfg_app.properties` if it doesn't exist, although the following code will generate the `cfg_app.properties` template at any time:

```java
public static void main(String[] args) {
    String template = MyConfig.generateTemplate(MyConfig.class);
    System.out.println(template);
}
```

**cfg\_app.properties:**

```bash
#########################
# My Header description #
#########################
my.key.name=DEC(plain password)
```

-----

## 4\. Protected Configuration

### 4.1 Intent

* Sensitive Data - passwords, license keys, signing key (HS256, HS384, HS512 only), and 3rd party tokens (AWS integration token, etc.) cannot be plain text.
* Protect sensitive data in the config files - just like the "one ring to rule them all" in The Lord of the Rings.
  * **One-Way Protection:** The application admin can only write to the config file with plain text but cannot read encrypted sensitive data from the config file.
  * **Two-Level Protection:** The application root password is managed/protected by the root admin. It controls sensitive data encryption/decryption, and it should not be managed
      by the application admin.

### 4.2 Motivation

* You want to protect sensitive data in the config files, and you encrypt them with a key.

* Nobody hangs the key on the door it just locked, so you do need to protect the key, which just locks (encrypts) the sensitive data in your safe box, and you do NOT want to keep
  it hardcoded in your source code.

* You really do NOT want to enter an endless loop by keep creating a new key to protect the original key, which protects the sensitive data.

* You only need one extra root password to encrypt/decrypt the sensitive data, and this root password is not with your application admin.

* **Two-Level Access**: who controls what

  * **Level 1: Application Admin** - can update application sensitive data as plain text **without knowing the root password nor how to encrypt/decrypt**. The plain text
      sensitive data will be automatically encrypted by the running application, or manually encrypted by the app admin without knowing the root password.
  * **Level 2: Root (Linux/Windows) Admin** - controls the root password in a protected file, which is used to encrypt/decrypt the sensitive data stored inside the application
      config file, and is only accessible by the root admin but not the application admin or other users.

  Your application is launched as a system service controlled by the root admin and runs with:

  `"-authfile <path to root password file>"`

  ```bash
  java -jar jExpressApp.jar -authfile /etc/security/my-service-name.root_pwd
  ```

  Your root password is stored in file `/etc/security/my-service.root_pwd`, and has the following format:

  ```bash
  APP_ROOT_PASSWORD=<base64 encoded my app root password>
  ```

### 4.3 Implementation

* **Auto Encrypt mode**:

  * **step1**: Wrap the plain text password with `DEC()` as shown below. Here, `DEC()` is a marker that tells the app what to encrypt, and the remaining values are untouched:
      ```properties
      datasource.password=DEC(plain password)
      ```
  * **step2**: Save this config file. The application will automatically pick up the change in 5 seconds and then encrypt it using the app config password stored in
      `<path to a file which contains config password>`. Then, it will replace it with `ECN(encrypted value)` in the same file:
      ```properties
      datasource.password=ENC(encrypted password)
      ```

* **Manual Batch Encrypt mode**:
  The commands below encrypt all values in the format of `DEC(plain text)` in the specified configuration environment:

  ```bash
  java -jar my-service.jar -cfgdir <path to config folder> -encrypt -authfile <path to root pwd file>
  ```

  In case you happen to know the root password (you wear two hats, the app admin and root admin is the same person), you can do the same by providing the root password directly:

  ```bash
  java -jar my-service.jar -cfgdir <path to config folder> -encrypt -auth <my app root password>
  ```

* **Manual Batch Decrypt mode**:
  You cannot decrypt without knowing the root password. That is to say, you cannot decrypt with the root password file.

  The command below decrypts all values in the format of `ENC(encrypted text)` in the specified configuration environment:

  ```bash
  java -jar my-service.jar -cfgdir <path to config folder> -decrypt -auth <my app root password>
  ```

> **Note:**
>
> * The comments in the configuration file will not be auto/batch encrypted/decrypted.
> * `changeit` is the default `<app root password>` when `-authfile` or `-auth` option is not specified.

-----

## 5\. Ping with Load Balancer

### 5.1 Intent

* Work with a load balancer.

### 5.2 Motivation

* Need to tell the load balancer my service status but do not affect my application log.

### 5.3 Sample Code

Add the following to enable ping on `https://host:port/hellosummer/ping`:

```java
@Ping
@GET
```

Full version:

```java
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
    public void ping() { // or whatever method name you like
    }

    @GET
    @Path("/hello/{name}")
    public String hello(@PathParam("sce") String name) {
        return "Hello " + name;
    }
}
```

-----

## 6\. Ping with Health Check/Auto-Shutdown

### 6.1 Intent

* Tell the load balancer I'm not in a good state.

### 6.2 Motivation

* When one of your application/service's dependencies (database, 3rd party service, etc.) is down, the framework will automatically respond with an error to the load balancer so
  that no upcoming requests will route to this node.

### 6.3 Sample Code

Add the following:

```java
@Service(binding = HealthInspector.class)
Class MyHealthInspector extends BootHealthInspectorImpl
```

Full version:

```java
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.logging.log4j.Logger;
import org.summerboot.jexpress.boot.annotation.Service;
import org.summerboot.jexpress.boot.instrumentation.BootHealthInspectorImpl;
import org.summerboot.jexpress.boot.instrumentation.HealthInspector;
import org.summerboot.jexpress.nio.server.domain.Err;
import org.summerboot.jexpress.nio.server.domain.ServiceError;

@Service(binding = HealthInspector.class)
public class MyHealthInspector extends BootHealthInspectorImpl {

    @Override
    protected void healthCheck(@Nonnull ServiceError error, @Nullable Logger callerLog) {
        if (error detected){
            error.addError(new Err(123, "error tag", "error meessage", null));
        }
    }
}
```

-----

## 7\. Auto-Alert

### 7.1 Intent

* Get noticed before someone knocks on your door.

### 7.2 Motivation

* The support team wants to get noticed when some expected issues happen, like a database being down or a network issue.
* The development team wants to get noticed when some unexpected issues happen, like a defect or bug causing a runtime exception.
* But you don't want to be bombed by emails.

### 7.3 Sample Code

There is a pre-defined config: `cfg_smtp.properties`.

```properties
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

```java
nothing,
the only
thing you
need to do
is update
the cfg_smtp.properties
```

-----

## 8\. Log after response sent

### 8.1 Intent

* Show the request log and response log from the same client together.
* The client receives a response without waiting for the application to finish the logging/reporting as before.
* Save disk space for log files.
* Easy to identify where the log file is generated and by which server.

### 8.2 Motivation

* Request\#1 is logged, and its response is logged separately after hundreds of lines.
* You don't want to keep the client waiting just because your application is doing logging.
* The log file will be zipped automatically when the file size exceeds the predefined limit.
* The log file name will be automatically filled with the server name when created.

### 8.3 And there are 2 type of separated logs:

1. **Request log** - It contains client request-related information. A single log entry contains the following information:

   1. Security/Business required information: On which server life/session/location, who did what, when, how, and from where.
   2. Performance tuning required information: POI (point of interest) of the key events.
   3. App support required information: The full conversation between the client and the service.
   4. App Debug required information: The full conversation between the service and a 3rd party.

   Log sample:

   ```bash
   [411043] 2025-08-17T11:50:58,429 WARN org.summerboot.jexpress.nio.server.BootHttpRequestHandler.() [Netty-HTTP.Biz-5-vt-1] [411043-2 /127.0.0.1:8311] [200 OK, error=0, queuing=1ms, process=5798ms, response=5801ms] HTTP/1.1 GET /hellosummer/services/appname/v1/aaa/111;m4=88;m5=99;m1=123 ; m2=456 /bbb/a2;    m3=789  , /127.0.0.1:21513=null
	POI.t0=2025-08-17T11:50:52.627-04:00 service.begin=0ms, process.begin=1ms, biz.begin=6ms, biz.end=5795ms, process.end=5798ms, service.end=5801ms, 
	1.client_req.headers=DefaultHttpHeaders[Connection: keep-alive, Accept: application/json, Authorization1: Bearer eyJhbGciOiJSUzUxMiJ9.eyJqdGkiOiIxLTQ1NkAzOTUwMzktMiIsInN1YiI6InR6aGFuZyIsImF1ZCI6WyJBZG1pbkdyb3VwIiwiRW1wbG95ZWVHcm91cCJdLCJ0ZW5hbnROYW1lIjoiakV4cHJlc3MgT3JnIiwiZXhwIjoxNzU1MjgwNzg0LCJpYXQiOjE3NTUxOTQzODR9.m9pkWGSWvCXj1sGESP4iw6l1L8Hqp37k4WEP-N9jhbP9NTsIOeKygsVo5DDDGVZQg7BbYpTW_iwIUI2ChNZgKvvzuWUEkBlfMSMKL4IbzlJyFm6qnXS2zzsE4vOSp44zJE1H4Ab7g3H3Qdwr_3GOy95gSmDZjEW0lKiXt4OeGfJ0iIIbuMgF1bvAjdHqC6SwCbfVjCjMDuhUVBIKF1DIFKw9bxZn7knePXSZFxQng3s2aQmFunS2R7dkOMZmZGzLhmE6N1Q30gUpGgPxv4l9owrMxmfE2k-mpmGMnPuBf4SBETgQbDnB9gels-kC-lmGGVVL6fC_eV6-G-Sp9dL4I5lsw2iCWJD1E4hh7vhvFwAOnNTzLCN6dtlJ9M04cn1eym6lK1iLxFGO8O6WxUP6oI-9OVnEQF7spKig5ajqYlatVUu9tHg9Araxeed077MIe2IJTd8PDDtdx2AEZn6iS-QOdT-31ibyHaYZ-jbXHp1qgw7eCY3fp5RpN-UNBWnSkAJtN1RLt0jtKR1EuF1bnVls1X1JSlY80rsVYbkdzGlZr5PSZTItk0FHkUyFD8waSsWLI8DrLbvt21zaoRaseX1F4bt9QyFsL2Vp2-uCBVcR-vfA0ng6cVgpwAkDCfEB-mL3JmVbBza_1zZDMYYpbBN0W_wGCa42torStj1-EuI, Host: localhost:8311, User-Agent: Apache-HttpClient/4.5.13 (Java/21.0.5), content-length: 0]
	2.client_req.body(0 bytes)=null
	3.server_resp.headers=DefaultHttpHeaders[X-Reference: 411043-2, X-ServerTs: 2025-08-17T11:50:58.426-04:00, content-type: application/json;charset=UTF-8, content-length: 158, connection: keep-alive]
	4.server_resp.body(158 bytes)={"name":"testMatrixParamWithRegex","value":"pa1=111, pa2=a2, m1=123, m2=456, m3=789, m4=88, txId=411043-2","receivedTime":"2025-08-17T11:50:58.4226452-04:00"}
   ```

   ```bash
   POI: service.begin=4ms, auth.begin=4ms, process.begin=4ms, biz.begin=4ms, biz.end=18ms, process.end=18ms, service.end=18ms

   This shows service begins to process the client request after 4ms from I/O layer process, and business process took 14ms (18 - 4) to finish, and I/O layer took 0ms (18 - 18) to send the response to client.
   ```

2. **Application Status/Event log** - It contains application status-related information (version, start event, configuration change event, TPS, etc.). Below is a sample:

   ```bash
   2021-09-24 14:11:06,181 INFO org.summerboot.jexpress.nio.server.NioServer.bind() [main] starting... Epoll=false,
   KQueue=false, multiplexer=AVAILABLE
   2021-09-24 14:11:06,633 INFO org.summerboot.jexpress.nio.server.NioServer.bind() [main] [OPENSSL] [TLSv1.2, TLSv1.3] (
   30s): [TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256, TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256, TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256]
   2021-09-24 14:11:07,987 INFO org.summerboot.jexpress.nio.server.NioServer.bind() [main] Server
   jExpress.v2.1.4@server1 (Client Auth: NONE) is listening on JDK [https://0.0.0.0:8989/service](https://0.0.0.0:8989/service)
   2021-09-24 14:11:07,988 INFO org.summerboot.jexpress.boot.SummerApplication.start() [main] CourtFiling
   v1.0.0RC1u1_jExpress.v2.1.4@server1_UTF-8 pid#29768@server1 application launched (success), kill -9 or Ctrl+C to
   shutdown
   2021-09-24 14:12:37,010 DEBUG org.summerboot.jexpress.nio.server.NioServer.lambda$bind$3() [pool-5-thread-1] hps=20,
   tps=20, activeChannel=2, totalChannel=10, totalHit=24 (ping0 + biz24), task=24, completed=24, queue=0, active=0,
   pool=9,
   core=9, max=9, largest=9
   2021-09-24 14:12:38,001 DEBUG org.summerboot.jexpress.nio.server.NioServer.lambda$bind$3() [pool-5-thread-1] hps=4,
   tps=4, activeChannel=2, totalChannel=10, totalHit=24 (ping0 + biz24), task=24, completed=24, queue=0, active=0,
   pool=9,
   core=9, max=9, largest=9
   ```

-----

## 9\. CLI - A/B/Mock mode

### 9.1 Intent

* Run in mock mode or switch to a different implementation.

### 9.2 Motivation

* You need to run your application with mocked implementations.
* You need to tell the application which component(s) should use the mocked implementation.

### 9.3 Sample Code

Use the `@Service` annotation with the `AlternativeName` attribute.

```java
@Service(AlternativeName = "myImpl")
```

Full version:

```java

@Service //this is the default
public class MyServiceImpl implements MyServcie {
    // ...
}

@Service(AlternativeName = "impl1")
public class MyServiceImpl_1 implements MyServcie {
    // ...
}

@Service(AlternativeName = "impl2")
public class MyServiceImpl_2 implements MyServcie {
    // ...
}
```

run the following command:

```bash
java -jar my-service.jar -?
```

you will see the following:

> \-use \<items\> launch application in mock mode, valid values \<impl1, impl2\>

The command below will run your application with the `MyServiceImpl_1` implementation:

```bash
java -jar my-service.jar -use impl1
```

-----

## 10\. CLI - list and check duplicated error code

### 10.1 Intent

* Check your error codes defined in your application.

### 10.2 Motivation

* With the development of more functions, you might have duplicated error codes.
* You may need to have an error code list.

### 10.3 Sample Code

Add the following if you define all your error codes in the `AppErrorCode` class:

```java
@Unique(name = "ErrorCode", type = int.class)
```

Full version:

```java
import org.summerboot.jexpress.boot.BootErrorCode;
import org.summerboot.jexpress.boot.annotation.Unique;

@Unique(name = "ErrorCode", type = int.class)
public interface AppErrorCode extends BootErrorCode {

    int APP_UNEXPECTED_FAILURE = 1001;
    int BAD_REQUEST = 1002;
    int AUTH_CUSTOMER_NOT_FOUND = 1003;
    int DB_SP_ERROR = 1004;
}
```

Add the following if you define all your String codes in the `AppPOI` class:

```java
@Unique(name = "POI", type = String.class)
```

Full version:

```java
import org.summerboot.jexpress.boot.BootPOI;
import org.summerboot.jexpress.boot.annotation.Unique;

@Unique(name = "POI", type = String.class)
public interface AppPOI extends BootPOI {

    String FILE_BEGIN = "file.begin";
    String FILE_END = "file.end";
}
```

run the following command:

```bash
java -jar my-service.jar -?
```

you will see the following:

> \-unique \<item\> list unique: \[ErrorCode, POI\]

The command below will show you a list of error codes, or an error message indicates the duplicated ones:

```bash
java -jar my-service.jar -unique ErrorCode
java -jar my-service.jar -unique POI
```

-----

## 11\. Plugin - run with external jar files in plugin folder

### 11.1 Intent

* Once the application is on production, you need a way to add new features or override existing logic without changing the existing code.

### 11.2 Motivation

* Make the application focus on the interface, and its implements can be developed as external jar files.
* Make the visitor pattern available at the application level.
* You can even put all your logic in one or multiple external jar files developed by different teams as plugins.


