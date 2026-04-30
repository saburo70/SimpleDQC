CREATE TABLE `users` (
   `id` int NOT NULL AUTO_INCREMENT,
   `name` varchar(100) COLLATE utf8mb3_bin DEFAULT NULL,
   `email` varchar(100) COLLATE utf8mb3_bin DEFAULT NULL,
   PRIMARY KEY (`id`)
 );

INSERT INTO `data`.`users` (`id`, `name`) VALUES ('1', 'Marck S.');
INSERT INTO `data`.`users` (`id`, `name`, `email`) VALUES ('2', 'Alice', 'alice@something.com');
INSERT INTO `data`.`users` (`id`, `name`, `email`) VALUES ('3', 'Martha', 'maybe@really.com');
INSERT INTO `data`.`users` (`id`, `name`, `email`) VALUES ('4', 'Bob', 'rober@yup.net');
