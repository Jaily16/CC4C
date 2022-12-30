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
INSERT INTO `administrator` VALUES ('2050249','123456',0),('2050287','123456',0),('2051851','123456',0),('2051970','050598',0);
/*!40000 ALTER TABLE `administrator` ENABLE KEYS */;
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
  `course_name` varchar(200) NOT NULL COMMENT '课程名称',
  `description` text NOT NULL COMMENT '课程描述',
  `level` int NOT NULL DEFAULT '0' COMMENT '课程难度评级',
  `state` int NOT NULL DEFAULT '1' COMMENT '课程的发布状态',
  `deleted` int NOT NULL DEFAULT '0' COMMENT '逻辑删除字段',
  PRIMARY KEY (`course_id`),
  UNIQUE KEY `course_pk` (`course_name`),
  KEY `course_fk` (`language_name`),
  CONSTRAINT `course_fk` FOREIGN KEY (`language_name`) REFERENCES `programming_language` (`language_name`)
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb3;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course`
--

LOCK TABLES `course` WRITE;
/*!40000 ALTER TABLE `course` DISABLE KEYS */;
INSERT INTO `course` VALUES (1,'java','黑马_20天学会JAVA','# Java入门基础视频教程，java零基础自学就选黑马程序员Java入门教程（含Java项目和Java真题）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n为了帮助广大对Java有兴趣和立志进入本行业的零基础学员，本套课程由此而生，本套课程是Java零基础学员极好的入门视频，课程从Java语言的简介到程序开发执行的原理、集成开发工具IDEA的使用，再到Java技术的应用都一应俱全，本套课程学完后足以应对年限30万+程序员的Java基础面试部分。\n本套课程在基本知识讲完后，就会以软件公司一个个真实的应用需求来加强大家对知识的理解和掌握，在解决需求的同时又以一个一个问题的方式去驱动我们的学习，本套课程知识全面透彻，案例极为丰富，阶段课程完结后都配备了综合实战案例，具备大量优雅、高质量的代码供初学者训练，本课程另一个核心特点就是手把手带着大家边干边学，边学边干，清楚，实用。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - itheima\n\n    http://yun.itheima.com/course/936.html?capid=1\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Cv411372m\n\n  - youtube\n\n    https://www.youtube.com/watch?v=VqfGCmjQt10&list=PLFbd8KZNbe-_Qe0FQXinSoSBDDPUofQ34\n\n',-2,1,0),(2,'java','黑马_Java零基础入门到精通','# 黑马程序员全套Java教程_Java基础入门视频教程，零基础小白自学Java入门教程\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n通过本视频的学习，让您从零开始，掌握 Java 开发的各种技术，再结合后续知识，从而达到企业对 Java 开发工程师的要求！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV18J411W7cE/?vd_source=a418185fadee73ac65d8fab69eee0b52\n\n',0,1,0),(3,'java','黑马_Java从入门到起飞','# 黑马程序员Java零基础视频教程(2022新版Java入门，含斯坦福大学练习题+力扣算法题+大厂java面试题）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nJava基础的天花板教程，面向0基础同学，有手就行。从0开始，到进阶，最后起飞，层层递进。课程中会讲解很多编程思想，以及我是如何从0开始去分析一个问题，并把代码写出来的。拒绝一听就懂，一学就废。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    上部：https://www.bilibili.com/video/BV17F411T7Ao\n    \n    下部：https://www.bilibili.com/video/BV1yW4y1Y7Ms\n',2,1,0),(4,'java','慕课_Java程序设计_北京大学','# 北京大学Java程序设计\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程是在已有的基础上让学习者能够以Java语言编写具有一定规模、综合性的应用程序。对后面的操作系统、编译原理、数据库等课程来说，该课程是一个承上启下的课程。\n\n从课时内容而言，主要有三部分：\n\n第一部分是Java语言部分，包括Java概述，简单的Java程序，变量、语句、数组，类、包、接口，深入理解Java语言，异常处理等。这部分内容的目的是掌握Java语言的语法，能够较为深入理解Java语言机制，掌握Java语言面向对象的特点。 \n\n 第二部分是Java的类库及应用，包括工具类及常用算法、多线程、流、文件及基于文本的应用、图形用户界面、网络、多媒体和数据库编程等，这部分的目标是掌握JavaSE中基本的API，掌握在集合、线程、输入输出、图形用户界面、网络等方面的应用。\n\n 第三部分是关于如何写出出高质量的代码，包括集成开发环境的使用，单元测试、日志、质量管理工具的使用，掌握重构和设计模式，这部分的目标是综合应用本课程的知识，能够编写有一定规模的应用程序，养成良好的编程习惯，能够编写高代码的质量。\n\n 课程一方面重视语言的基础和原理，另一方面注意实际编程能力的培养。\n\n 课程中除了视频、讨论区外，每周都有一些测验、还有一些小的作业，在课程结束还要求开发有一定工作量、有中等难度的项目。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/PKU-1001941004\n',-1,1,0),(5,'java','慕课_Java程序设计_常州信息职业技术学院','## 浙江大学零基础学Java语言\n\n\n\n### 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n### 课程描述\n\n程序设计是一门基础课程。对于计算机相关专业而言，程序设计是专业基础知识，是进一步学习其他专业知识的第一步阶梯；对于非计算机专业而言，程序设计的学习有助于理解计算机的能力所在，理解哪些是计算机擅长解决的问题，怎样的方式方法是计算机擅长的手段，从而能更好地利用计算机来解决本专业领域内的问题。\n\nJava是近10年来最流行的编程语言，在各类编程语言排行榜上长年占据前两名的位置。本课程是以Java语言来讲授程序设计的入门知识的。\n\n程序设计是实践性很强的课程，该课程的学习有其自身的特点，听不会，也看不会，只能练会。你必须通过大量的编程训练，在实践中掌握编程知识，培养编程能力，并逐步理解和掌握程序设计的思想和方法。在这里所提供的，只是基础的知识讲解，要想学会编程，还需要更多时间的投入和努力。\n\n\n\n### 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/ZJU-1001541001\n',-1,1,0),(6,'java','慕课_基于Java的面向对象编程范式_南京大学','# 南京大学基于Java的面向对象编程范式\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n这，\n\n不是21天速成Java课程；\n\n不是Java语法细节讲解课程；\n\n是一门讲编程基础思想的课程；\n\n是一门讲面向对象思想的课程；\n\n是一门讲编程最佳实践的课程；\n\n需要动手写代码的课程；\n\n第一周增加了一些基础Java训练，测试大家Java基本水平；\n\n之后每周一道；\n\n会带着大家一起做，一起讲解；\n\n希望大家学完之后能够有恍然大悟的感觉。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/NJU-1002246017\n',-1,1,0),(7,'java','慕课_零基础学Java语言_浙江大学','# 浙江大学零基础学Java语言\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n程序设计是一门基础课程。对于计算机相关专业而言，程序设计是专业基础知识，是进一步学习其他专业知识的第一步阶梯；对于非计算机专业而言，程序设计的学习有助于理解计算机的能力所在，理解哪些是计算机擅长解决的问题，怎样的方式方法是计算机擅长的手段，从而能更好地利用计算机来解决本专业领域内的问题。\n\nJava是近10年来最流行的编程语言，在各类编程语言排行榜上长年占据前两名的位置。本课程是以Java语言来讲授程序设计的入门知识的。\n\n程序设计是实践性很强的课程，该课程的学习有其自身的特点，听不会，也看不会，只能练会。你必须通过大量的编程训练，在实践中掌握编程知识，培养编程能力，并逐步理解和掌握程序设计的思想和方法。在这里所提供的，只是基础的知识讲解，要想学会编程，还需要更多时间的投入和努力。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.icourse163.org/course/ZJU-1001541001\n',-1,1,0),(8,'c++','黑马_从0到1入门c++','# 黑马程序员匠心之作|C++教程从0到1入门编程,学习编程不再难\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本教程分为7个阶段，涵盖基础入门到实战项目\n\n第1阶段-C++基础入门\n\n第2阶段实战-通讯录管理系统\n\n第3阶段-C++核心编程\n\n第4阶段实战-基于多态的企业职工系统\n\n第5阶段-C++提高编程\n\n第6阶段实战-基于STL泛化编程的演讲比赛\n\n第7阶段-C++实战项目机房预约管理系统\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1et411b73Z\n\n',1,1,0),(9,'java','尚硅谷_IDEA','# 【尚硅谷】IDEA2022全新版教程，兼容JDK17（快速上手Java开发利器）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本套教程采用IDEA最新的2022.x版本（兼容JDK17），从IDEA的安装和设置入手，详解IDEA中工程与模块的结构、代码模板的设置与使用、断点调试、快捷键使用、Maven工程与Web工程的搭建、数据库的关联、常用热点插件使用等共计12大专题内容。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1CK411d7aA\n',66,1,0),(10,'java','尚学堂_Java300集','# 尚学堂给同学们带来全新的Java300集课程啦!java零基础小白自学Java必备优质教程_手把手图解学习Java，让学习成为一种享受\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n尚学堂给同学们带来全新的Java300集课程啦 \n\n本课程为Java300集2022版第一季，配合最新版的Java课程，所有视频重新录制，课件所有图形做了重新绘制和配色，图解学习Java，让学习成为一种享受 本套教程专门为零基础学员而制，适合准备入行Java开发的零基础学员，视频中穿插多个实战项目。每一个知识点都讲解的通俗易懂，由浅入深。不仅适用于零基础的初学者，有经验的程序员也可做巩固学习。 \n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1qL411u7eE\n',0,1,0),(11,'java','尚硅谷_Java基础极速版','# 【尚硅谷】7天搞定Java基础，Java零基础极速入门\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本套教程基于Java17和IDEA2022，专为Java初学者量身定制。通过本套教程的学习，你将掌握Java语言的整体结构和学习体系、Java基础语法及简单应用，为后续Web开发、Spring框架的学习打下坚实的基础。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1o841187iP\n    \n    \n',-2,1,0),(12,'java','尚硅谷_Java基础细致版','# 尚硅谷Java入门视频教程(在线答疑+Java面试真题)\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nMySQL百科全书（从小白到大牛）：[BV1iq4y1u7vj](https://www.bilibili.com/video/BV1iq4y1u7vj/?spm_id_from=333.788.video.desc.click)（宋红康主讲） \n\n宋红康亲授：播放量1000万+，好评如潮\n\n Java入门神器：2万多行代码+5套实战项目+近百道企业面试真题\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Kb411W75N\n',1,1,0),(13,'c++','Stanford_CS106L_c++','# CS106L: Standard C++ Programming\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n这门课会深入到很多标准 C++ 的特性和语法，让你编写出高质量的 C++ 代码。例如 auto binding, uniform initialization, lambda function, move semantics，RAII 等技巧都在我此后的代码生涯中被反复用到，非常实用。\n\n值得一提的是，这门课的作业里你会实现一个 HashMap（类似于 STL 中的 `unordered_map`), 这个作业几乎把整个课程串联了起来，非常考验代码能力。特别是 `iterator` 的实现，做完这个作业我开始理解为什么 Linus 对 C/C++ 嗤之以鼻了，因为真的很难写对。\n\n总的来讲这门课并不难，但是信息量很大，需要你在之后的开发实践中反复巩固。Stanford 之所以单开一门 C++ 的编程课，是因为它后续的很多 CS 课程 Project 都是基于 C++的。例如 CS144 计算机网络和 CS143 编译器。这两门课在本书中均有收录。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - Stanford\n\n    http://web.stanford.edu/class/cs106l/\n  \n- 课程视频：\n\n  - youtube\n\n    https://www.youtube.com/channel/UCSqr6y-eaQT_qZJVUm_4QxQ/playlists\n\n\n',2,1,0),(14,'java','尚学堂_Java全套','# JAVA全套课程尚学堂Java入门Java零基础必备Java编程课程Java核心基础EasyUISSM整合框架Redis高并发—全套课程\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nJava完整全套课程，一套视频全套包含！ javaSE基础入门，javaEE框架，SSM框架整合、Redis等全套课程，从入门到精通给你想要的！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV137411V7Y1\n',1,1,0),(15,'java','尚硅谷_JUC','# 尚硅谷2022版JUC并发编程（对标阿里P6-P7）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n从理论到实战，知识点涵盖全面，庖丁解牛式讲解！针对1-5年的Java程序员，精心设计的课程体系，详解原理，案例驱动，即给方法又给方案，生产环境模拟教学，大厂面试真题拆解，应有尽有！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1ar4y1x727\n',2,1,0),(16,'java','尚硅谷_JVM','# 尚硅谷宋红康JVM全套教程（详解java虚拟机）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n全套课程分为《内存与垃圾回收篇》《字节码与类的加载篇》《性能监控与调优篇》三个篇章，由尚硅谷宋红康老师亲自主刀，亲手绘制的图示，仅上篇就有50张之多...内容之强悍，可见一斑！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1PJ411n7xZ\n',2,1,0),(17,'java','黑马_MySql','# 黑马程序员 MySQL数据库入门到精通，从mysql安装到mysql高级、mysql优化全囊括\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程是目前为止，MySQL方面最为全面的一套课程，视频知识涵盖了MySQL的基础、进阶、运维等多个方面，不仅讲解知识点的具体应用，还会讲解其底层结构和原理。知识讲解全面、深入，能够完全满足我们日常的开发、运维、面试、以及自我提升，而且在讲解过程中结合多种手段，帮助学生更加清晰的理解课程中的重点和难点内容。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Kr4y1i7ru\n',2,1,0),(18,'java','尚硅谷_JDBC','# 尚硅谷JDBC核心技术视频教程（康师傅带你一站式搞定jdbc）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本套教程涵盖JDBC的方方面面，包括手动获取数据库连接的多种方式、使用数据库连接池获取连接、Statement与PreparedStatement的对比使用、sql注入问题讲解、Blob字段的操作、高效的批量插入、DAO设计模式、使用dbutils提供的相关工具类等。此外还对数据库事务进行详解，利用反射及JDBC元数据编写通用的查询方法等企业级开发内容。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1eJ411c7rf\n',66,1,0),(19,'java','尚硅谷_MySQL','# MySQL数据库教程天花板，mysql安装到mysql高级，强！硬！\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nMySQL课程天花板：6大范式讲解、7大日志剖析、7大SQL性能分析工具、9大存储引擎剖析、10大类30小类优化场景、15个不同锁的应用讲解、18种创建索引的规则、300+张高清无码技术剖析图......\n\n基础篇：P1 - P95 高级篇：P96 - P199\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1iq4y1u7vj\n',-1,1,0),(20,'java','尚硅谷_MySQL优化','# 尚硅谷MySQL数据库高级，mysql优化，数据库优化\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本教程主要讲授针对 Java 开发所需的 MySQL 高级知识，课程中会让大家快速掌握索引，如何避免索引失效，索引的优化策略，了解innodb和myisam存储引擎，熟悉MySQL锁机制，能熟练配置MySQL主从复制，熟练掌握explain、show profile、慢查询日志等日常SQL诊断和性能分析策略\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1KW411u7vy\n',1,1,0),(21,'java','黑马_JavaWeb','# 黑马程序员新版JavaWeb基础教程，Java web从入门到企业实战完整版\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nJavaWeb是整个Web开发的基础课程，需要掌握三部分内容：数据库、前端、web核心。本套JavaWeb教程旨在用最短的时间掌握最全的JavaWeb核心技术，使学习效率猛增2倍，并且可以为后期的分布式、微服务打下坚实的基础。 该套视频全是干货，不墨迹，没废话，让你花最短时间学会，包括javaweb+mysql+maven+html+css+ajax+vue+项目实战等内容，是目前站内最全的JavaWeb技术栈课程。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Qf4y1T7Hx\n\n',1,1,0),(22,'java','尚硅谷_JavaWeb','# 尚硅谷最新版JavaWeb全套教程,java web零基础入门完整版\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本视频涵盖核心技术点有：Servlet程序、Filter过滤器、Listener监听器、jsp页面、EL表达式、JSTL标签库、jQuery框架、Cookie技术、Session会话、JSON使用、Ajax请求，并在讲解知识点过程中会带领大家完成一个书城项目。相对于旧版，本版本使用idea进行开发，同时对多项技术做了升级！ 课程目标：听懂、理解、会用。并为后期框架、框架的学习打下坚实的基础\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Y7411K7zz\n',-1,1,0),(23,'java','黑马_Maven','# 黑马程序员Maven全套教程，maven项目管理从基础到高级，Java项目开发必会管理工具maven\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程主要讲解Maven的使用，从基础到高级，让学生深入了解Maven项目的构建及管理方式，Java项目开发所需的管理工具Maven。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Ah411S7ZE\n\n',1,1,0),(24,'java','黑马_MybatisPlus','# 黑马程序员MybatisPlus深入浅出教程，快速上手mybatisplus\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nMyBatis-Plus（简称 MP）是一个 MyBatis 的增强工具，在 MyBatis 的基础上只做增强不做改变，为简化开发、提高效率而生。使用原生的Mybatis编写持久层逻辑时，所需要的代码是比较繁琐的，需要定义Mapper接口和Mapper.xml文件，每一个方法都需要编写对应的sql语句，会存在很多大量的重复工作，使用MP之后，对通用的方法做了高度的抽取，避免了很多重复工作，可以非常快速的实现了单表的各种增、删、改、查操作。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1rE41197jR\n',2,1,0),(25,'java','黑马_Spring','# 黑马程序员Spring视频教程，深度讲解spring5底层原理\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程以讲解 Spring 原理知识为主。但又不同于一般的原理课，基本不翻源码，而是通过各种单元测试和模拟实现，带领学员更为感性地认识 Spring 底层。 学完本课程能够收获：培养正确的学习源码方法；睥睨其它程序员的资本；唯一认清 Spring 的机会！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1P44y1N7QG\n\n',2,1,0),(26,'java','黑马_SpringBoot','# 黑马程序员SpringBoot2全套视频教程，springboot零基础到项目实战（spring boot2完整版）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpringBoot技术是目前市面上从事JavaEE企业级开发过程中使用量最大的技术。本视频围绕SpringBoot技术由浅入深带领学习者从小白身份入门SpringBoot。经过若干个案例的制作与学习，全面掌握在企业级开发过程中如何使用SpringBoot技术将市面上各个层面各个领域的实用技术整合在一起工作，并应用于企业级开发各个层面的实际问题。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV15b4y1a7yG\n\n',2,1,0),(27,'java','黑马_SpringCloud','# SpringCloud+RabbitMQ+Docker+Redis+搜索+分布式，系统详解springcloud微服务技术栈课程|黑马程序员Java微服务\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpringCloud微服务技术栈课程火热登场！ SpringCloudAlibaba、RabbitMQ、Docker、Redis、Elasticsearch等众多行业大厂必备技术一网打尽。 实用篇、高级篇、面试篇分层次教学，由易到难，层层推进，高潮不断！ 该套教程技术体系完整，即使在职或者曾学过的话也强烈建议你再刷一遍这套教程！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1LQ4y127n4\n\n\n',2,1,0),(28,'java','黑马_SSM','# 黑马程序员2022新版SSM框架教程_Spring+SpringMVC+Maven高级+SpringBoot+MyBatisPlus企业实用开发技术\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSSM框架课程是Java从业人员从基础学习阶段进阶到初级程序员的入门课程，也是走向成功的必经之路。 SSM框架课程中共包含5个课程模块，分别是Spring框架、SpringMVC框架、Maven高级、SpringBoot框架、MyBatis-Plus框架。通过本阶段课程的学习，学习者可以掌握大量实用开发技术，企业开发规范，最终实现基于SpringBoot技术实现SSM整合。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Fi4y1S7ix\n',2,1,0),(29,'java','尚硅谷_Maven','# 尚硅谷2022版Maven教程（maven入门+高深，全网无出其右！）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n基础篇（1-53集），适合小白，快速上手； 高级篇（54-121集），适合开发人员，了解Maven项目开发全流程； 资深篇（122-173集），适合老鸟学习，搞定你开发中的诸多痛点。\n\n本课程将会让你从不会使用 Maven 的小白晋升熟练使用 Maven 管理依赖和构建过程的大牛，并理解 Maven 中各种内部机制的来龙去脉，在 Maven 的整个生态中游刃有余。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV12q4y147e4\n',-1,1,0),(30,'java','尚硅谷_Mybatis','# 【尚硅谷】2022版MyBatis零基础入门教程（细致全面，快速上手）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n课程包括MyBatis框架搭建，MyBatis配置文件以及映射文件的讲解以及编写，MyBatis获取参数值的方式，MyBatis中的各种查询功能，MyBatis的自定义映射，MyBatis动态SQL，MyBatis的缓存机制，MyBatis逆向工程...\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1VP4y1c7j7\n',-1,1,0),(31,'java','尚硅谷_Spring','# 【尚硅谷】Spring框架视频教程（spring超详细源码级讲解）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpring框架可以单独构建应用程序，也可以和其他框架组合使用。Spring框架凭借其强大的功能以及优良的性能，在企业开发中被广泛应用。 本套教程从Spring框架最基础的部分讲起，内容由浅入深，适合有一定Java开发基础的相关人员，也适合具备一定软件开发能力的人员。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Vf4y127N5\n',0,1,0),(32,'java','尚硅谷_SpringBoot','# 【尚硅谷】SpringBoot2零基础入门教程（spring boot2干货满满）\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nSpringBoot2升级之后，带来了非常多的新特性，以及底层源码设计的差异。本套视频教程基于SpringBoot2.3与2.4版本讲解，适用于有Spring、SpringMVC基础，初学或想深入了解SpringBoot的学习者。 教程包含核心基础、Web原理、单元测试、数据访问、指标监控等章节。 通过以上内容的学习，会将你的SpringBoot水平带到一个更高的层次，面向应用开发游刃有余！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV19K4y1L7MT\n',2,1,0),(33,'java','尚硅谷_SpringCloud','# 尚硅谷SpringCloud框架开发教程(SpringCloudAlibaba微服务分布式架构丨Spring Cloud)\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n尚硅谷SpringCloud 1 视频，一经推出，广受好评。本视频含SpringCloud Hoxton和SpringCloud alibaba，双剑合并，威力大增！内容涵盖目前火热的分布式微服务架构的全部技术栈，是尚硅谷高阶班微服务课程的全新升级版。新版教程对老版的五大技术做了升级加强和替换更新，对原有技术进行了更加深入的讲解，此外，引入了后起新秀SpringCloud alibaba，满足你对新技术的探索欲望！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV18E411x7eT\n',2,1,0),(34,'java','尚硅谷_SSM','# 【尚硅谷】SSM框架全套教程，MyBatis+Spring+SpringMVC+SSM整合一套通关\n\n\n\n## 课程简介\n\n- 编程语言：JAVA\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n尚硅谷研究院结合多年教学经验，充分教研精心设计了这套教程，只此一套教程即可SSM从原理到应用、再到核心源码，全部轻松拿捏！教程讲解直给干货，不说废话，简洁精炼，堪称SSM入门首选，可应对SSM学习中所有问题。 教程讲解循序渐进，先从MyBatis讲起，再依次介绍Spring、SpringMVC，最后完成整合。整套教程理论与应用完美结合，难点和重点针对性拆解，让你在掌握技术原理的同时，具备实战操作能力！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1Ya411S7aT\n',1,1,0),(35,'c++','老九学堂_零基础学c++','# 【零基础学C++】老九零基础学编程系列之C++\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n这是一门真正适合任何零基础学习的入门课，由教学经验超过十年的大咖级老师，老九学堂总教头徐嵩老师主讲。 本课程与外面那些妖艳贱货不同，我们不古板的深究语法，只注重于实用，课程中有很多逗笔，开脑洞的玩法，所用项目都是真实原创，课程将以游戏作为案例，揭露程序本质，将会了解到更多程序思维，我们的终极目标是学完课程能做出自己\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV12x411D7xr\n\n',0,1,0),(36,'c++','慕课_C++程序设计_西北工业大学','## 西北工业大学_C++程序设计\n\n\n\n### 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n### 课程描述\n\n程序设计课程是大学计算机基础教育和计算机科学与技术专业基础的核心课程，是数据结构、算法设计、数学建模、软件技术等课程的前导课程。程序设计课程的教学目标是使学生能够使用一种开发工具熟练的进行软件开发，为学生将来的创新实验、毕业设计、科学研究提供了有力的技术支持。\n\n C++是国内外广泛使用的计算机程序设计语言。其功能强大、面向对象、数据表示丰富、代码运行效率高、可移植性好，适合编写系统软件和各类应用程序。在TIOBE排行榜上，C++语言多年来始终处于前五位。学习程序设计从C++入手，对于培养利用计算机求解现实问题的计算思维能力具有其他语言无法比拟的有点。且在完全掌握了C++语言之后，再学习其他程序设计语言就会轻车熟路了。\n\nC是C++的子集，因此在C++的授课中，有至少一半的内容是和C语言一样的。而国内C++程序设计课程的学时普遍较少，且讲授的重点一般都放在和C重叠的那一部分。对于C++比C多出来的内容往往只介绍类、继承等基本概念，而对于重载、多态、异常处理、数据流等内容言之甚少。本课程即针对这一问题，增加了C++独有的内容的比重，不但适用于在校大学生，且适用于工作中使用C++进行软件开发的人。\n\n\n\n### 课程资源\n\n- 课程网站：\n\n  - 中国大学慕课\n\n    https://www.icourse163.org/course/NWPU-494001\n\n',-1,1,0),(37,'c++','慕课_C++程序设计基础_华中科技大学','# 华中科技大学_C++程序设计基础\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n​		人工智能带来了C++的再次繁荣，从某种程度上说，Python编程只是在搭建软件的外包装，而C++才是其核心。C++与C在占据系统底层应用方面没有什么差距，但是在规模化编程、自动生成、实现系统架构方面，非C++莫属。况且由于C++源自C的特点，C编程往往又是在C++平台中实现。追本溯源，C++语言才是当今人工智能大发展上最重要的工具。\n\n​		本课程是C++程序设计的入门课程。兼顾基础理论和编程实践。基础理论浅显易懂，编程案例生动形象。采用全国等级考试的集成开发环境**[VC++2010 Express](https://download.csdn.net/download/xiaodeng33/10678437)**进行编程与调试工具。从案例分析和问题入手，寻找解题思路，到编程、调试、运行，都借助于合适的实际案例进行展示。特别是初期的编译错误的定位与解决策略，后面的运行错误借助**调试工具**进行查错、纠错等，通过案例和视频的展示学会编程方法与调试技术。完整的案例分析和编程过程帮助初学者既能看懂教材，又能**解决上机无从下手的问题。**\n\n   	部分同学学习C++语言的**难点在指针**，指针的难点在于和数组、函数等结合下的变化，加上指针使用非常容易出错，使得指针更增加了神秘的色彩。本课程先进行理论讲解，通过指针对内存的实际操作情况进行演示与分析，然后通过由浅入深的编程例题和作业逐渐掌握指针。\n\n　　学习C++语言存在**面向过程思想和面向对象思想转变****的困难**，特别是面向对象擅长解决复杂问题，而对初学者的案例都是用简单问题来诠释面向对象的程序设计思想方法，造成难于领会面向对象的程序设计思想的精髓。本课程通过用对比方式，诠释面向对象和面向过程程序设计的区别，体会C++的优势。通过实际案例掌握面向对象思想，通过项目实践，解决学而不知何用的问题。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - 中国大学慕课\n\n    https://www.icourse163.org/course/HUST-1206625820\n\n',-1,1,0),(38,'c++','慕课_C++面向对象程序设计_北京大学','# 北京大学_程序设计与算法（三）C++面向对象程序设计\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程讲授C++程序设计有关的概念和语法，使你能够使用C++语言，以面向对象的方法编写可维护性、可扩充性好的，较大规模的程序。要求学习者已经掌握C语言程序设计。这门课将带你掌握C++语言中类、对象、运算符重载、继承、多态等面向对象的程序设计方法，以及模板、标准模板库STL等泛型程序设计的机制，体会和领悟面向对象程序设计方法和泛型程序设计方法的优势。\n\n**本课程作业和考试题都是在线提交程序，系统自动评测，容不得半点错误，这对学习者是非常严格而且有效的训练，符合当下顶尖IT企业招聘考核的形式，学习效果远胜于书面作业人工批改的形式。**期末还有一个大型的游戏模拟程序作业《魔兽世界》，深受北京大学信息学院学生欢迎，能够很好地训练C++面向程序设计的技能。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - 中国大学慕课\n\n    https://www.icourse163.org/course/PKU-1002029030\n\n',1,1,0),(39,'c++','慕课_程序设计基础_电子科技大学','# 电子科技大学_程序设计基础（C&C++）\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n​		本课程是程序设计C语言和C++语言的入门课程。兼顾基础理论和编程实践。基础理论浅显易懂，编程案例趣味性强。视频使用当下流行的集成开发环境**visual studio 2015（vs2008-VS2019各版本皆适用）**进行编程与调试工具。从分析问题，寻找解题思路，到编程、调试、运行，都借助于实际案例进行展示。特别是初期的编译错误的定位与解决策略，后面的运行错误借助**调试工具**进行查错、纠错等，通过一系列事例一一展开,很容易通过视频一步步的演示学会编程方法与调试技术。而**调试技术**的掌握是初学者的难点之一。完整的编程过程解决了初学者看教材明白，上机却无从下手的问题。\n\n​		不少同学学习C语言的难点在指针与函数。而随后的随处可见的C++的成员函数很自然的就解除函数方面的困惑。指针的难点在于和数组、函数等结合下的变化多端，加上指针使用非常容易出错，这种结合下的错误更多且难以解决。本课程先进行理论讲解，通过指针对内存的实际操作情况进行演示与分析，然后通过由浅入深的编程例题和作业逐渐掌握指针。\n\n　　学习C语言后，再学习C++，存在**面向过程思想向面向对象思想转变**的困难，造成学完C++还不能理解C++特点，也不能正确使用C++进行程序设计。本课程通过用C++语言改写前面C语言案例，对比理解二者的区别，体会C++的优势。然后用一个简单的**图形界面游戏**逐渐扩展功能，使其具有可玩性，功能的增加，使得C语言完成难度增加，而C++的类、继承、派生等面向对象技术却轻松实现这个游戏。通过编程实战掌握C++语言的优势，通过实际案例掌握面向对象思想，解决学而不知何用的困惑。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - 中国大学慕课\n\n    https://www.icourse163.org/course/UESTC-1001774006\n\n',-1,1,0),(40,'c++','慕课_计算机程序设计_西安交通大学','# 西安交通大学_计算机程序设计（C++）\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\n本课程以C++语言为载体，讲授计算机程序设计，为更好地利用计算机解决工程实践、科学研究和日常生活中的问题打下基础。\n\n从程序设计方法角度分，计算机语言有面向过程的和面向对象的。面向过程的思想是将任务分解成一系列的函数，函数通过相互调用联系起来完成任务。面向对象的思想是将任务分解成一系列对象，对象具有功能。对象间通过消息传递信息，触发事件，完成任务。面向对象的语言被认为具有更好的重用性、可维护性和可扩展性。C++是面向对象的语言。但也要注意，面向过程和面向对象不是截然分开的，不是对立的。面向过程是面向对象的基础。本课程前半部分的编程思想仍是面向过程的，这对初学者更容易入门；后半部分是面向对象的，为进一步学习奠定基础。\n\n学习程序设计，要学习语言的语法，更重要的是求解问题的算法思想。语法的学习需要多尝试，算法的学习需要多思考。学习程序设计，就要多编程。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - 中国大学慕课\n\n    https://www.icourse163.org/course/XJTU-46006\n\n',-1,1,0),(41,'c++','千锋教育_c++','# 千锋教育超级C++教程，碾压同类视频，学不会退币！课件，源码，笔记，软件，案例齐全，建议初学者收藏\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nC++教程从0到1匠心精作，快速入门编程，让学习编程不再难！\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1gS4y1872b\n\n',-1,1,0),(42,'c++','浙江大学_面向对象设计c++','# 【浙江大学】我真希望学C++之前，就听到翁恺教授讲解面向对象设计C++该多好，流下了没有技术的眼泪！\n\n\n\n## 课程简介\n\n- 编程语言：c++\n- 课程难度：:star::star::star::star::star:\n\n\n\n## 课程描述\n\nC++是一门功能超强大的编程语言，适用于程序设计，游戏开发，嵌入式设备，系统开发……没有哪个领域是不能用C++的，即使是最新潮的VR/AR。该课程助你在C++领域中更进一步。\n\n\n\n## 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV16P4y1o7No\n\n',1,1,0),(43,'python','RICE_An Introduction to Interactive Programming in Python (Part 1)','## An Introduction to Interactive Programming in Python (Part 1)\n\n\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：19小时\n\n### 课程描述\n\nPython入门级课程，这门课程将讲授Python编程基础知识，例如普通表达式，条件表达式和函数，并用这些知识构建一个简单的交互式应用。\n>This two-part course is designed to help students with very little or no computing background learn the basics of building simple interactive applications. Our language of choice, Python, is an easy-to learn, high-level computer language that is used in many of the computational courses offered on Coursera. To make learning Python easy, we have developed a new browser-based programming environment that makes developing interactive applications in Python simple. These applications will involve windows whose contents are graphical and respond to buttons, the keyboard and the mouse. In part 1 of this course, we will introduce the basic elements of programming (such as expressions, conditionals, and functions) and then use these elements to create simple interactive applications such as a digital stopwatch. Part 1 of this class will culminate in building a version of the classic arcade game \"Pong\".\n\n### 课程资源\n\n- 课程主页： https://www.coursera.org/archive/interactive-python-1\n',1,1,0),(44,'python','RICE_An Introduction to Interactive Programming in Python (Part 2)','## An Introduction to Interactive Programming in Python (Part 2)\n\n\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：16小时\n\n### 课程描述\n\nPython入门级课程，这门课程将继续讲授Python基础知识，例如列表，词典和循环，并将使用这些知识构建一个简单的游戏例如Blackjack:\n>This two-part course is designed to help students with very little or no computing background learn the basics of building simple interactive applications. Our language of choice, Python, is an easy-to learn, high-level computer language that is used in many of the computational courses offered on Coursera. To make learning Python easy, we have developed a new browser-based programming environment that makes developing interactive applications in Python simple. These applications will involve windows whose contents are graphical and respond to buttons, the keyboard and the mouse. In part 2 of this course, we will introduce more elements of programming (such as list, dictionaries, and loops) and then use these elements to create games such as Blackjack. Part 1 of this class will culminate in building a version of the classic arcade game \"Asteroids\". Upon completing this course, you will be able to write small, but interesting Python programs. The next course in the specialization will begin to introduce a more principled approach to writing programs and solving computational problems that will allow you to write larger and more complex programs.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/interactive-python-2\n',1,1,0),(45,'python','RICE_Learn to Program The Fundamentals','## Learn to Program: The Fundamentals\n\n\n\n### 课程简介\n\n- 开课大学：University Of Toronto\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：25小时\n\n\n### 课程描述\n\nPython入门级课程。这门课程以Python语言传授编程入门知识，实为零基础的Python入门课程。之前一个同学的评价是 “两个老师语速都偏慢，讲解细致，又有可视化工具Python Visualizer用于详细了解程序具体执行步骤，可以说是零基础学习python编程的最佳选择。”\n>Behind every mouse click and touch-screen tap, there is a computer program that makes things happen. This course introduces the fundamental building blocks of programming and teaches you how to write fun and useful programs using the Python language.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/learn-to-program\n',1,1,0),(46,'python','RICE_Programming for Everybody','##  Programming for Everybody\n\n\n\n### 课程简介\n\n- 编程语言：Python\n- 课程难度：:star:\n- 预计学时：19小时\n\n\n\n### 课程描述\n\nPython入门级课程，这门课程暂且翻译为“人人都可以学编程-从Python开始”，如果没有任何编程基础，就从这门课程开始吧：\n>This course aims to teach everyone the basics of programming computers using Python. We cover the basics of how one constructs a program from a series of simple instructions in Python. The course has no pre-requisites and avoids all but the simplest mathematics. Anyone with moderate computer experience should be able to master the materials in this course. This course will cover Chapters 1-5 of the textbook “Python for Everybody”. Once a student completes this course, they will be ready to take more advanced programming courses. This course covers Python 3.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python\n',1,1,0),(47,'python','RICE_Python Programming Essentials','## Python Programming Essentials\n\n\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star:\n- 预计学时：10小时\n\n\n\n### 课程描述\n\nPython入门基础课程，这门课程将讲授Python编程基础知识，包括表达式，变量，函数等，目标是让用户熟练使用Python:\n>This course will introduce you to the wonderful world of Python programming! We\'ll learn about the essential elements of programming and how to construct basic Python programs. We will cover expressions, variables, functions, logic, and conditionals, which are foundational concepts in computer programming. We will also teach you how to use Python modules, which enable you to benefit from the vast array of functionality that is already a part of the Python language. These concepts and skills will help you to begin to think like a computer programmer and to understand how to go about writing Python programs. By the end of the course, you will be able to write short Python programs that are able to accomplish real, practical tasks. This course is the foundation for building expertise in Python programming. As the first course in a specialization, it provides the necessary building blocks for you to succeed at learning to write more complex Python programs. This course uses Python 3. While many Python programs continue to use Python 2, Python 3 is the future of the Python programming language. This first course will use a Python 3 version of the CodeSkulptor development environment, which is specifically designed to help beginning programmers learn quickly. CodeSkulptor runs within any modern web browser and does not require you to install any software, allowing you to start writing and running small programs immediately. In the later courses in this specialization, we will help you to move to more sophisticated desktop development environments.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python-programming\n',1,1,0),(48,'python','黑马_python600集从入门到精通','## 黑马程序员Python教程_600集Python从入门到精通教程（懂中文就能学会）\n\n\n\n### 课程简介\n\n- 编程语言：python\n- 课程难度：:star::star::star:\n\n\n\n### 课程描述\n\n目录大纲：\n\n本套教程15天\n\n1-3   天内容为Linux基础命令\n\n4-13  天内容为Python基础教程\n\n14-15 天内容为 飞机大战项目演练\n\n \n\n视频概括：\n\n第一阶段（1-3天）：\n\n该阶段首先通过介绍不同领域的三种操作系统，操作系统的发展简史以及Linux系统的文件目录结构让大家对Linux系统有一个简单的认识，同时知道为什么要学习Linux命令。然后我们会正式学习Linux命令\n\n1. 文件和目录命令：ls，cd，touch，mkdir，rm\n\n2. 拷贝和移动命令：tree，cp，mv\n\n3. 文件内容命令：cat，more，grep\n\n4. 远程管理命令：ifconfig，ping，SSH的工作方式简介以及ssh命令\n\n5. 用户权限及用户管理命令：chmod，chgrp，useradd，passwd，userdel\n\n6. 软件安装及压缩命令：apt简介及命令，tar，gzip压缩命令，bzip2压缩命令\n\n7. vim的基本使用\n\n \n\n \n\n第二阶段（4-10天）\n\n该阶段我们正式进入Python这门语言的学习，首先通过了解Python语言的起源，Python语言的设计目标，Python语言的设计哲学，Python语言的优缺点和面向对象的基本概念，以及Python语言的执行方式，还有Python集成开发环境PyCharm的使用为我们接下来的学习做铺垫。\n\n然后我们会学习int，string，float三种简单的变量类型，变量间的计算，变量的输入输出，if判断语句，while循环语句，for循环语句，break和continue的使用，函数的基本使用，模块的使用，列表，元组，字典三种高级变量，字符串的常用操作。\n\n接下来我们会通过一个名片管理系统的案例，把这一阶段的知识进行一个串联。在学习名片管理系统时，首先我们会学习怎么去搭建这一系统的框架，然后我们会分别实现新增名片，显示全部名片，查询名片，删除名片，修改名片这些功能。\n\n最后我们会学习语法的进阶内容，全局变量，局部变量，可变数据类型和不可变数据类型以及函数返回多个值，函数的缺省参数，多值参数，递归的基本使用。\n\n  \n\n \n\n第三阶段（11-13天）\n\n该阶段我们会学习面向对象（OOP）这一重要的编程思想，首先学习的知识点有类和对象的基本概念，dir函数，self的作用，初始化方法__init__，内置函数__str__，__del__，单继承，方法重写，私有属性和方法，多继承，多态，类属性，静态方法。\n\n然后我们还会学习单例模式这一设计模式，异常的捕获，异常的抛出，from import局部导入，from import导入同名工具， from import导入所有工具，包的使用，制作模块，pip的使用以及文件的相关操作。\n\n \n\n \n\n第四阶段（14-15天）\n\n该阶段是项目演练阶段，我们会带领大家通过使用之前学习过的知识开发飞机大战这一经典游戏，项目中分别有游戏窗口，图像绘制，游戏循环，事件监听，精灵和精灵组以及创建敌机，创建英雄和发射子弹，碰撞检测等模块。\n\n\n\n### 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1ex411x7Em/?share_source=copy_web&vd_source=364714bccd281e7e2faabe29f7e6e2d0\n  - youtube   \n    https://www.youtube.com/playlist?list=PL961LrMWXCe6MCBIWtBRYnZdL-4U3wvCw\n',1,1,0),(49,'python','小甲鱼_0基础学习python','## 【Python教程】《零基础入门学习Python》最新版\n\n\n\n### 课程简介\n\n- 编程语言：python\n- 课程难度：:star::star\n\n\n\n### 课程描述\n\n本系列教程是《零基础入门学习Python》最新版教程，希望大家喜欢。\n\n-----------------------------------------------------------------------------------\n\nPython除了不能生孩子，其他的都能干！\n\n快跟小甲鱼一起来学习Python吧^_^\n\n人生苦短，我学Python~\n字幕制作者（中文（中国））： 鱼C-小师妹\n\n\n### 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1c4411e77t/?share_source=copy_web&vd_source=364714bccd281e7e2faabe29f7e6e2d0',66,1,0),(50,'python','RICE_Algorithmic Thinking (Part 1)','##  Algorithmic Thinking (Part 1)\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：12小时\n\n### 课程描述\n\n编程基础课程，这门课程聚焦在算法思维的培养上，讲授图算法的相关概念并用Python实现:\n>Experienced Computer Scientists analyze and solve computational problems at a level of abstraction that is beyond that of any particular programming language. This two-part course builds on the principles that you learned in our Principles of Computing course and is designed to train students in the mathematical concepts and process of \"Algorithmic Thinking\", allowing them to build simpler, more efficient solutions to real-world computational problems. In part 1 of this course, we will study the notion of algorithmic efficiency and consider its application to several problems from graph theory. As the central part of the course, students will implement several important graph algorithms in Python and then use these algorithms to analyze two large real-world data sets. The main focus of these tasks is to understand interaction between the algorithms and the structure of the data sets being analyzed by these algorithms. Recommended Background - Students should be comfortable writing intermediate size (300+ line) programs in Python and have a basic understanding of searching, sorting, and recursion. Students should also have a solid math background that includes algebra, pre-calculus and a familiarity with the math concepts covered in \"Principles of Computing\".\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/algorithmic-thinking-1',2,1,0),(51,'python','RICE_Algorithmic Thinking (Part 2)','##  Algorithmic Thinking (Part 2)\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：12小时\n\n### 课程描述\n\n编程基础课程，这门课程聚焦在培养学生的算法思维，并了解一些高级算法主题，例如分治法，动态规划等：\n>Experienced Computer Scientists analyze and solve computational problems at a level of abstraction that is beyond that of any particular programming language. This two-part class is designed to train students in the mathematical concepts and process of \"Algorithmic Thinking\", allowing them to build simpler, more efficient solutions to computational problems. In part 2 of this course, we will study advanced algorithmic techniques such as divide-and-conquer and dynamic programming. As the central part of the course, students will implement several algorithms in Python that incorporate these techniques and then use these algorithms to analyze two large real-world data sets. The main focus of these tasks is to understand interaction between the algorithms and the structure of the data sets being analyzed by these algorithms. Once students have completed this class, they will have both the mathematical and programming skills to analyze, design, and program solutions to a wide range of computational problems. While this class will use Python as its vehicle of choice to practice Algorithmic Thinking, the concepts that you will learn in this class transcend any particular programming language.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/algorithmic-thinking-2',2,1,0),(52,'python','RICE_Applied Machine Learning in Python','## Applied Machine Learning in Python\n\n\n\n### 课程简介\n\n- 开课大学：University Of Michigan\n- 编程语言：Python\n- 课程难度：:star::star::star::star:\n- 预计学时：31小时\n\n### 课程描述\n\nPython应用课程，这门课程主要聚焦在通过Python应用机器学习，包括机器学习和统计学的区别，机器学习工具包scikit-learn的介绍，有监督学习和无监督学习，数据泛化问题（例如交叉验证和过拟合）等。\n>This course will introduce the learner to applied machine learning, focusing more on the techniques and methods than on the statistics behind these methods. The course will start with a discussion of how machine learning is different than descriptive statistics, and introduce the scikit learn toolkit. The issue of dimensionality of data will be discussed, and the task of clustering data, as well as evaluating those clusters, will be tackled. Supervised approaches for creating predictive models will be described, and learners will be able to apply the scikit learn predictive modelling methods while understanding process issues related to data generalizability (e.g. cross validation, overfitting). The course will end with a look at more advanced techniques, such as building ensembles, and practical limitations of predictive models. By the end of this course, students will be able to identify the difference between a supervised (classification) and unsupervised (clustering) technique, identify which technique they need to apply for a particular dataset and need, engineer features to meet that need, and write python code to carry out an analysis. This course should be taken after Introduction to Data Science in Python and Applied Plotting, Charting & Data Representation in Python and before Applied Text Mining in Python and Applied Social Analysis in Python.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python-machine-learning\n',2,1,0),(53,'python','RICE_Applied Plotting, Charting & Data Representation in Python','## Applied Plotting, Charting & Data Representation in Python\n\n### 课程简介\n\n- 开课大学：University Of Michigan\n- 编程语言：Python\n- 课程难度：:star::star::star:\n- 预计学时：24小时\n\n### 课程描述\n\nPython应用课程，这门课程聚焦在通过使用matplotlib库进行数据图表的绘制和可视化呈现：\n>This course will introduce the learner to information visualization basics, with a focus on reporting and charting using the matplotlib library. The course will start with a design and information literacy perspective, touching on what makes a good and bad visualization, and what statistical measures translate into in terms of visualizations. The second week will focus on the technology used to make visualizations in python, matplotlib, and introduce users to best practices when creating basic charts and how to realize design decisions in the framework. The third week will describe the gamut of functionality available in matplotlib, and demonstrate a variety of basic statistical charts helping learners to identify when a particular method is good for a particular problem. The course will end with a discussion of other forms of structuring and visualizing data. This course should be taken after Introduction to Data Science in Python and before the remainder of the Applied Data Science with Python courses: Applied Machine Learning in Python, Applied Text Mining in Python, and Applied Social Network Analysis in Python.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python-plotting\n',2,1,0),(54,'python','RICE_Introduction to Data Science in Python','##   Introduction to Data Science in Python\n\n\n\n### 课程简介\n\n- 开课大学：University Of Michigan\n- 编程语言：Python\n- 课程难度：:star::star::star:\n- 预计学时：35小时\n\n### 课程描述\n\nPython基础和应用课程，这门课程从Python基础讲起，然后通过pandas数据科学库介绍DataFrame等数据分析中的核心数据结构概念，让学生学会操作和分析表格数据并学会运行基础的统计分析工具。\n> This course will introduce the learner to the basics of the python programming environment, including how to download and install python, expected fundamental python programming techniques, and how to find help with python programming questions. The course will also introduce data manipulation and cleaning techniques using the popular python pandas data science library and introduce the abstraction of the DataFrame as the central data structure for data analysis. The course will end with a statistics primer, showing how various statistical measures can be applied to pandas DataFrames. By the end of the course, students will be able to take tabular data, clean it, manipulate it, and run basic inferential statistical analyses. This course should be taken before any of the other Applied Data Science with Python courses: Applied Plotting, Charting & Data Representation in Python, Applied Machine Learning in Python, Applied Text Mining in Python, Applied Social Network Analysis in Python.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python-data-analysis\n',2,1,0),(55,'python','RICE_Principles of Computing (Part 1)','##  Principles of Computing (Part 1)\n\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：19小时\n\n### 课程描述\n\n编程基础课程，这门课程聚焦在了编程的基础上，包括编码标准和测试，数学基础包括概率和组合等。\n>This two-part course builds upon the programming skills that you learned in our Introduction to Interactive Programming in Python course. We will augment those skills with both important programming practices and critical mathematical problem solving skills. These skills underlie larger scale computational problem solving and programming. The main focus of the class will be programming weekly mini-projects in Python that build upon the mathematical and programming principles that are taught in the class. To keep the class fun and engaging, many of the projects will involve working with strategy-based games. In part 1 of this course, the programming aspect of the class will focus on coding standards and testing. The mathematical portion of the class will focus on probability, combinatorics, and counting with an eye towards practical applications of these concepts in Computer Science. Recommended Background - Students should be comfortable writing small (100+ line) programs in Python using constructs such as lists, dictionaries and classes and also have a high-school math background that includes algebra and pre-calculus.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/principles-of-computing-1\n',2,1,0),(56,'python','RICE_Principles of Computing (Part 2)','##  Principles of Computing (Part 2)\n\n### 课程简介\n\n- 开课大学：RICE\n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：16小时\n\n### 课程描述\n\n编程基础课程，这门课程聚焦在搜索、排序、递归等主题上：\n>This two-part course introduces the basic mathematical and programming principles that underlie much of Computer Science. Understanding these principles is crucial to the process of creating efficient and well-structured solutions for computational problems. To get hands-on experience working with these concepts, we will use the Python programming language. The main focus of the class will be weekly mini-projects that build upon the mathematical and programming principles that are taught in the class. To keep the class fun and engaging, many of the projects will involve working with strategy-based games. In part 2 of this course, the programming portion of the class will focus on concepts such as recursion, assertions, and invariants. The mathematical portion of the class will focus on searching, sorting, and recursive data structures. Upon completing this course, you will have a solid foundation in the principles of computation and programming. This will prepare you for the next course in the specialization, which will begin to introduce a structured approach to developing and analyzing algorithms. Developing such algorithmic thinking skills will be critical to writing large scale software and solving real world computational problems.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/principles-of-computing-2\n',2,1,0),(57,'python','RICE_Python Data Structures','## Python Data Structures\n\n\n\n### 课程简介\n\n- 所属大学：University Of Michigan \n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：19小时\n\n\n\n### 课程描述\n\nPython基础课程，这门课程的目标是介绍Python语言的核心数据结构（This course will introduce the core data structures of the Python programming language.），关于这门课程：\n>This course will introduce the core data structures of the Python programming language. We will move past the basics of procedural programming and explore how we can use the Python built-in data structures such as lists, dictionaries, and tuples to perform increasingly complex data analysis. This course will cover Chapters 6-10 of the textbook “Python for Everybody”. This course covers Python 3.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python-data\n',2,1,0),(58,'python','RICE_University Of Michigan','## Using Databases with Python\n\n### 课程简介\n\n- 所属大学：University Of Michigan \n- 编程语言：Python\n- 课程难度：:star::star:\n- 预计学时：15小时\n\n\n### 课程描述\n\nPython应用课程，在Python中使用数据库。这门课程的目标是在Python中学习SQL，使用SQLite3作为抓取数据的存储数据库：\n>This course will introduce students to the basics of the Structured Query Language (SQL) as well as basic database design for storing data as part of a multi-step data gathering, analysis, and processing effort. The course will use SQLite3 as its database. We will also build web crawlers and multi-step data gathering and visualization processes. We will use the D3.js library to do basic data visualization. This course will cover Chapters 14-15 of the book “Python for Everybody”. To succeed in this course, you should be familiar with the material covered in Chapters 1-13 of the textbook and the first three courses in this specialization. This course covers Python 3.\n\n### 课程资源\n\n- 课程主页：https://www.coursera.org/archive/python-databases\n',2,1,0),(59,'python','黑马_Django入门视频教程','## Django视频教程_Django入门视频教程|黑马程序员\n\n\n\n### 课程简介\n\n- 编程语言：python\n- 课程难度：:star::star::star::\n\n\n\n### 课程描述\n\nDjango视频教程_Django入门视频教程|黑马程序员\n\n课程内容：\n\n1、Django的简介。\n\n主要知识点：MVC设计模式以及Django的MVT。\n\n \n\n2、搭建开发环境：\n\n主要知识点：Linux的虚拟环境搭建和应用、Django的安装。\n\n \n\n3、利用Django框架完成简单的图书项目：\n\n主要知识点：编写模型，使用API与数据库交互、使用Django的后台管理管理数据、通过视图接收请求，通过模型获取数据、调用模板完成页面展示。\n\n\n### 课程资源\n\n- 课程网站：\n\n  - 黑马官网\n\n    http://yun.itheima.com/course/257.html?hm\n',2,1,0),(60,'python','黑马_python进阶之Mysql入门教程','## python进阶之Mysql入门教程|黑马程序员\n\n\n\n### 课程简介\n\n- 编程语言：python\n- 课程难度：:star::star::star::\n\n\n\n### 课程描述\n\n课程亮点：\n\n1，课程由浅到深，由原理到实践，适合零基础入门学习。\n\n2，结合实际案例，培养解决实际问题的能力。\n\n\n\n课程内容：\n\n1.掌握数据库的分类\n\n2.熟悉SQL介绍\n\n3.熟悉MySQL介绍\n\n4.掌握数据库基本操作\n\n5.掌握数据的增删改查CRUD\n\n6.掌握MySQL脚本数据备份\n\n7.掌握python操作数据库CRUD\n\n\n\n适用人群：\n\n1、对mysql感兴趣的在校生及应届毕业生。\n\n2、对目前职业有进一步提升要求，希望从事数据行业工作的在职人员。\n\n3、对数据行业感兴趣的相关人员。\n\n\n\n基础课程主讲内容包括：\n\n1. 数据库介绍\n\n2.Mysql和SQL\n\n3.Mysql的安装启动\n\n4.Navicat的基本使用\n\n5.Mysql的基本使用\n\n6.Python和Mysql的交互\n\n7.黑马订单管理案例\n\n\n\nMysql基础入门课程大纲：\n\n阶段一：数据库介绍\n\n1.数据库分类介绍\n\n2.关系型数据库介绍\n\n3.非关系型数据库介绍\n\n\n\n阶段二：Mysql和SQL\n\n1.Mysql的介绍\n\n2.SQL语句的介绍\n\n3.SQL和Mysql的关系\n\n\n\n阶段三：Mysql的安装启动\n\n1.Mysql安装和删除\n\n2.数据库的创建和删除\n\n3.字段类型和约束介绍\n\n\n\n\n\n阶段四：Navicat的基本使用\n\n1.navicat介绍和安装\n\n2.navicat连接mysql服务\n\n3.navicat操作数据库\n\n4.navicat操作数据表\n\n5.navicat操作数据CRUD\n\n\n\n\n\n阶段五.Mysql的基本使用\n\n1.终端操作登入登出mysql客户端\n\n2.终端操作数据库指令\n\n3.终端操作数据表指令\n\n4.终端操作数据查询指令\n\n5.终端操作数据增加指令\n\n6.终端操作数据修改和删除指令\n\n7.终端操作备份和恢复\n\n\n\n阶段六.Python和Mysql的交互\n\n1.pymysql的安装\n\n2.pymysql的使用介绍\n\n3.pymysql查询数据\n\n4.pymysql增加修改删除数据\n\n\n\n阶段七.黑马订单管理案例\n\n1.订单管理案例介绍\n\n2.订单管理案例需求分析\n\n3.订单管理案例-实现建库建表\n\n4.订单管理案例-实现插入测试数据\n\n5.订单管理案例-分析查询功能\n\n6.订单管理案例-实现查询功能\n\n7.订单管理案例-分析增加功能\n\n8.订单管理案例-实现增加功能\n\n9.订单管理案例-实现封装功函数\n\n10.黑马订单案例修改和删除\n\n\n### 课程资源\n\n- 课程网站：\n\n  - 黑马官方\n\n    http://yun.itheima.com/course/764.html?hm\n',2,1,0),(61,'python','黑马_2022新版python8天入门到精通','## 黑马程序员 零基础学Java语言2022新版黑马程序员python教程，8天python从入门到精通\n\n\n\n### 课程简介\n\n- 编程语言：Python\n- 课程难度：:star::star\n\n\n\n### 课程描述\n\n本视频主要面向的群体是：  \n\n1.想学习Python基础语法，但从来没接触过的同学\n\n2.已经学过，但是基础掌握不扎实的同学，本套视频适合快速复习\n\n\n学完本课程能够收获：\n\n掌握Python基础语法，掌握代码编写的规范和技巧，Bug调试能力，用Python第三方库做出可视化图表\n\n\n\n讲解方式：\n\n本课程从零基础开始入门学习Python，从软件下载，IDE使用，让学生一步步了解Python，课堂生动有趣，不枯燥。\n\n\n\n课程亮点：\n\n1.开发环境使用Python 3.10\n\n2.课程难度适中，保姆式教学，层层递进，利于学生吸收\n\n3.学练结合，让学生在听课的同时，也能动手操作练习，吸收巩固\n\n4.基础语法学习完毕后配套综合练习，感受Python的广泛用途\n\n5.锻炼学生的自主解决问题的能力和举一反三能力\n\n6.提供完整课件，源码，安装包，让学生学习更高效\n\n\n\n### 课程资源\n\n- 课程网站：\n\n  - bilibili\n\n    https://www.bilibili.com/video/BV1qW4y1a7fU/?share_source=copy_web&vd_source=364714bccd281e7e2faabe29f7e6e2d0\n  - 黑马官网  \n   http://yun.itheima.com/course/1008.html?hm\n',-1,1,0);
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
INSERT INTO `course_module` VALUES (1,1,'java基础',0),(1,2,'java高级',1),(1,3,'java数据库开发',0),(1,4,'java web',0),(1,5,'java开发框架',0),(2,1,'c++基础',0),(3,1,'python基础',0),(3,2,'python进阶',1),(3,3,'python高级',1);
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
/*!40000 ALTER TABLE `indirect_comment` ENABLE KEYS */;
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
INSERT INTO `module_course` VALUES (1,1,1),(1,1,2),(1,1,3),(1,1,4),(1,1,5),(1,1,6),(1,1,7),(2,1,8),(1,1,9),(1,1,10),(1,1,11),(1,1,12),(2,1,13),(1,1,14),(1,2,15),(1,2,16),(1,3,17),(1,3,18),(1,3,19),(1,3,20),(1,4,21),(1,4,22),(1,5,23),(1,5,24),(1,5,25),(1,5,26),(1,5,27),(1,5,28),(1,5,29),(1,5,30),(1,5,31),(1,5,32),(1,5,33),(1,5,34),(2,1,35),(2,1,36),(2,1,37),(2,1,38),(2,1,39),(2,1,40),(2,1,41),(2,1,42),(3,1,43),(3,1,44),(3,1,45),(3,1,46),(3,1,47),(3,1,48),(3,1,49),(3,2,50),(3,2,51),(3,3,52),(3,2,53),(3,2,54),(3,2,55),(3,2,56),(3,2,57),(3,3,58),(3,3,59),(3,2,60),(3,1,61);
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
INSERT INTO `programming_language` VALUES (1,'java',0),(2,'c++',0),(3,'python',0),(4,'c',0);
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
  `state` int NOT NULL DEFAULT '0' COMMENT '用户账号的状态',
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
/*!40000 ALTER TABLE `user_submits_blog` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2022-12-30 17:26:58
