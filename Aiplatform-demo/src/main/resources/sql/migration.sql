-- 用户自定义 API Key 表
CREATE TABLE IF NOT EXISTS `user_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `provider` varchar(32) NOT NULL COMMENT '服务商: openai/anthropic/deepseek/moonshot/zhipu/qwen/siliconflow',
  `display_name` varchar(64) DEFAULT NULL COMMENT '显示名称',
  `api_key` varchar(256) NOT NULL COMMENT 'API Key明文',
  `base_url` varchar(256) DEFAULT NULL COMMENT '自定义Base URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户自定义API Key表';
