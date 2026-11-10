-- Run as a MySQL administrator. Application credentials are created separately.
CREATE DATABASE IF NOT EXISTS studyfix_management CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
-- Tables are initialized automatically from src/studyfix/db/mysql-schema.sql
-- when the application starts with STUDYFIX_MANAGEMENT_DB=mysql.
