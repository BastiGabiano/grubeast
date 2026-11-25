-- Grub East database (MySQL / MariaDB)
-- The app runs all of this by itself on first start, so you normally never need this file.
-- It is here so you can read the design or load it by hand in phpMyAdmin.

CREATE DATABASE IF NOT EXISTS grubeast CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE grubeast;

CREATE TABLE IF NOT EXISTS images (id BIGINT AUTO_INCREMENT PRIMARY KEY, data MEDIUMBLOB NOT NULL);

CREATE TABLE IF NOT EXISTS users (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              name VARCHAR(120) NOT NULL,
              email VARCHAR(190) NOT NULL UNIQUE,
              password VARCHAR(255) NOT NULL,
              role VARCHAR(20) NOT NULL,
              approved TINYINT(1) NOT NULL DEFAULT 1,
              rejected TINYINT(1) NOT NULL DEFAULT 0,
              rejection_reason VARCHAR(300),
              bio VARCHAR(1000),
              contact VARCHAR(200)
            );

CREATE TABLE IF NOT EXISTS shops (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              owner_id BIGINT NOT NULL UNIQUE,
              description VARCHAR(1000),
              location VARCHAR(200),
              available_days VARCHAR(200),
              open_time VARCHAR(10),
              close_time VARCHAR(10),
              allow_preorder TINYINT(1) NOT NULL DEFAULT 0,
              logo_id BIGINT NULL,
              FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (logo_id) REFERENCES images(id) ON DELETE SET NULL
            );

CREATE TABLE IF NOT EXISTS products (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              shop_id BIGINT NOT NULL,
              name VARCHAR(150) NOT NULL,
              price DECIMAL(10,2) NOT NULL,
              available TINYINT(1) NOT NULL DEFAULT 1,
              description VARCHAR(500),
              image_id BIGINT NULL,
              FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
              FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL
            );

CREATE TABLE IF NOT EXISTS reviews (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              shop_id BIGINT NOT NULL,
              author_id BIGINT NOT NULL,
              rating INT NOT NULL,
              body VARCHAR(500),
              posted_at DATETIME NOT NULL,
              UNIQUE (shop_id, author_id),
              FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
              FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
            );

CREATE TABLE IF NOT EXISTS events (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              organizer_id BIGINT NOT NULL,
              title VARCHAR(150) NOT NULL,
              description VARCHAR(1000),
              event_date DATE NOT NULL,
              start_time VARCHAR(10),
              end_time VARCHAR(10),
              location VARCHAR(200),
              seeking_sponsors TINYINT(1) NOT NULL DEFAULT 0,
              image_id BIGINT NULL,
              FOREIGN KEY (organizer_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (image_id) REFERENCES images(id) ON DELETE SET NULL
            );

CREATE TABLE IF NOT EXISTS sponsor_applications (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              event_id BIGINT NOT NULL,
              business_id BIGINT NOT NULL,
              message VARCHAR(500),
              status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
              UNIQUE (event_id, business_id),
              FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
              FOREIGN KEY (business_id) REFERENCES users(id) ON DELETE CASCADE
            );

CREATE TABLE IF NOT EXISTS messages (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              sender_id BIGINT NOT NULL,
              recipient_id BIGINT NOT NULL,
              body VARCHAR(1000) NOT NULL,
              sent_at DATETIME NOT NULL,
              seen TINYINT(1) NOT NULL DEFAULT 0,
              FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
            );

CREATE TABLE IF NOT EXISTS cart_items (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              product_id BIGINT NOT NULL,
              quantity INT NOT NULL,
              UNIQUE (user_id, product_id),
              FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
            );

CREATE TABLE IF NOT EXISTS preorders (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              shop_id BIGINT NOT NULL,
              customer_id BIGINT NOT NULL,
              event_id BIGINT NULL,
              status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
              note VARCHAR(300),
              created_at DATETIME NOT NULL,
              FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
              FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE CASCADE,
              FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE SET NULL
            );

CREATE TABLE IF NOT EXISTS preorder_items (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              preorder_id BIGINT NOT NULL,
              product_name VARCHAR(150) NOT NULL,
              unit_price DECIMAL(10,2) NOT NULL,
              quantity INT NOT NULL,
              FOREIGN KEY (preorder_id) REFERENCES preorders(id) ON DELETE CASCADE
            );

