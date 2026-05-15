-- MVP 推荐官确认：订单保存下单时推荐官快照，避免后续用户更换推荐关系影响历史订单佣金。
ALTER TABLE `order_master`
  ADD COLUMN `recommender_user_id` bigint DEFAULT NULL COMMENT '下单时一级推荐官快照' AFTER `user_id`,
  ADD COLUMN `level2_recommender_user_id` bigint DEFAULT NULL COMMENT '下单时二级推荐官快照' AFTER `recommender_user_id`,
  ADD KEY `idx_recommender_user_id` (`recommender_user_id`);
