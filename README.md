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
                .run(args, "my app v1.0");
    }
}

#1. HttpRequestHandler is your ChannelHandler implementation, if you just want to use default functions, just extends org.summerframework.nio.server.BootHttpRequestHandler
#2. create a RESTful API class with JAX-RS style, and annotate this class with @Controller 
 ```



## 2. Hot Configuration

**2.1 Intent**

- No longer need to stop and restart microservices when the configuration changes

- Implementing a High Availability/Disaster Recovery (Hot/Hot) Configuration

  

**2.2 Motivation**

- Service continuity needs to be guaranteed when configuration (3rd party token, license key, etc.) changes

  

**2.3 Sample Code**

Add the following

```
.bind_SummerBootConfig("my config file name", AppConfig.CFG)
```



Full version:

 ```bash
public class Main {
    public static void main(String[] args) {
        SummerApplication.bind(Main.class)
        		.bind_SummerBootConfig("my config file name", MyConfig.instance)
                .bind_NIOHandler(HttpRequestHandler.class)
                .run(args, "my app v1.0");
    }
}

#1. create a config class named 'MyConfig', use volatile to define your config items
#2. Make MyConfig singleton, and MyConfig.instance is the singleton instance
#3. make MyConfig subclass of AbstractSummerBootConfig
 ```



## 3. Domain Configuration

**3.1 Intent**

- When you have multiple configuration sets, you need a point from which you can control the configuration

  

**3.2 Motivation**

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

  

**3.3 Sample Code**

run with dev configuration

```
java -jar my-service.jar -domain dev
```

run with production configuration

```
java -jar my-service.jar -domain production
```



## 4. Two-Level-Access protected Configuration

**4.1 Intent**

- **Sensitive Data** - passwords, license keys, signing key (HS256, HS384, HS512 only) and 3rd party tokens (AWS integration token, etc.) cannot be plain text.

- To protect sensitive data in the config files

  

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

**5.1 Intent****

- No longer need to stop and restart microservices when the configuration changes

**5.2 Motivation****

- No longer need to stop and restart microservices when the configuration changes

**5.3 Sample Code****

- No longer need to stop and restart microservices when the configuration changes



## 6. Ping with Health Check/Auto-Shutdown

**6.1 Intent****

- No longer need to stop and restart microservices when the configuration changes

**6.2 Motivation****

- No longer need to stop and restart microservices when the configuration changes

**6.3 Sample Code****

- No longer need to stop and restart microservices when the configuration changes



## 7. Auto-Alert

**7.1 Intent**

- No longer need to stop and restart microservices when the configuration changes

**7.2 Motivation**

- No longer need to stop and restart microservices when the configuration changes

**7.3 Sample Code**

- No longer need to stop and restart microservices when the configuration changes



## 8. Log after response sent

**8.1 Intent**

- No longer need to stop and restart microservices when the configuration changes

**8.2 Motivation**

- No longer need to stop and restart microservices when the configuration changes

**8.3 Sample Code**





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
                .run(args, "my app v1.0");
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
                .run(args, "my app v1.0");
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
                .run(args, "my app v1.0");
    }
}
 ```



run the following command:

```
java -jar my-service.jar -?
```

you will see the following:

> -sample <config>   view config sample, valid values <NioConfig,HttpConfig,SMTPConfig,AuthConfig,AppConfig>

the command below will show your a clean template of AppConfig:

```
java -jar my-service.jar -sample AppConfig
```

