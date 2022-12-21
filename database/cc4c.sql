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
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
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
  `blog_id` bigint NOT NULL COMMENT '博客id',
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
  `time` datetime NOT NULL COMMENT '课程信息更新时间',
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
  `blog_id` bigint NOT NULL COMMENT '博客id',
  `writer_id` bigint NOT NULL COMMENT '博客撰写者id',
  `title` varchar(75) NOT NULL COMMENT '博客标题',
  `content` longtext NOT NULL COMMENT '博客内容',
  `publish_time` datetime DEFAULT NULL COMMENT '博客发布时间',
  `click` int NOT NULL DEFAULT '0' COMMENT '博客点击数',
  `state` int NOT NULL DEFAULT '0' COMMENT '博客的公开状态',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
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
INSERT INTO `blog` VALUES (1,1602639796138680322,'1','1','2022-12-17 16:37:06',0,1,0),(1605504520115195906,1602639796138680322,'my blog','this',NULL,0,1,0);
/*!40000 ALTER TABLE `blog` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `blog_direct_comment`
--

DROP TABLE IF EXISTS `blog_direct_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_direct_comment` (
  `comment_id` bigint NOT NULL COMMENT '评论id',
  `blog_id` bigint NOT NULL COMMENT '博客id',
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
-- Table structure for table `blog_draft`
--

DROP TABLE IF EXISTS `blog_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_draft` (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `content` longtext COMMENT '草稿内容',
  PRIMARY KEY (`user_id`),
  CONSTRAINT `blog_draft_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='用于存储用户的博客草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `blog_draft`
--

LOCK TABLES `blog_draft` WRITE;
/*!40000 ALTER TABLE `blog_draft` DISABLE KEYS */;
/*!40000 ALTER TABLE `blog_draft` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `blog_involves_language`
--

DROP TABLE IF EXISTS `blog_involves_language`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `blog_involves_language` (
  `blog_id` bigint NOT NULL COMMENT '博客id',
  `language_id` int NOT NULL COMMENT '编程语言id',
  PRIMARY KEY (`blog_id`,`language_id`),
  KEY `blog_involves_language_fk2` (`language_id`),
  CONSTRAINT `blog_involves_language_fk1` FOREIGN KEY (`blog_id`) REFERENCES `blog` (`blog_id`),
  CONSTRAINT `blog_involves_language_fk2` FOREIGN KEY (`language_id`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='博客内容与那些编程语言相关';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `blog_involves_language`
--

LOCK TABLES `blog_involves_language` WRITE;
/*!40000 ALTER TABLE `blog_involves_language` DISABLE KEYS */;
INSERT INTO `blog_involves_language` VALUES (1605504520115195906,1);
/*!40000 ALTER TABLE `blog_involves_language` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comment`
--

DROP TABLE IF EXISTS `comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comment` (
  `comment_id` bigint NOT NULL COMMENT '评论id',
  `user_id` bigint NOT NULL,
  `content` text NOT NULL COMMENT '评论内容',
  `time` datetime NOT NULL COMMENT '评论时间',
  `like` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
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
INSERT INTO `comment` VALUES (1605182021087330306,1602639796138680322,'我是对评论的评论','2022-12-20 20:43:11',0,0);
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
  `description` text NOT NULL COMMENT '课程描述',
  `level` int NOT NULL DEFAULT '0' COMMENT '课程难度评级',
  `state` int NOT NULL DEFAULT '1' COMMENT '课程的发布状态',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
  PRIMARY KEY (`course_id`),
  UNIQUE KEY `course_pk` (`course_name`),
  KEY `course_fk` (`language_name`),
  CONSTRAINT `course_fk` FOREIGN KEY (`language_name`) REFERENCES `programming_language` (`language_name`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course`
--

LOCK TABLES `course` WRITE;
/*!40000 ALTER TABLE `course` DISABLE KEYS */;
INSERT INTO `course` VALUES (1,'java','黑马_20天学会JAVA','# Java入门基础视频教程，java零基础自学就选黑马程序员Java入门教程（含Java项目和Java真题）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n为了帮助广大对Java有兴趣和立志进入本行业的零基础学员，本套课程由此而生，本套课程是Java零基础学员极好的入门视频，课程从Java语言的简介到程序开发执行的原理、集成开发工具IDEA的使用，再到Java技术的应用都一应俱全，本套课程学完后足以应对年限30万+程序员的Java基础面试部分。\n本套课程在基本知识讲完后，就会以软件公司一个个真实的应用需求来加强大家对知识的理解和掌握，在解决需求的同时又以一个一个问题的方式去驱动我们的学习，本套课程知识全面透彻，案例极为丰富，阶段课程完结后都配备了综合实战案例，具备大量优雅、高质量的代码供初学者训练，本课程另一个核心特点就是手把手带着大家边干边学，边学边干，清楚，实用。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - itheima\n\n    http://yun.itheima.com/course/936.html?capid=1\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Cv411372m\n\n  - youtube\n\n    https://www.youtube.com/watch?v=VqfGCmjQt10&list=PLFbd8KZNbe-_Qe0FQXinSoSBDDPUofQ34\n\n',-2,1,0),(2,'java','黑马_Java零基础入门到精通','# 黑马程序员全套Java教程_Java基础入门视频教程，零基础小白自学Java入门教程\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n通过本视频的学习，让您从零开始，掌握 Java 开发的各种技术，再结合后续知识，从而达到企业对 Java 开发工程师的要求！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV18J411W7cE/?vd_source=a418185fadee73ac65d8fab69eee0b52\n\n',0,1,0),(3,'java','黑马_Java从入门到起飞','# 黑马程序员Java零基础视频教程(2022新版Java入门，含斯坦福大学练习题+力扣算法题+大厂java面试题）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nJava基础的天花板教程，面向0基础同学，有手就行。从0开始，到进阶，最后起飞，层层递进。课程中会讲解很多编程思想，以及我是如何从0开始去分析一个问题，并把代码写出来的。拒绝一听就懂，一学就废。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    上部：https://www.bilibili.com/video/BV17F411T7Ao\n    \n    下部：https://www.bilibili.com/video/BV1yW4y1Y7Ms\n',2,1,0),(4,'java','慕课_Java程序设计_北京大学','# 北京大学Java程序设计\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程是在已有的基础上让学习者能够以Java语言编写具有一定规模、综合性的应用程序。对后面的操作系统、编译原理、数据库等课程来说，该课程是一个承上启下的课程。\n\n从课时内容而言，主要有三部分：\n\n第一部分是Java语言部分，包括Java概述，简单的Java程序，变量、语句、数组，类、包、接口，深入理解Java语言，异常处理等。这部分内容的目的是掌握Java语言的语法，能够较为深入理解Java语言机制，掌握Java语言面向对象的特点。 \n\n 第二部分是Java的类库及应用，包括工具类及常用算法、多线程、流、文件及基于文本的应用、图形用户界面、网络、多媒体和数据库编程等，这部分的目标是掌握JavaSE中基本的API，掌握在集合、线程、输入输出、图形用户界面、网络等方面的应用。\n\n 第三部分是关于如何写出出高质量的代码，包括集成开发环境的使用，单元测试、日志、质量管理工具的使用，掌握重构和设计模式，这部分的目标是综合应用本课程的知识，能够编写有一定规模的应用程序，养成良好的编程习惯，能够编写高代码的质量。\n\n 课程一方面重视语言的基础和原理，另一方面注意实际编程能力的培养。\n\n 课程中除了视频、讨论区外，每周都有一些测验、还有一些小的作业，在课程结束还要求开发有一定工作量、有中等难度的项目。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/PKU-1001941004\n',-1,1,0),(5,'java','慕课_Java程序设计_常州信息职业技术学院','## 浙江大学零基础学Java语言\n\n\n\n### 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n### 课程描述\n\n程序设计是一门基础课程。对于计算机相关专业而言，程序设计是专业基础知识，是进一步学习其他专业知识的第一步阶梯；对于非计算机专业而言，程序设计的学习有助于理解计算机的能力所在，理解哪些是计算机擅长解决的问题，怎样的方式方法是计算机擅长的手段，从而能更好地利用计算机来解决本专业领域内的问题。\n\nJava是近10年来最流行的编程语言，在各类编程语言排行榜上长年占据前两名的位置。本课程是以Java语言来讲授程序设计的入门知识的。\n\n程序设计是实践性很强的课程，该课程的学习有其自身的特点，听不会，也看不会，只能练会。你必须通过大量的编程训练，在实践中掌握编程知识，培养编程能力，并逐步理解和掌握程序设计的思想和方法。在这里所提供的，只是基础的知识讲解，要想学会编程，还需要更多时间的投入和努力。\n\n\n\n### 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/ZJU-1001541001\n',-1,1,0),(6,'java','慕课_基于Java的面向对象编程范式_南京大学','# 南京大学基于Java的面向对象编程范式\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n这，\n\n不是21天速成Java课程；\n\n不是Java语法细节讲解课程；\n\n是一门讲编程基础思想的课程；\n\n是一门讲面向对象思想的课程；\n\n是一门讲编程最佳实践的课程；\n\n需要动手写代码的课程；\n\n第一周增加了一些基础Java训练，测试大家Java基本水平；\n\n之后每周一道；\n\n会带着大家一起做，一起讲解；\n\n希望大家学完之后能够有恍然大悟的感觉。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/NJU-1002246017\n',-1,1,0),(7,'java','慕课_零基础学Java语言_浙江大学','# 浙江大学零基础学Java语言\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n程序设计是一门基础课程。对于计算机相关专业而言，程序设计是专业基础知识，是进一步学习其他专业知识的第一步阶梯；对于非计算机专业而言，程序设计的学习有助于理解计算机的能力所在，理解哪些是计算机擅长解决的问题，怎样的方式方法是计算机擅长的手段，从而能更好地利用计算机来解决本专业领域内的问题。\n\nJava是近10年来最流行的编程语言，在各类编程语言排行榜上长年占据前两名的位置。本课程是以Java语言来讲授程序设计的入门知识的。\n\n程序设计是实践性很强的课程，该课程的学习有其自身的特点，听不会，也看不会，只能练会。你必须通过大量的编程训练，在实践中掌握编程知识，培养编程能力，并逐步理解和掌握程序设计的思想和方法。在这里所提供的，只是基础的知识讲解，要想学会编程，还需要更多时间的投入和努力。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/ZJU-1001541001\n',-1,1,0),(9,'java','尚硅谷_IDEA','# 【尚硅谷】IDEA2022全新版教程，兼容JDK17（快速上手Java开发利器）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本套教程采用IDEA最新的2022.x版本（兼容JDK17），从IDEA的安装和设置入手，详解IDEA中工程与模块的结构、代码模板的设置与使用、断点调试、快捷键使用、Maven工程与Web工程的搭建、数据库的关联、常用热点插件使用等共计12大专题内容。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1CK411d7aA\n',66,1,0),(10,'java','尚学堂_Java300集','# 尚学堂给同学们带来全新的Java300集课程啦!java零基础小白自学Java必备优质教程_手把手图解学习Java，让学习成为一种享受\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n尚学堂给同学们带来全新的Java300集课程啦 \n\n本课程为Java300集2022版第一季，配合最新版的Java课程，所有视频重新录制，课件所有图形做了重新绘制和配色，图解学习Java，让学习成为一种享受 本套教程专门为零基础学员而制，适合准备入行Java开发的零基础学员，视频中穿插多个实战项目。每一个知识点都讲解的通俗易懂，由浅入深。不仅适用于零基础的初学者，有经验的程序员也可做巩固学习。 \n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1qL411u7eE\n',0,1,0),(11,'java','尚硅谷_Java基础极速版','# 【尚硅谷】7天搞定Java基础，Java零基础极速入门\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本套教程基于Java17和IDEA2022，专为Java初学者量身定制。通过本套教程的学习，你将掌握Java语言的整体结构和学习体系、Java基础语法及简单应用，为后续Web开发、Spring框架的学习打下坚实的基础。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1o841187iP\n    \n    \n',-2,1,0),(12,'java','尚硅谷_Java基础细致版','# 尚硅谷Java入门视频教程(在线答疑+Java面试真题)\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nMySQL百科全书（从小白到大牛）：[BV1iq4y1u7vj](https://www.bilibili.com/video/BV1iq4y1u7vj/?spm_id_from=333.788.video.desc.click)（宋红康主讲） \n\n宋红康亲授：播放量1000万+，好评如潮\n\n Java入门神器：2万多行代码+5套实战项目+近百道企业面试真题\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Kb411W75N\n',1,1,0),(14,'java','尚学堂_Java全套','# JAVA全套课程尚学堂Java入门Java零基础必备Java编程课程Java核心基础EasyUISSM整合框架Redis高并发—全套课程\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nJava完整全套课程，一套视频全套包含！ javaSE基础入门，javaEE框架，SSM框架整合、Redis等全套课程，从入门到精通给你想要的！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV137411V7Y1\n',1,1,0),(15,'java','尚硅谷_JUC','# 尚硅谷2022版JUC并发编程（对标阿里P6-P7）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n从理论到实战，知识点涵盖全面，庖丁解牛式讲解！针对1-5年的Java程序员，精心设计的课程体系，详解原理，案例驱动，即给方法又给方案，生产环境模拟教学，大厂面试真题拆解，应有尽有！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1ar4y1x727\n',2,1,0),(16,'java','尚硅谷_JVM','# 尚硅谷宋红康JVM全套教程（详解java虚拟机）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n全套课程分为《内存与垃圾回收篇》《字节码与类的加载篇》《性能监控与调优篇》三个篇章，由尚硅谷宋红康老师亲自主刀，亲手绘制的图示，仅上篇就有50张之多...内容之强悍，可见一斑！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1PJ411n7xZ\n',2,1,0),(17,'java','黑马_MySql','# 黑马程序员 MySQL数据库入门到精通，从mysql安装到mysql高级、mysql优化全囊括\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程是目前为止，MySQL方面最为全面的一套课程，视频知识涵盖了MySQL的基础、进阶、运维等多个方面，不仅讲解知识点的具体应用，还会讲解其底层结构和原理。知识讲解全面、深入，能够完全满足我们日常的开发、运维、面试、以及自我提升，而且在讲解过程中结合多种手段，帮助学生更加清晰的理解课程中的重点和难点内容。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Kr4y1i7ru\n',2,1,0),(18,'java','尚硅谷_JDBC','# 尚硅谷JDBC核心技术视频教程（康师傅带你一站式搞定jdbc）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本套教程涵盖JDBC的方方面面，包括手动获取数据库连接的多种方式、使用数据库连接池获取连接、Statement与PreparedStatement的对比使用、sql注入问题讲解、Blob字段的操作、高效的批量插入、DAO设计模式、使用dbutils提供的相关工具类等。此外还对数据库事务进行详解，利用反射及JDBC元数据编写通用的查询方法等企业级开发内容。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1eJ411c7rf\n',66,1,0),(19,'java','尚硅谷_MySQL','# MySQL数据库教程天花板，mysql安装到mysql高级，强！硬！\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nMySQL课程天花板：6大范式讲解、7大日志剖析、7大SQL性能分析工具、9大存储引擎剖析、10大类30小类优化场景、15个不同锁的应用讲解、18种创建索引的规则、300+张高清无码技术剖析图......\n\n基础篇：P1 - P95 高级篇：P96 - P199\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1iq4y1u7vj\n',-1,1,0),(20,'java','尚硅谷_MySQL优化','# 尚硅谷MySQL数据库高级，mysql优化，数据库优化\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本教程主要讲授针对 Java 开发所需的 MySQL 高级知识，课程中会让大家快速掌握索引，如何避免索引失效，索引的优化策略，了解innodb和myisam存储引擎，熟悉MySQL锁机制，能熟练配置MySQL主从复制，熟练掌握explain、show profile、慢查询日志等日常SQL诊断和性能分析策略\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1KW411u7vy\n',1,1,0),(21,'java','黑马_JavaWeb','# 黑马程序员新版JavaWeb基础教程，Java web从入门到企业实战完整版\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nJavaWeb是整个Web开发的基础课程，需要掌握三部分内容：数据库、前端、web核心。本套JavaWeb教程旨在用最短的时间掌握最全的JavaWeb核心技术，使学习效率猛增2倍，并且可以为后期的分布式、微服务打下坚实的基础。 该套视频全是干货，不墨迹，没废话，让你花最短时间学会，包括javaweb+mysql+maven+html+css+ajax+vue+项目实战等内容，是目前站内最全的JavaWeb技术栈课程。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Qf4y1T7Hx\n\n',1,1,0),(22,'java','尚硅谷_JavaWeb','# 尚硅谷最新版JavaWeb全套教程,java web零基础入门完整版\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本视频涵盖核心技术点有：Servlet程序、Filter过滤器、Listener监听器、jsp页面、EL表达式、JSTL标签库、jQuery框架、Cookie技术、Session会话、JSON使用、Ajax请求，并在讲解知识点过程中会带领大家完成一个书城项目。相对于旧版，本版本使用idea进行开发，同时对多项技术做了升级！ 课程目标：听懂、理解、会用。并为后期框架、框架的学习打下坚实的基础\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Y7411K7zz\n',-1,1,0),(23,'java','黑马_Maven','# 黑马程序员Maven全套教程，maven项目管理从基础到高级，Java项目开发必会管理工具maven\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程主要讲解Maven的使用，从基础到高级，让学生深入了解Maven项目的构建及管理方式，Java项目开发所需的管理工具Maven。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Ah411S7ZE\n\n',1,1,0),(24,'java','黑马_MybatisPlus','# 黑马程序员MybatisPlus深入浅出教程，快速上手mybatisplus\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nMyBatis-Plus（简称 MP）是一个 MyBatis 的增强工具，在 MyBatis 的基础上只做增强不做改变，为简化开发、提高效率而生。使用原生的Mybatis编写持久层逻辑时，所需要的代码是比较繁琐的，需要定义Mapper接口和Mapper.xml文件，每一个方法都需要编写对应的sql语句，会存在很多大量的重复工作，使用MP之后，对通用的方法做了高度的抽取，避免了很多重复工作，可以非常快速的实现了单表的各种增、删、改、查操作。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1rE41197jR\n',2,1,0),(25,'java','黑马_Spring','# 黑马程序员Spring视频教程，深度讲解spring5底层原理\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程以讲解 Spring 原理知识为主。但又不同于一般的原理课，基本不翻源码，而是通过各种单元测试和模拟实现，带领学员更为感性地认识 Spring 底层。 学完本课程能够收获：培养正确的学习源码方法；睥睨其它程序员的资本；唯一认清 Spring 的机会！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1P44y1N7QG\n\n',2,1,0),(26,'java','黑马_SpringBoot','# 黑马程序员SpringBoot2全套视频教程，springboot零基础到项目实战（spring boot2完整版）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpringBoot技术是目前市面上从事JavaEE企业级开发过程中使用量最大的技术。本视频围绕SpringBoot技术由浅入深带领学习者从小白身份入门SpringBoot。经过若干个案例的制作与学习，全面掌握在企业级开发过程中如何使用SpringBoot技术将市面上各个层面各个领域的实用技术整合在一起工作，并应用于企业级开发各个层面的实际问题。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV15b4y1a7yG\n\n',2,1,0),(27,'java','黑马_SpringCloud','# SpringCloud+RabbitMQ+Docker+Redis+搜索+分布式，系统详解springcloud微服务技术栈课程|黑马程序员Java微服务\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpringCloud微服务技术栈课程火热登场！ SpringCloudAlibaba、RabbitMQ、Docker、Redis、Elasticsearch等众多行业大厂必备技术一网打尽。 实用篇、高级篇、面试篇分层次教学，由易到难，层层推进，高潮不断！ 该套教程技术体系完整，即使在职或者曾学过的话也强烈建议你再刷一遍这套教程！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1LQ4y127n4\n\n\n',2,1,0),(28,'java','黑马_SSM','# 黑马程序员2022新版SSM框架教程_Spring+SpringMVC+Maven高级+SpringBoot+MyBatisPlus企业实用开发技术\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSSM框架课程是Java从业人员从基础学习阶段进阶到初级程序员的入门课程，也是走向成功的必经之路。 SSM框架课程中共包含5个课程模块，分别是Spring框架、SpringMVC框架、Maven高级、SpringBoot框架、MyBatis-Plus框架。通过本阶段课程的学习，学习者可以掌握大量实用开发技术，企业开发规范，最终实现基于SpringBoot技术实现SSM整合。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Fi4y1S7ix\n',2,1,0),(29,'java','尚硅谷_Maven','# 尚硅谷2022版Maven教程（maven入门+高深，全网无出其右！）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n基础篇（1-53集），适合小白，快速上手； 高级篇（54-121集），适合开发人员，了解Maven项目开发全流程； 资深篇（122-173集），适合老鸟学习，搞定你开发中的诸多痛点。\n\n本课程将会让你从不会使用 Maven 的小白晋升熟练使用 Maven 管理依赖和构建过程的大牛，并理解 Maven 中各种内部机制的来龙去脉，在 Maven 的整个生态中游刃有余。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV12q4y147e4\n',-1,1,0),(30,'java','尚硅谷_Mybatis','# 【尚硅谷】2022版MyBatis零基础入门教程（细致全面，快速上手）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n课程包括MyBatis框架搭建，MyBatis配置文件以及映射文件的讲解以及编写，MyBatis获取参数值的方式，MyBatis中的各种查询功能，MyBatis的自定义映射，MyBatis动态SQL，MyBatis的缓存机制，MyBatis逆向工程...\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1VP4y1c7j7\n',-1,1,0),(31,'java','尚硅谷_Spring','# 【尚硅谷】Spring框架视频教程（spring超详细源码级讲解）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpring框架可以单独构建应用程序，也可以和其他框架组合使用。Spring框架凭借其强大的功能以及优良的性能，在企业开发中被广泛应用。 本套教程从Spring框架最基础的部分讲起，内容由浅入深，适合有一定Java开发基础的相关人员，也适合具备一定软件开发能力的人员。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Vf4y127N5\n',0,1,0),(32,'java','尚硅谷_SpringBoot','# 【尚硅谷】SpringBoot2零基础入门教程（spring boot2干货满满）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpringBoot2升级之后，带来了非常多的新特性，以及底层源码设计的差异。本套视频教程基于SpringBoot2.3与2.4版本讲解，适用于有Spring、SpringMVC基础，初学或想深入了解SpringBoot的学习者。 教程包含核心基础、Web原理、单元测试、数据访问、指标监控等章节。 通过以上内容的学习，会将你的SpringBoot水平带到一个更高的层次，面向应用开发游刃有余！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV19K4y1L7MT\n',2,1,0),(33,'java','尚硅谷_SpringCloud','# 尚硅谷SpringCloud框架开发教程(SpringCloudAlibaba微服务分布式架构丨Spring Cloud)\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n尚硅谷SpringCloud 1 视频，一经推出，广受好评。本视频含SpringCloud Hoxton和SpringCloud alibaba，双剑合并，威力大增！内容涵盖目前火热的分布式微服务架构的全部技术栈，是尚硅谷高阶班微服务课程的全新升级版。新版教程对老版的五大技术做了升级加强和替换更新，对原有技术进行了更加深入的讲解，此外，引入了后起新秀SpringCloud alibaba，满足你对新技术的探索欲望！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV18E411x7eT\n',2,1,0),(34,'java','尚硅谷_SSM','# 【尚硅谷】SSM框架全套教程，MyBatis+Spring+SpringMVC+SSM整合一套通关\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n尚硅谷研究院结合多年教学经验，充分教研精心设计了这套教程，只此一套教程即可SSM从原理到应用、再到核心源码，全部轻松拿捏！教程讲解直给干货，不说废话，简洁精炼，堪称SSM入门首选，可应对SSM学习中所有问题。 教程讲解循序渐进，先从MyBatis讲起，再依次介绍Spring、SpringMVC，最后完成整合。整套教程理论与应用完美结合，难点和重点针对性拆解，让你在掌握技术原理的同时，具备实战操作能力！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Ya411S7aT\n',1,1,0);
/*!40000 ALTER TABLE `course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_direct_comment`
--

DROP TABLE IF EXISTS `course_direct_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_direct_comment` (
  `comment_id` bigint NOT NULL COMMENT '评论id',
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
  `module_name` varchar(50) NOT NULL COMMENT '模块名称',
  `level` int NOT NULL DEFAULT '0' COMMENT '可选字段，标注该模块的难度等级',
  PRIMARY KEY (`language_id`,`priority`),
  CONSTRAINT `course_module_fk` FOREIGN KEY (`language_id`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_module`
--

LOCK TABLES `course_module` WRITE;
/*!40000 ALTER TABLE `course_module` DISABLE KEYS */;
INSERT INTO `course_module` VALUES (1,1,'java基础',0),(1,2,'java高级',1),(1,3,'java数据库开发',0),(1,4,'java web',0),(1,5,'java开发框架',0);
/*!40000 ALTER TABLE `course_module` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `indirect_comment`
--

DROP TABLE IF EXISTS `indirect_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `indirect_comment` (
  `comment_id` bigint NOT NULL COMMENT '评论id',
  `father_id` bigint NOT NULL COMMENT '父评论id',
  `layer` int NOT NULL DEFAULT '0' COMMENT '评论层数',
  PRIMARY KEY (`comment_id`,`father_id`),
  CONSTRAINT `indirect_comment_fk1` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`comment_id`),
  CONSTRAINT `check_max_layer` CHECK ((`layer` <= 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `indirect_comment`
--

LOCK TABLES `indirect_comment` WRITE;
/*!40000 ALTER TABLE `indirect_comment` DISABLE KEYS */;
INSERT INTO `indirect_comment` VALUES (1605182021087330306,1604827993379729409,0);
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
  `description` text COMMENT '文档文字描述',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
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
  `priority` int NOT NULL COMMENT '模块优先级',
  `course_id` int NOT NULL COMMENT '课程id',
  PRIMARY KEY (`language_id`,`priority`,`course_id`),
  KEY `module_course_fk2` (`course_id`),
  CONSTRAINT `module_course_fk1` FOREIGN KEY (`language_id`, `priority`) REFERENCES `course_module` (`language_id`, `priority`),
  CONSTRAINT `module_course_fk2` FOREIGN KEY (`course_id`) REFERENCES `course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `module_course`
--

LOCK TABLES `module_course` WRITE;
/*!40000 ALTER TABLE `module_course` DISABLE KEYS */;
INSERT INTO `module_course` VALUES (1,1,1),(1,1,2),(1,1,3),(1,1,4),(1,1,5),(1,1,6),(1,1,7),(1,1,9),(1,1,10),(1,1,11),(1,1,12),(1,1,14),(1,2,15),(1,2,16),(1,3,17),(1,3,18),(1,3,19),(1,3,20),(1,4,21),(1,4,22),(1,5,23),(1,5,24),(1,5,25),(1,5,26),(1,5,27),(1,5,28),(1,5,29),(1,5,30),(1,5,31),(1,5,32),(1,5,33),(1,5,34);
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
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
  PRIMARY KEY (`language_id`),
  UNIQUE KEY `programming_language_pk` (`language_name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `programming_language`
--

LOCK TABLES `programming_language` WRITE;
/*!40000 ALTER TABLE `programming_language` DISABLE KEYS */;
INSERT INTO `programming_language` VALUES (1,'java','G:\\cc4c\\programmingLanguage\\icon\\java.png',0),(2,'c++','G:\\cc4c\\programmingLanguage\\icon\\c++.png',0),(3,'python','G:\\cc4c\\programmingLanguage\\icon\\python.png',0),(4,'c','G:\\cc4c\\programmingLanguage\\icon\\c.png',0),(5,'csharp','G:\\cc4c\\programmingLanguage\\icon\\csharp.png',0);
/*!40000 ALTER TABLE `programming_language` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` bigint NOT NULL COMMENT '用户id',
  `user_name` varchar(30) NOT NULL COMMENT '用户姓名',
  `email` varchar(320) NOT NULL COMMENT '用户邮箱',
  `password` varchar(16) NOT NULL COMMENT '用户密码',
  `major` int DEFAULT NULL COMMENT '用户专业',
  `avatar` varchar(260) DEFAULT NULL COMMENT '用户头像存储路径',
  `state` int NOT NULL COMMENT '用户账号的状态',
  `create_time` datetime NOT NULL,
  `favourite_language` int DEFAULT NULL COMMENT '用户最喜欢的语言',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_unique` (`user_name`),
  UNIQUE KEY `email_unique` (`email`),
  KEY `user_fk` (`favourite_language`),
  CONSTRAINT `user_fk` FOREIGN KEY (`favourite_language`) REFERENCES `programming_language` (`language_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1602639796138680322,'111','1211qq.com','123456',1,'212',2,'2022-12-13 20:21:18',1,0),(1602640576853839874,'1111','12112qq.com','123456',1,'212',2,'2022-12-13 20:24:24',1,0),(1603284684677148674,'1411','12188qq.com','123456',1,'212',2,'2022-12-15 15:03:51',1,0),(1604021202404261890,'645','66612qq.com','123456',1,'212',2,'2022-12-17 15:50:30',1,0);
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
  `blog_id` bigint NOT NULL COMMENT '博客id',
  `time` datetime NOT NULL COMMENT '收藏博客时间',
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
  `time` datetime NOT NULL COMMENT '收藏课程时间',
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
INSERT INTO `user_favors_course` VALUES (1602639796138680322,1,'2022-12-21 16:27:26'),(1602639796138680322,2,'2022-12-21 16:27:42'),(1602639796138680322,3,'2022-12-21 16:27:59');
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
  `blog_id` bigint NOT NULL COMMENT '博客id',
  `submit_time` datetime NOT NULL COMMENT '博客提交审核时间',
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
INSERT INTO `user_submits_blog` VALUES (1602639796138680322,1605504520115195906,'2022-12-21 18:04:41');
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
  `subscribe_time` datetime NOT NULL COMMENT '订阅时间',
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

-- Dump completed on 2022-12-21 19:56:52
