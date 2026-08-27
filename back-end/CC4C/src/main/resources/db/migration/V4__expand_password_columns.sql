ALTER TABLE `user`
    MODIFY COLUMN `password` VARCHAR(255) NOT NULL;

ALTER TABLE `administrator`
    MODIFY COLUMN `admin_password` VARCHAR(255) NOT NULL;
