CREATE DATABASE user_server;
USE user_server;

CREATE TABLE `users` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
	`account` VARCHAR(20) NOT NULL UNIQUE COMMENT  '账号',
  `phone` varchar(20) NOT NULL UNIQUE COMMENT '手机号',
  `nickname` varchar(50) NOT NULL COMMENT '昵称',
  `password` varchar(255) NOT NULL COMMENT '密码哈希',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '账号状态: 0-异常, 1-正常,2-封禁',
  `last_login_ip` varchar(45) DEFAULT NULL COMMENT '最后登录IP',
  `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';



CREATE TABLE `user_friend` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) UNSIGNED NOT NULL COMMENT '用户ID',
  `friend_id` bigint(20) UNSIGNED NOT NULL COMMENT '好友ID',
  `remark` varchar(50) DEFAULT NULL COMMENT '好友备注(单向)',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态: 1-正常, 0-已删除',
  `is_blocked` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否拉黑: 0-否, 1-是',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  KEY `idx_user_status` (`user_id`, `status`) -- 优化“查询我的正常好友”速度
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户好友关系表(双向存储)';


CREATE DATABASE group_server;
USE group_server;

CREATE TABLE `group_info`(
	`id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '群ID',
	`group_no` VARCHAR(32)  NOT NULL COMMENT '群号（对外展示）',
	`name` VARCHAR(100) NOT NULL COMMENT '群名称',
	`avatar` VARCHAR(500) DEFAULT NULL COMMENT '群头像URL',
	`description` VARCHAR(500) DEFAULT NULL COMMENT '群简介',
	`owner_id` BIGINT UNSIGNED NOT NULL COMMENT '群主用户ID',
	`group_type` TINYINT NOT NULL DEFAULT 0 COMMENT '群类型:0普通群 1讨论组 2频道',
	`join_type` TINYINT NOT NULL DEFAULT 0 COMMENT '加群方式:0自由加入 1需审批 2禁止加入',
	`max_members` INT NOT NULL DEFAULT 500 COMMENT '最大成员数',
	`member_count` INT NOT NULL DEFAULT 1 COMMENT '当前成员数(冗余)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0已解散 1正常 2封禁',
  `mute_all` TINYINT NOT NULL DEFAULT 0 COMMENT '全员禁言:0否 1是',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_no` (`group_no`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群基本信息';

CREATE TABLE `group_member` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `group_no`    VARCHAR(32) NOT NULL COMMENT '群ID',
  `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `role`        TINYINT NOT NULL DEFAULT 0 COMMENT '角色:0成员 1管理员 2群主',
  `nickname`    VARCHAR(50) DEFAULT NULL COMMENT '群内昵称',
  `mute_until`  DATETIME DEFAULT NULL COMMENT '禁言到期时间,NULL为未禁言',
  `join_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入群时间',
  `inviter_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '邀请人ID',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0已退出 1正常 2被踢',
  `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_no`, `user_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_group_role` (`group_no`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员';

CREATE DATABASE message_server;
USE message_server;

CREATE TABLE `user_conversation`(
	`id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话ID',
	`user_id` BIGINT(20) UNSIGNED NOT NULL COMMENT '所属用户',
	`conversation_id` BIGINT(20) UNSIGNED NOT NULL COMMENT '所属回话',
	`is_pinned` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否置顶，0否、1是',
	`pinned_at` DATETIME(3) DEFAULT NULL COMMENT '置顶时间',
	`is_muted` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否免打扰，0否、1是',
	`last_read_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已读到的回话序号',
	`last_read_at` DATETIME(3) DEFAULT NULL COMMENT '已读位置最近一次前进时间',
	`settings_version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '设置版本号，用于防止旧设备覆盖新设置',
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	PRIMARY KEY (`id`),
	UNIQUE KEY `uk_user_conversation` (`user_id`,`conversation_id`),
	KEY `idx_user_pinned` (`user_id`,`is_pinned`,`pinned_at`),
	KEY `idx_user_seq` (`user_id`,`last_read_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '用户回话表';

CREATE TABLE `conversation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '会话编号',
    `conversation_type` TINYINT UNSIGNED NOT NULL COMMENT '会话类型：1单聊，2群聊',
    `user_low_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '单聊中编号较小的用户，群聊为空',
    `user_high_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '单聊中编号较大的用户，群聊为空',
    `group_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联群编号，单聊为空',
    `last_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前已提交消息的最大会话序号，初始为0',
    `last_message_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '最近一条消息的编号，尚无消息时为空',
    `last_message_at` DATETIME(3) DEFAULT NULL COMMENT '最近一条消息的保存时间，尚无消息时为空',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '会话状态：0已关闭，1正常，2封禁',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY uk_conversation_single (`user_low_id`, `user_high_id`),
    UNIQUE KEY uk_conversation_group (`group_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '单聊与群聊的公共会话信息';



CREATE TABLE `message` (
	`id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息ID',
	`conversation_id` BIGINT(20) UNSIGNED NOT NULL COMMENT '回话ID',
	`seq` BIGINT(20) UNSIGNED NOT NULL COMMENT '消息在该回话的顺序',
	`sender_id` BIGINT(20) UNSIGNED NOT NULL COMMENT '发送者ID',
	`client_message_id` VARCHAR(64) NOT NULL COMMENT '客户端发送操作编号：首次发送生成，重试时保持不变',
	`message_type` TINYINT UNSIGNED NOT NULL COMMENT '消息类型，1文字、2图片、3文件',
	`text_content` TEXT COMMENT '文字消息',
	`attachment_id` BIGINT(20) UNSIGNED DEFAULT NULL COMMENT '图片或文件编号',
	`is_recalled` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否已撤回，0未撤回、1已撤回',
	`recalled_at` DATETIME(3) DEFAULT NULL COMMENT '消息撤回时间',
	`is_blocked` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否违规内容',
	`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
	
	PRIMARY KEY(`id`),
	UNIQUE KEY `uk_conversation_seq` (`conversation_id`, `seq`),
	UNIQUE KEY `uk_sender_client_message` (`sender_id`, `client_message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '消息记录表';


CREATE TABLE `message_outbox` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息事件编号',
    `message_id` BIGINT UNSIGNED NOT NULL COMMENT '对应的消息编号',
    `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '所属会话编号',
    `seq` BIGINT UNSIGNED NOT NULL COMMENT '消息在会话中的顺序',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型，例如MESSAGE_CREATED',
    `event_key` VARCHAR(128) NOT NULL COMMENT '事件唯一编号，用于幂等',
    `payload` JSON NOT NULL COMMENT '发送给消息队列的事件内容',
      `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待发送、2已发送、3待重试、4最终失败；历史1按待发送兼容，处理锁由 locked_at/locked_by 表示',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_at` DATETIME(3) DEFAULT NULL COMMENT '下一次重试时间',
    `last_error` VARCHAR(500) DEFAULT NULL COMMENT '最近一次失败原因',
    `locked_at` DATETIME(3) DEFAULT NULL COMMENT '任务锁定时间',
    `locked_by` VARCHAR(64) DEFAULT NULL COMMENT '处理该任务的实例',
    `published_at` DATETIME(3) DEFAULT NULL COMMENT '成功发布时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_event` (`event_type`, `event_key`),
    KEY `idx_outbox_dispatch` (`status`, `next_retry_at`, `id`),
    KEY `idx_outbox_message` (`message_id`),
    KEY `idx_outbox_conversation_seq` (`conversation_id`, `seq`)
) ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci
COMMENT='消息可靠投递事件表';


create table `off_message_box` (
	id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '离线消息id',
	message_id BIGINT UNSIGNED NOT NULL COMMENT '对应的消息编号',
	conversation_id BIGINT UNSIGNED NOT NULL COMMENT '所属会话编号',
	seq BIGINT UNSIGNED NOT NULL COMMENT '消息在会话中的顺序',
	user_id BIGINT(20) UNSIGNED NOT NULL COMMENT '所属用户',
	status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-待拉取 1-已ACK',
	created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
	acked_at DATETIME DEFAULT NULL,
	PRIMARY KEY (`id`)
	UNIQUE KEY `uk_user_msg` (`user_id`,`message_id`),
  KEY `idx_user_conv_status_seq` (`user_id`, `conversation_id`, `status`, `seq`),
  KEY `idx_status_acked` (`status`, `acked_at`),
  KEY `idx_created` (`created_at`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户离线消息收件箱';


	CREATE TABLE device_sync_cursor (
			`id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '记录编号',
			`user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户',
			`device_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登录设备编号',
			`conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '所属会话',
			`last_synced_seq` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '设备已确认完整接收的位置，更新时只能前进',
			`created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
			`updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近更新时间',
			PRIMARY KEY (`id`),
			UNIQUE KEY uk_cursor_user_device_conversation (`user_id`, `device_id`, `conversation_id`),
			KEY idx_cursor_user_conversation (`user_id`, `conversation_id`)
	) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备在各会话中的消息接收进度';

CREATE DATABASE file_server;
USE file_server;
CREATE TABLE file_metadata (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件编号，对应消息中的attachment_id',
    `uploader_id` BIGINT UNSIGNED NOT NULL COMMENT '上传用户编号',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_type` VARCHAR(127) DEFAULT NULL COMMENT '实际检测确认的文件类型，例如image/jpeg',
    `size_bytes` BIGINT UNSIGNED DEFAULT NULL COMMENT '实际文件大小，单位字节，上传完成后填写',
    `storage_bucket` VARCHAR(63) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '文件所在的存储空间名称',
    `storage_key` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台生成的文件存储位置，不保存临时下载地址',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0上传中，1可使用，2清理中，3已删除',
    `is_blocked` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否因违规停止访问：0否，1是',
    `uploaded_at` DATETIME(3) DEFAULT NULL COMMENT '上传完成时间',
    `temporary_expires_at` DATETIME(3) DEFAULT NULL COMMENT '临时到期时间；创建时填24小时后，关联有效消息后清空',
    `deleted_at` DATETIME(3) DEFAULT NULL COMMENT '实际删除完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY uk_file_storage (`storage_bucket`, `storage_key`),
    KEY idx_file_uploader (`uploader_id`, `created_at`, `id`),
    KEY idx_file_temporary_cleanup (`status`, `temporary_expires_at`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '图片和文件的登记信息';




































