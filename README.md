# Summer Boot focuses on solving the following non-functional and operational maintainability requirements, some of which Spring Boot has (may) not yet provided





## 1. Rapid development of microservices with Non-blocking I/O (powered by Netty Reactor - *multiplexing* approach)

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

> .bind_SummerBootConfig("my config file name", AppConfig.CFG)



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

> java -jar my-service.jar -domain dev

run with production configuration

> java -jar my-service.jar -domain production
