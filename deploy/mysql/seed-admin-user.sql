-- 管理员账号：用户名 admin，密码 admin123（BCrypt）
USE singularity_user;

DELETE FROM user WHERE username = 'admin';

INSERT INTO user (username, password, nickname, role, balance)
VALUES (
  'admin',
  '$2b$10$OViScRZ8ZBIlCrl3B8Fb5.kwTgJzPuasEXNEQVFs2fuwpgzCL7t6u',
  '管理员',
  'admin',
  10000.00
);

SELECT id, username, nickname, role, balance FROM user WHERE username = 'admin';
