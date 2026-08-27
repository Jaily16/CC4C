ALTER TABLE course DROP FOREIGN KEY course_fk;

ALTER TABLE administrator CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE programming_language CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE user CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE course CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE course_module CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE module_course CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE blog CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE blog_draft CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE blog_involves_language CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE user_submits_blog CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE user_collects_blog CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE user_favors_course CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE course_direct_comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE blog_direct_comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE indirect_comment CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE course
    ADD CONSTRAINT course_fk FOREIGN KEY (language_name)
        REFERENCES programming_language (language_name);

ALTER TABLE course_direct_comment
    ADD CONSTRAINT uk_course_direct_comment_owner UNIQUE (comment_id);

ALTER TABLE blog_direct_comment
    ADD CONSTRAINT uk_blog_direct_comment_owner UNIQUE (comment_id);

ALTER TABLE indirect_comment
    ADD CONSTRAINT uk_indirect_comment_owner UNIQUE (comment_id),
    ADD CONSTRAINT indirect_comment_father_fk FOREIGN KEY (father_id) REFERENCES comment (comment_id),
    ADD INDEX idx_indirect_comment_father (father_id, comment_id);

ALTER TABLE blog
    ADD INDEX idx_blog_state_time (state, deleted, publish_time, blog_id),
    ADD INDEX idx_blog_state_click (state, deleted, click, blog_id);
