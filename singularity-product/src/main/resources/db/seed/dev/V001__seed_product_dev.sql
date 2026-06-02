INSERT INTO `product` (`product_id`, `name`, `subtitle`, `main_image`, `category`, `tags`, `status`, `price`, `version`, `is_deleted`)
VALUES
  ('IPHONE_16', 'iPhone 16', 'A18 芯片，性能旗舰', 'https://cdn.example.com/products/iphone16.png', 'phone', 'apple,ios,5g', 1, 6999.00, 0, 0),
  ('PS5_PRO', 'PlayStation 5 Pro', '次世代主机升级版', 'https://cdn.example.com/products/ps5pro.png', 'console', 'sony,game,4k', 1, 4999.00, 0, 0),
  ('RTX_5090', 'GeForce RTX 5090', '高性能显卡', 'https://cdn.example.com/products/rtx5090.png', 'gpu', 'nvidia,ai,render', 1, 15999.00, 0, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `subtitle` = VALUES(`subtitle`),
  `main_image` = VALUES(`main_image`),
  `category` = VALUES(`category`),
  `tags` = VALUES(`tags`),
  `status` = VALUES(`status`),
  `price` = VALUES(`price`),
  `version` = `product`.`version`,
  `is_deleted` = VALUES(`is_deleted`);
