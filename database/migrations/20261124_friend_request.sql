-- Run against user_server before deploying chat-user. Existing friendship/history rows are retained.
CREATE TABLE IF NOT EXISTS `friend_request` (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint UNSIGNED NOT NULL,
  `receiver_id` bigint UNSIGNED NOT NULL,
  `message` varchar(100) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) NOT NULL,
  `handled_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_friend_request_pair` (`applicant_id`, `receiver_id`, `status`),
  KEY `idx_friend_request_incoming` (`receiver_id`, `id`),
  KEY `idx_friend_request_outgoing` (`applicant_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请；终态记录保留，重新申请生成新ID';
