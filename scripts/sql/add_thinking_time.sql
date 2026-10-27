-- 凌点AI平台 - 添加思考耗时字段
-- chat_message 表新增 thinking_time 列，记录 AI 回复的思考耗时（秒）
-- 执行方式: docker exec lingdian-mysql mysql -uroot -p<password> lingdian_ai < /path/to/this.sql

ALTER TABLE chat_message
    ADD COLUMN thinking_time DOUBLE DEFAULT NULL COMMENT '思考耗时（秒），仅AI消息有值'
    AFTER metadata;
