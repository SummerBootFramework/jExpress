-- MySQL dump 10.13  Distrib 5.6.25, for Win64 (x86_64)
--
-- Host: localhost    Database: test
-- ------------------------------------------------------
-- Server version	5.6.25-log

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `filelocation`
--

DROP TABLE IF EXISTS `filelocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `filelocation`
(
    `id`        bigint(20) NOT NULL AUTO_INCREMENT,
    `createdTs` datetime      NOT NULL,
    `updatedTs` datetime      NOT NULL,
    `version`   int(11) DEFAULT NULL,
    `location`  varchar(1000) NOT NULL,
    `name`      varchar(255)  NOT NULL,
    `tenant_ID` bigint(20) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UKn8p5jj4eidac2047qk0f3j77g` (`tenant_ID`,`name`)
) ENGINE=MyISAM AUTO_INCREMENT=1575 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `filelocation`
--

LOCK
TABLES `filelocation` WRITE;
/*!40000 ALTER TABLE `filelocation` DISABLE KEYS */;
INSERT INTO `filelocation`
VALUES (1574, '2026-02-12 14:32:42', '2026-02-12 14:32:42', 1, 'location-1770906762275', 'name2', 1);
/*!40000 ALTER TABLE `filelocation` ENABLE KEYS */;
UNLOCK
TABLES;

--
-- Table structure for table `tenant`
--

DROP TABLE IF EXISTS `tenant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `tenant`
(
    `id`        bigint(20) NOT NULL AUTO_INCREMENT,
    `createdTs` datetime     NOT NULL,
    `updatedTs` datetime     NOT NULL,
    `version`   int(11) DEFAULT NULL,
    `createdBy` varchar(255) DEFAULT NULL,
    `createdIP` varchar(255) DEFAULT NULL,
    `updatedBy` varchar(255) DEFAULT NULL,
    `updatedIP` varchar(255) DEFAULT NULL,
    `dn`        varchar(255) NOT NULL,
    `ldapId`    varchar(128) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_gpnyej5lihkutivplyfxblosn` (`dn`),
    UNIQUE KEY `UK_gqaem028qpv7kuor2syqtn3l1` (`ldapId`)
) ENGINE=MyISAM AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tenant`
--

LOCK
TABLES `tenant` WRITE;
/*!40000 ALTER TABLE `tenant` DISABLE KEYS */;
INSERT INTO `tenant`
VALUES (1, '2022-10-27 23:22:08', '2022-10-27 23:22:08', 0, NULL, NULL, NULL, NULL, 'dn2', 'ldapId2');
/*!40000 ALTER TABLE `tenant` ENABLE KEYS */;
UNLOCK
TABLES;
/*
 * Copyright 2005-2026 Du Law Office - jExpress, The Summer Boot Framework Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-02-18 16:33:22
