-- MySQL dump 10.13  Distrib 8.0.29, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: cc4c_dev1
-- ------------------------------------------------------
-- Server version	8.0.29

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `administrator`
--

DROP TABLE IF EXISTS `administrator`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `administrator` (
  `admin_id` char(7) NOT NULL COMMENT '管理员id',
  `admin_password` varchar(16) NOT NULL COMMENT '管理员密码',
  PRIMARY KEY (`admin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `administrator`
--

LOCK TABLES `administrator` WRITE;
/*!40000 ALTER TABLE `administrator` DISABLE KEYS */;
/*!40000 ALTER TABLE `administrator` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `administrator_allows_blog`
--

DROP TABLE IF EXISTS `administrator_allows_blog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `administrator_allows_blog` (
  `admin_id` char(7) NOT NULL COMMENT '管理员id',
  `blog_id` varchar(25) NOT NULL COMMENT '博客id',
  PRIMARY KEY (`admin_id`,`blog_id`),
  KEY `administrator_allows_blog_fk2` (`blog_id`),
  CONSTRAINT `administrator_allows_blog_fk1` FOREIGN KEY (`admin_id`) REFERENCES `administrator` (`admin_id`),
  CONSTRAINT `administrator_allows_blog_fk2` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `administrator_allows_blog`
--

LOCK TABLES `administrator_allows_blog` WRITE;
/*!40000 ALTER TABLE `administrator_allows_blog` DISABLE KEYS */;
/*!40000 ALTER TABLE `administrator_allows_blog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `administrator_updates_course`
--

DROP TABLE IF EXISTS `administrator_updates_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `administrator_updates_course` (
  `admin_id` char(7) NOT NULL COMMENT '管理员id',
  `course_id` int NOT NULL COMMENT '课程id',
  `time` varchar(20) NOT NULL COMMENT '课程信息更新时间',
  PRIMARY KEY (`admin_id`,`course_id`),
  KEY `administrator_updates_course_fk2` (`course_id`),
  CONSTRAINT `administrator_updates_course_fk1` FOREIGN KEY (`admin_id`) REFERENCES `administrator` (`admin_id`),
  CONSTRAINT `administrator_updates_course_fk2` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `administrator_updates_course`
--

LOCK TABLES `administrator_updates_course` WRITE;
/*!40000 ALTER TABLE `administrator_updates_course` DISABLE KEYS */;
/*!40000 ALTER TABLE `administrator_updates_course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `blog`
--

DROP TABLE IF EXISTS `blog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog` (
  `blog_id` varchar(25) NOT NULL COMMENT '博客id',
  `writer_id` bigint NOT NULL COMMENT '博客撰写者id',
  `title` varchar(75) NOT NULL COMMENT '博客标题',
  `content_path` varchar(260) NOT NULL COMMENT '博客内容文件路径',
  `publish_time` varchar(20) NOT NULL COMMENT '博客发布时间',
  `clicks` int NOT NULL COMMENT '博客点击数',
  `is_public` tinyint(1) NOT NULL COMMENT '是否公开博客',
  PRIMARY KEY (`blog_id`),
  KEY `blog_fk` (`writer_id`),
  CONSTRAINT `blog_fk` FOREIGN KEY (`writer_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `blog`
--

LOCK TABLES `blog` WRITE;
/*!40000 ALTER TABLE `blog` DISABLE KEYS */;
/*!40000 ALTER TABLE `blog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `blog_direct_comment`
--

DROP TABLE IF EXISTS `blog_direct_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_direct_comment` (
  `comment_id` varchar(35) NOT NULL COMMENT '评论id',
  `blog_id` varchar(25) NOT NULL COMMENT '博客id',
  PRIMARY KEY (`comment_id`,`blog_id`),
  KEY `blog_direct_comment_fk2` (`blog_id`),
  CONSTRAINT `blog_direct_comment_fk1` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`comment_id`),
  CONSTRAINT `blog_direct_comment_fk2` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `blog_direct_comment`
--

LOCK TABLES `blog_direct_comment` WRITE;
/*!40000 ALTER TABLE `blog_direct_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `blog_direct_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comment`
--

DROP TABLE IF EXISTS `comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `comment_id` varchar(35) NOT NULL COMMENT '评论id',
  `user_id` bigint NOT NULL,
  `content_path` varchar(260) NOT NULL COMMENT '评论内容存储路径',
  `time` varchar(20) NOT NULL COMMENT '评论时间',
  `like` int NOT NULL COMMENT '点赞数',
  PRIMARY KEY (`comment_id`),
  KEY `comment_fk` (`user_id`),
  CONSTRAINT `comment_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `comment`
--

LOCK TABLES `comment` WRITE;
/*!40000 ALTER TABLE `comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course`
--

DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course` (
  `course_id` int NOT NULL AUTO_INCREMENT COMMENT '课程id',
  `language_name` varchar(15) NOT NULL COMMENT '编程语言名称',
  `course_name` varchar(50) NOT NULL COMMENT '课程名称',
  `description_path` varchar(260) NOT NULL COMMENT '课程描述文件路径',
  `course_website` varchar(200) DEFAULT NULL COMMENT '课程网站地址',
  `course_book` varchar(200) DEFAULT NULL COMMENT '课程资料地址',
  `course_vedio` varchar(2000) NOT NULL COMMENT '课程视频地址',
  `level` tinyint NOT NULL COMMENT '课程难度评级',
  `is_published` tinyint(1) NOT NULL,
  PRIMARY KEY (`course_id`),
  UNIQUE KEY `course_pk` (`course_name`),
  KEY `course_fk` (`language_name`),
  CONSTRAINT `course_fk` FOREIGN KEY (`language_name`) REFERENCES `programming_language` (`language_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course`
--

LOCK TABLES `course` WRITE;
/*!40000 ALTER TABLE `course` DISABLE KEYS */;
/*!40000 ALTER TABLE `course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_direct_comment`
--

DROP TABLE IF EXISTS `course_direct_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_direct_comment` (
  `comment_id` varchar(35) NOT NULL COMMENT '评论id',
  `course_id` int NOT NULL COMMENT '课程id',
  PRIMARY KEY (`comment_id`,`course_id`),
  KEY `course_direct_comment_fk2` (`course_id`),
  CONSTRAINT `course_direct_comment_fk1` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`comment_id`),
  CONSTRAINT `course_direct_comment_fk2` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_direct_comment`
--

LOCK TABLES `course_direct_comment` WRITE;
/*!40000 ALTER TABLE `course_direct_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_direct_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_module`
--

DROP TABLE IF EXISTS `course_module`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_module` (
  `language_id` int NOT NULL COMMENT '编程语言id',
  `priority` int NOT NULL COMMENT '模块排序优先级',
  `module_name` varchar(30) NOT NULL COMMENT '模块名称',
  PRIMARY KEY (`language_id`,`priority`),
  CONSTRAINT `course_module_fk` FOREIGN KEY (`language_id`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_module`
--

LOCK TABLES `course_module` WRITE;
/*!40000 ALTER TABLE `course_module` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_module` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `indirect_comment`
--

DROP TABLE IF EXISTS `indirect_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `indirect_comment` (
  `comment_id` varchar(35) NOT NULL COMMENT '评论id',
  `father_id` varchar(35) NOT NULL COMMENT '父评论id',
  `layer` tinyint NOT NULL COMMENT '评论层数',
  PRIMARY KEY (`comment_id`,`father_id`),
  CONSTRAINT `indirect_comment_fk` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`comment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `indirect_comment`
--

LOCK TABLES `indirect_comment` WRITE;
/*!40000 ALTER TABLE `indirect_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `indirect_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `language_documentation`
--

DROP TABLE IF EXISTS `language_documentation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `language_documentation` (
  `language_id` int NOT NULL COMMENT '编程语言id',
  `doc_link` varchar(200) NOT NULL COMMENT '文档链接地址',
  `description` varchar(150) DEFAULT NULL COMMENT '文档文字描述',
  PRIMARY KEY (`language_id`,`doc_link`),
  CONSTRAINT `language_documentation_fk` FOREIGN KEY (`language_id`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `language_documentation`
--

LOCK TABLES `language_documentation` WRITE;
/*!40000 ALTER TABLE `language_documentation` DISABLE KEYS */;
/*!40000 ALTER TABLE `language_documentation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `module_course`
--

DROP TABLE IF EXISTS `module_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `module_course` (
  `language_id` int NOT NULL COMMENT '编程语言id',
  `priority` int NOT NULL COMMENT '模块排序优先级',
  `course_name` varchar(50) NOT NULL COMMENT '课程名称',
  `module_name` varchar(30) NOT NULL COMMENT '模块名称',
  PRIMARY KEY (`language_id`,`priority`,`course_name`),
  KEY `module_course_fk2` (`course_name`),
  CONSTRAINT `module_course_fk1` FOREIGN KEY (`language_id`, `priority`) REFERENCES `course_module` (`language_id`, `priority`),
  CONSTRAINT `module_course_fk2` FOREIGN KEY (`course_name`) REFERENCES `course` (`course_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `module_course`
--

LOCK TABLES `module_course` WRITE;
/*!40000 ALTER TABLE `module_course` DISABLE KEYS */;
/*!40000 ALTER TABLE `module_course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `programming_language`
--

DROP TABLE IF EXISTS `programming_language`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `programming_language` (
  `language_id` int NOT NULL AUTO_INCREMENT COMMENT '编程语言id',
  `language_name` varchar(15) NOT NULL COMMENT '编程语言名称',
  `icon_path` varchar(260) NOT NULL COMMENT '编程语言图标路径',
  PRIMARY KEY (`language_id`),
  UNIQUE KEY `programming_language_pk` (`language_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `programming_language`
--

LOCK TABLES `programming_language` WRITE;
/*!40000 ALTER TABLE `programming_language` DISABLE KEYS */;
/*!40000 ALTER TABLE `programming_language` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户id',
  `user_name` varchar(30) NOT NULL COMMENT '用户姓名',
  `email` varchar(320) NOT NULL COMMENT '用户邮箱',
  `password` varchar(16) NOT NULL COMMENT '用户密码',
  `major` char(1) DEFAULT NULL COMMENT '用户专业',
  `head_sculptrue` varchar(260) DEFAULT NULL COMMENT '用户头像存储路径',
  `state` char(1) NOT NULL COMMENT '用户账号的状态',
  `create_time` varchar(20) NOT NULL,
  `favourite_language` int DEFAULT NULL COMMENT '用户最喜欢的语言',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_unique` (`user_name`,`email`),
  KEY `user_fk` (`favourite_language`),
  CONSTRAINT `user_fk` FOREIGN KEY (`favourite_language`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_collects_blog`
--

DROP TABLE IF EXISTS `user_collects_blog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_collects_blog` (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `blog_id` varchar(25) NOT NULL COMMENT '博客id',
  `time` varchar(20) NOT NULL COMMENT '收藏博客时间',
  PRIMARY KEY (`user_id`,`blog_id`),
  KEY `user_collects_blog_fk2` (`blog_id`),
  CONSTRAINT `user_collects_blog_fk1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `user_collects_blog_fk2` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_collects_blog`
--

LOCK TABLES `user_collects_blog` WRITE;
/*!40000 ALTER TABLE `user_collects_blog` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_collects_blog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_favors_course`
--

DROP TABLE IF EXISTS `user_favors_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_favors_course` (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `course_id` int NOT NULL COMMENT '课程id',
  `time` varchar(20) NOT NULL COMMENT '收藏课程时间',
  PRIMARY KEY (`user_id`,`course_id`),
  KEY `user_favors_course_fk2` (`course_id`),
  CONSTRAINT `user_favors_course_fk1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `user_favors_course_fk2` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_favors_course`
--

LOCK TABLES `user_favors_course` WRITE;
/*!40000 ALTER TABLE `user_favors_course` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_favors_course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_submits_blog`
--

DROP TABLE IF EXISTS `user_submits_blog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_submits_blog` (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `blog_id` varchar(25) NOT NULL COMMENT '博客id',
  `submit_time` varchar(20) NOT NULL COMMENT '博客提交审核时间',
  PRIMARY KEY (`user_id`,`blog_id`),
  KEY `user_submits_blog_fk2` (`blog_id`),
  CONSTRAINT `user_submits_blog_fk1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `user_submits_blog_fk2` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`blog_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_submits_blog`
--

LOCK TABLES `user_submits_blog` WRITE;
/*!40000 ALTER TABLE `user_submits_blog` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_submits_blog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_subscribes_language`
--

DROP TABLE IF EXISTS `user_subscribes_language`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_subscribes_language` (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `language_id` int NOT NULL COMMENT '编程语言id',
  `subscribe_time` varchar(20) NOT NULL COMMENT '订阅时间',
  PRIMARY KEY (`user_id`,`language_id`),
  KEY `user_subscribes_language_fk2` (`language_id`),
  CONSTRAINT `user_subscribes_language_fk1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `user_subscribes_language_fk2` FOREIGN KEY (`language_id`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_subscribes_language`
--

LOCK TABLES `user_subscribes_language` WRITE;
/*!40000 ALTER TABLE `user_subscribes_language` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_subscribes_language` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2022-11-30 23:40:35
