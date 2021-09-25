
# Summer Boot focuses on solving the following non-functional and operational maintainability requirements, some of which Spring Boot has (may) not yet provided



## 1. Performance: RESTful Web Services (JAX-RS) with Non-blocking I/O (powered by Netty Reactor - *multiplexing* approach)



**1.1 Intent**

- To solve the performance bottleneck of traditional multi-threading mode at I/O layer

- Quickly develop a RESTful Web Services with JAX-RS

  

**1.2 Motivation**

- Application server is always heavy and some of them are not free, include but not limited to IBM WebSphere, Oracle Glassfish, Payara, Red Hat JBoss or Tomcat

- Netty Reactor - *multiplexing* approach provides an incredible amount of power for developers who need to work down on the socket level, for example when developing custom communication protocols between clients and servers

  

**1.3 Sample Code**

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
                .bind_NIOHandler(HttpRequestHandler.class)
                .run(args, "My Service 1.0");
    }
}

#1. HttpRequestHandler is your ChannelHandler implementation, if you just want to use default functions, just extends org.summerframework.nio.server.BootHttpRequestHandler
#2. create a RESTful API class with JAX-RS style, and annotate this class with @Controller 
 ```

HttpRequestHandler.java

```
@Singleton
public class HttpRequestHandler extends BootHttpRequestHandler {

    @Inject
    protected Authenticator auth;

    @Inject
    protected AppCache cache;

    @Override //role-based validation
    protected boolean authenticateCaller(final RequestProcessor processor, final HttpHeaders httpRequestHeaders, final String httpRequestPath, final ServiceResponse response) throws IOException {
        if (processor.isRoleBased()) {
            auth.verifyToken(httpRequestHeaders, null, response);
            if (response.caller() == null) {
                return false;
            }
        }
        return true;
    }
}

```

A RESTful API class with JAX-RS style, and annotate this class with @Controller 

```
@Singleton
@Controller
@Path(MY_CONTEXT_ROOT + "/mock")
@Consumes(MediaType.APPLICATION_JSON)
public class MyClass {
    @POST
    @PUT
    @Path("/xml/{sset}/{year}/{foo2}")// mock/xml/4,3,2/2012;  author  =   Changski   ;  country =  加拿大     /123
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    public Foo test_JAX_RS(Foo foo,
            String body,
            @CookieParam("ck") String cv, @CookieParam("ck") Cookie ck,
            @PathParam("sset") SortedSet<Foo2> ss, @PathParam("foo2") Foo2 foo2,
            @PathParam("year") int y, @MatrixParam("country") String c, @MatrixParam("author") String a,
            final ServiceResponse response) {
        response.status(HttpResponseStatus.OK);
        if (foo != null) {
            foo.bar = cv;
        }
        return foo;
    }
}
```



## 2. Domain Configuration

**2.1 Intent**

- When you have multiple configuration sets, you need a point from which you can control the configuration

  

**2.2 Motivation**

- During the development, you have dev environment setup and configurations ready for development env. And then you need to have another set of configuration for QA environment, and then another set of configuration for production.

- You don't want to just copy/paste/rename

  Folder structure is like below:

   ```bash
  my-service
  |   my-service.jar
  |   
  +---lib
  |       *.jar
  |       
  \---standalone_dev
      +\---configuration
              cfg_app.properties
              cfg_auth.properties
              cfg_smtp.properties
              cfg_db.properties
              cfg_http.properties
              cfg_nio.properties
              log4j2.xml
              server.keystore
              server.truststore
  |       
  \---standalone_production
      +\---configuration
              cfg_app.properties
              cfg_auth.properties
              cfg_smtp.properties
              cfg_db.properties
              cfg_http.properties
              cfg_nio.properties
              log4j2.xml
              server.keystore
              server.truststore
   ```

  There are five pre-defined configurations:

  > see section 11 (CLI -  generate configuration file) to get the configuration template

  1. cfg_auth.properties - manage the role-based access
  2.  cfg_smtp.properties - manage the Auto-Alert (see section 7)
  3.  cfg_http.properties - manage the Java 11 HTTP Client
  4.  cfg_nio.properties - manage the Netty NIO
  5.  cfg_db.properties - manage the JPA, the format is pure JPA properties

**2.3 Sample Code**

run with dev configuration

```
java -jar my-service.jar -domain dev
```

run with production configuration

```
java -jar my-service.jar -domain production
```



java -jar my-service.jar -sample NioConfig,HttpConfig,SMTPConfig,AuthConfig

## 3. Hot Configuration

**3.1 Intent**

- Need to guarantee service continuity and protect it from configuration changes (3rd party tokens, license keys, etc.)

  

**3.2 Motivation**

- Your Wall Street investors definitely don’t want to stop and restart the cash cow just because you need to update your config file with a renewed 3rd party license key

  

**3.3 Sample Code**

Add the following, **once the config changed, the Summer Boot will automatically load it up and refresh the  AppConfig.CFG**

```
.bind_SummerBootConfig("my config file name", AppConfig.CFG)
```



Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("cfg_app.properties", AppConfig.CFG)
                .bind_NIOHandler(HttpRequestHandler.class)
                .run(args, "My Service 1.0");
    }
}
 ```



AppConfig.java

```
#1. create a config class named 'AppConfig', use volatile to define your config items
#2. Make AppConfig singleton, and AppConfig.instance is the singleton instance
#3. make AppConfig subclass of AbstractSummerBootConfig

public class AppConfig extends AbstractSummerBootConfig {
    public static final AppConfig CFG = new AppConfig();

    private AppConfig() {
    }

	//1. server binding
    @Memo(title = "1. server binding")
    @Config(key = "server.binding.addr")
    private volatile String bindingAddr;
    @Config(key = "server.binding.port")
    private volatile int bindingPort;

    //2. Server keystore
    @Memo(title = "2. Server keystore")
    @Config(key = "server.ssl.KeyStore", StorePwdKey = "server.ssl.KeyStorePwd",
            AliasKey = "server.ssl.KeyAlias", AliasPwdKey = "server.ssl.KeyPwd", required = false)
    @JsonIgnore
    private volatile KeyManagerFactory kmf;
    
    @JsonIgnore
    @Config(key = "vendor.password", validate = Config.Validate.Encrypted)
    protected volatile String sbsVendorPassword;
    
    @Memo(title = "3. Email Alert")
    @Config(key = "email.to.support", validate = Config.Validate.EmailRecipients,
            desc = "CSV format: user1@email.com, user2@email.com")
    protected volatile Set<String> emailToList;
    
    ...
    
    the getter and setter methods...
}
```

cfg_app.properties

```
#####################
# 1. server binding #
#####################
server.binding.addr=0.0.0.0
server.binding.port=5678


###########################
# 2. Server keystore      #
###########################
server.ssl.KeyStore=server.keystore
server.ssl.KeyStorePwd=ENC(xf3jrVGMrv2SzBGtDte2bQ==)
server.ssl.KeyAlias=mydomain
server.ssl.KeyPwd=ENC(xf3jrVGMrv2SzBGtDte2bQ==)

vendor.password=ENC(xf3jrVGMrv2SzBGtDte2bQ==)

###########################
# 3. Email Alert          #
###########################
## CSV format: user1@email.com, user2@email.com
email.to.support=johndoe@email.com, janedoe@email.com
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

    > “-domain <domain name> -authfile <path to root password file>”

    ```
    java -jar my-service.jar -domain <domain name> -authfile /etc/security/my-service.root_pwd
    ```

    Your root password is stored in file /etc/security/my-service.root_pwd, and has the following format:

    ```
    APP_ROOT_PASSWORD=<base64 encoded my app root password>
    ```



**4.3 Implementation** 

- **Auto Encrypt mode**: 

  - **step1**: Wrap the values of password inside **DEC()** as shown below, Here, DEC() is a placeholder that tells the app what to encrypt, and the remaining values are untouched:

    ```
    datasource.password=DEC(plain password)
    ```

  - **step2: Save this config file. The application will aromatically pick up the change in 5 seconds and then encrypt it using the app config password stored in <path to a file which contains config password>,  then replace it with **ECN(**encrypted value**)** in the same file:

    ```
    datasource.password=ENC(encrypted password)
    ```

- **Manual Batch Encrypt mode**: 

  the commands below encrypt all values in the format of “DEC(plain text)” in the specified configuration domain:

  ```
  java -jar my-service.jar -domain <domain name> -encrypt true -authfile <path to root pwd file>
  ```

   In case you happens to know the root password (you ware two hats, the app admin and root admin is same person), you can do the same by providing the root password directly:

  ```
  java -jar my-service.jar -domain <domain name> -encrypt true -auth <my app root password>
  ```

  

- **Manual Batch Decrypt mode**:

  You cannot decrypt without knowing the root password, that is to say, you cannot decrypt with root password file. 

  The command below decrypts all values in the format of “ENC(encrypted text)” in the specified configuration domain:

  ```
  java -jar my-service.jar -domain <domain name> -encrypt false -auth <my app root password>
  ```

  

- **Manual Encrypt mode:** In case you want to manually verify an encrypted sensitive data

   use the command below, compare the output with the encrypted value in the config file:

  ```
  java -jar my-service.jar -encrypt <plain text> -auth <my app root password>
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

Add the following to enable ping on https://host:port/myservice/ping

```
.enable_Ping_HealthCheck("/myservice", "ping")
```

Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance)
                .bind_NIOHandler(HttpRequestHandler.class)
                .enable_Ping_HealthCheck("/myservice", "ping")
                .run(args, "My Service 1.0");
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
.enable_Ping_HealthCheck(AppURI.CONTEXT_ROOT, AppURI.LOAD_BALANCER_HEALTH_CHECK, HealthInspectorImpl.class)
```

Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance)
                .bind_NIOHandler(HttpRequestHandler.class)
                .enable_Ping_HealthCheck(AppURI.CONTEXT_ROOT, AppURI.LOAD_BALANCER_HEALTH_CHECK, HealthInspectorImpl.class)
                .run(args, "My Service 1.0");
    }
}

@Singleton
public class HealthInspectorImpl extends BootHealthInspectorImpl {
    @Inject
    private DataRepository db;

    @Override
    protected void healthCheck(@Nonnull ServiceError error, @Nullable Logger callerLog) {
        error.addErrors(db.ping());
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
# Example: johndoe@olg.ca, janedoe@olg.ca #
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
.bind_AlertMessenger(MyPostOfficeImpl.class)
```

full version:

```
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance)
                .bind_NIOHandler(HttpRequestHandler.class)
                .enable_Ping_HealthCheck("/myservice", "ping")
                .bind_AlertMessenger(MyPostOfficeImpl.class)
                .run(args, "My Service 1.0");
    }
}
```

MyPostOfficeImpl.java

```
@Singleton
public class MyPostOfficeImpl extends BootPostOfficeImpl {

    @Override
    protected String updateAlertTitle(String title) {
        return "[ALERT] " + title;
    }
}

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

> 2021-09-24 14:11:06,181 INFO org.summerframework.nio.server.NioServer.bind() [main] starting... Epoll=false, KQueue=false, multiplexer=AVAILABLE 
> 2021-09-24 14:11:06,633 INFO org.summerframework.nio.server.NioServer.bind() [main] [OPENSSL] [TLSv1.2, TLSv1.3] (30s): [TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256, TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256, TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256] 
> 2021-09-24 14:11:07,987 INFO org.summerframework.nio.server.NioServer.bind() [main] Server Summer.Boot.v2.0.11@DuXiaoPC (Client Auth: NONE) is listening on JDK https://0.0.0.0:8989/service 
> 2021-09-24 14:11:07,988 INFO org.summerframework.boot.SummerApplication.start() [main] CourtFiling v1.0.0RC1u1_Summer.Boot.v2.0.11@DuXiaoPC_UTF-8 pid#29768@DuXiaoPC application launched (success), kill -9 or Ctrl+C to shutdown 
> 2021-09-24 14:12:37,010 DEBUG org.summerframework.nio.server.NioServer.lambda$bind$3() [pool-5-thread-1] hps=20, tps=20, activeChannel=2, totalChannel=10, totalHit=20 (ping0 + biz20), task=20, completed=20, queue=0, active=0, pool=9, core=9, max=9, largest=9 
> 2021-09-24 14:12:38,001 DEBUG org.summerframework.nio.server.NioServer.lambda$bind$3() [pool-5-thread-1] hps=4, tps=4, activeChannel=2, totalChannel=10, totalHit=24 (ping0 + biz24), task=24, completed=24, queue=0, active=0, pool=9, core=9, max=9, largest=9 



## 9. CLI - mock mode 

**9.1 Intent**

- Run in mock mode

**9.2 Motivation**

- You need to run you application with mocked implementations
- You need to tell the application which component(s) should use the mocked implementation

**9.3 Sample Code**

Add the following if you define all your error codes in AppErrorCode class:

```
.bind_SummerBootConfig("cfg_db.properties", DatabaseConfig.CFG, GuiceModule.Mock.db.name(), false)
# false means do not load config file When in mock mode
```

Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance)
        		.bind_SummerBootConfig(Constant.CFG_FILE_DB, DatabaseConfig.CFG, GuiceModule.Mock.db.name(), false)
                .bind_NIOHandler(HttpRequestHandler.class)
                .run(args, "My Service 1.0");
    }
}

public class GuiceModule extends AbstractModule {

    public enum Mock {
        db
    }

    private final Set<Mock> mockItems = new HashSet<>();

    public GuiceModule(Mock... mocks) {
        if (mocks != null && mocks.length > 0) {
            mockItems.addAll(Arrays.asList(mocks));
        }
    }

    private boolean isMock(Mock mockItem) {
        return SummerApplication.isMockMode(mockItem.name()) || mockItems.contains(mockItem);
    }

    @Override
    public void configure() {
        bind(DataRepository.class).to(isMock(Mock.db)
                ? DataRepositoryImpl_Mock.class
                : DataRepositoryImpl_SQLServer.class);
    }
}
 ```

run the following command:

```
java -jar my-service.jar -?
```

you will see the following:

> -mock <items>      launch application in mock mode, valid values <db>

the command below will run your application with a mocked database implementation (DataRepositoryImpl_Mock.class):

```
java -jar my-service.jar -mock db
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
Class errorCodeClass = AppErrorCode.class;
boolean checkDuplicated = true;
.enable_CLI_ListErrorCodes(errorCodeClass, checkDuplicated)
```

Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance)
        		.bind_SummerBootConfig(Constant.CFG_FILE_DB, DatabaseConfig.CFG, GuiceModule.Mock.db.name(), false)
                .bind_NIOHandler(HttpRequestHandler.class)
                .enable_CLI_ListErrorCodes(AppErrorCode.class, true)
                .run(args, "My Service 1.0");
    }
}
 ```

run the following command:

```
java -jar my-service.jar -?
```

you will see the following:

>  -errorcode         list application error code

the command below will show you a list of error codes, or error message indicates the duplicated ones:

```
java -jar my-service.jar -errorcode
```





## 11. CLI -  generate configuration file

**11.1 Intent**

- Keep configuration files clean and in sync with your code

**11.2 Motivation**

- With the development of more functions, like document maintenance, the configuration file may be inconsistent with the code
- You need a way to dump a clean configurations template from code

**11.3 Sample Code**

Add the following if you want to enable dumping a template of MyConfig

```
.enable_CLI_ViewConfig(MyConfig.class)
```

Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance).enable_CLI_ViewConfig(MyConfig.class)
        		.bind_SummerBootConfig(Constant.CFG_FILE_DB, DatabaseConfig.CFG, GuiceModule.Mock.db.name(), false)
                .bind_NIOHandler(HttpRequestHandler.class)
                .enable_CLI_ListErrorCodes(AppErrorCode.class, true)
                .run(args, "My Service 1.0");
    }
}
 ```



run the following command:

```
java -jar my-service.jar -?
```

you will see the following:

> -sample <config>   view config sample, valid values <NioConfig,HttpConfig,SMTPConfig,AuthConfig,AppConfig>

the command below will show you a clean template of AppConfig:

```
java -jar my-service.jar -sample AppConfig
```

