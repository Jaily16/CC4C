ALTER TABLE user_favors_course
    ADD INDEX idx_user_favors_course_user_time_course (user_id, `time` DESC, course_id);

ALTER TABLE user_collects_blog
    ADD INDEX idx_user_collects_blog_user_time_blog (user_id, `time` DESC, blog_id DESC);
