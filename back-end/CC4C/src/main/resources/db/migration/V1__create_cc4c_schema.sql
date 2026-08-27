CREATE TABLE administrator (
    admin_id CHAR(7) NOT NULL,
    admin_password VARCHAR(16) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    PRIMARY KEY (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE programming_language (
    language_id INT NOT NULL AUTO_INCREMENT,
    language_name VARCHAR(15) NOT NULL,
    deleted INT NOT NULL DEFAULT 0,
    PRIMARY KEY (language_id),
    UNIQUE KEY programming_language_pk (language_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE user (
    user_id BIGINT NOT NULL,
    user_name VARCHAR(30) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password VARCHAR(16) NOT NULL,
    major INT DEFAULT NULL,
    avatar VARCHAR(260) DEFAULT NULL,
    state INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    favourite_language INT DEFAULT NULL,
    deleted INT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    UNIQUE KEY user_unique (user_name),
    UNIQUE KEY email_unique (email),
    KEY user_fk (favourite_language),
    CONSTRAINT user_fk FOREIGN KEY (favourite_language) REFERENCES programming_language (language_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE course (
    course_id INT NOT NULL AUTO_INCREMENT,
    language_name VARCHAR(15) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    level INT NOT NULL DEFAULT 0,
    state INT NOT NULL DEFAULT 1,
    deleted INT NOT NULL DEFAULT 0,
    PRIMARY KEY (course_id),
    UNIQUE KEY course_pk (course_name),
    KEY course_fk (language_name),
    CONSTRAINT course_fk FOREIGN KEY (language_name) REFERENCES programming_language (language_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE course_module (
    language_id INT NOT NULL,
    priority INT NOT NULL,
    module_name VARCHAR(50) NOT NULL,
    level INT NOT NULL DEFAULT 0,
    PRIMARY KEY (language_id, priority),
    CONSTRAINT course_module_fk FOREIGN KEY (language_id) REFERENCES programming_language (language_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE module_course (
    language_id INT NOT NULL,
    priority INT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (language_id, priority, course_id),
    KEY module_course_fk2 (course_id),
    CONSTRAINT module_course_fk1 FOREIGN KEY (language_id, priority)
        REFERENCES course_module (language_id, priority),
    CONSTRAINT module_course_fk2 FOREIGN KEY (course_id) REFERENCES course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE blog (
    blog_id BIGINT NOT NULL,
    writer_id BIGINT NOT NULL,
    title VARCHAR(75) NOT NULL,
    content LONGTEXT NOT NULL,
    publish_time DATETIME DEFAULT NULL,
    click INT NOT NULL DEFAULT 0,
    state INT NOT NULL DEFAULT 0,
    deleted INT NOT NULL DEFAULT 0,
    PRIMARY KEY (blog_id),
    KEY blog_fk (writer_id),
    CONSTRAINT blog_fk FOREIGN KEY (writer_id) REFERENCES user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE blog_draft (
    user_id BIGINT NOT NULL,
    content LONGTEXT,
    PRIMARY KEY (user_id),
    CONSTRAINT blog_draft_fk FOREIGN KEY (user_id) REFERENCES user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE blog_involves_language (
    blog_id BIGINT NOT NULL,
    language_id INT NOT NULL,
    PRIMARY KEY (blog_id, language_id),
    KEY blog_involves_language_fk2 (language_id),
    CONSTRAINT blog_involves_language_fk1 FOREIGN KEY (blog_id) REFERENCES blog (blog_id),
    CONSTRAINT blog_involves_language_fk2 FOREIGN KEY (language_id)
        REFERENCES programming_language (language_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE user_submits_blog (
    user_id BIGINT NOT NULL,
    blog_id BIGINT NOT NULL,
    submit_time DATETIME NOT NULL,
    PRIMARY KEY (user_id, blog_id),
    KEY user_submits_blog_fk2 (blog_id),
    CONSTRAINT user_submits_blog_fk1 FOREIGN KEY (user_id) REFERENCES user (user_id),
    CONSTRAINT user_submits_blog_fk2 FOREIGN KEY (blog_id) REFERENCES blog (blog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE user_collects_blog (
    user_id BIGINT NOT NULL,
    blog_id BIGINT NOT NULL,
    time DATETIME NOT NULL,
    PRIMARY KEY (user_id, blog_id),
    KEY user_collects_blog_fk2 (blog_id),
    CONSTRAINT user_collects_blog_fk1 FOREIGN KEY (user_id) REFERENCES user (user_id),
    CONSTRAINT user_collects_blog_fk2 FOREIGN KEY (blog_id) REFERENCES blog (blog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE user_favors_course (
    user_id BIGINT NOT NULL,
    course_id INT NOT NULL,
    time DATETIME NOT NULL,
    PRIMARY KEY (user_id, course_id),
    KEY user_favors_course_fk2 (course_id),
    CONSTRAINT user_favors_course_fk1 FOREIGN KEY (user_id) REFERENCES user (user_id),
    CONSTRAINT user_favors_course_fk2 FOREIGN KEY (course_id) REFERENCES course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE comment (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    time DATETIME NOT NULL,
    `like` INT NOT NULL DEFAULT 0,
    deleted INT NOT NULL DEFAULT 0,
    PRIMARY KEY (comment_id),
    KEY comment_fk (user_id),
    CONSTRAINT comment_fk FOREIGN KEY (user_id) REFERENCES user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE course_direct_comment (
    comment_id BIGINT NOT NULL,
    course_id INT NOT NULL,
    PRIMARY KEY (comment_id, course_id),
    KEY course_direct_comment_fk2 (course_id),
    CONSTRAINT course_direct_comment_fk1 FOREIGN KEY (comment_id) REFERENCES comment (comment_id),
    CONSTRAINT course_direct_comment_fk2 FOREIGN KEY (course_id) REFERENCES course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE blog_direct_comment (
    comment_id BIGINT NOT NULL,
    blog_id BIGINT NOT NULL,
    PRIMARY KEY (comment_id, blog_id),
    KEY blog_direct_comment_fk2 (blog_id),
    CONSTRAINT blog_direct_comment_fk1 FOREIGN KEY (comment_id) REFERENCES comment (comment_id),
    CONSTRAINT blog_direct_comment_fk2 FOREIGN KEY (blog_id) REFERENCES blog (blog_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

CREATE TABLE indirect_comment (
    comment_id BIGINT NOT NULL,
    father_id BIGINT NOT NULL,
    layer INT NOT NULL DEFAULT 0,
    PRIMARY KEY (comment_id, father_id),
    CONSTRAINT indirect_comment_fk1 FOREIGN KEY (comment_id) REFERENCES comment (comment_id),
    CONSTRAINT check_max_layer CHECK (layer <= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;
