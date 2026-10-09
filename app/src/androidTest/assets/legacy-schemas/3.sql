CREATE TABLE IF NOT EXISTS `prompts` (`_id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `content_ru` TEXT NOT NULL, `content_en` TEXT NOT NULL, `variables_json` TEXT NOT NULL, `compatible_models` TEXT NOT NULL, `category` TEXT NOT NULL, `tags` TEXT NOT NULL, `is_local` INTEGER NOT NULL, `is_favorite` INTEGER NOT NULL, `rating` REAL NOT NULL, `rating_votes` INTEGER NOT NULL, `status` TEXT NOT NULL, `author` TEXT NOT NULL, `author_id` TEXT NOT NULL, `source` TEXT NOT NULL, `notes` TEXT NOT NULL, `version` TEXT NOT NULL, `created_at` TEXT NOT NULL, `modified_at` TEXT NOT NULL, PRIMARY KEY(`_id`));
CREATE INDEX IF NOT EXISTS `index_prompts_category` ON `prompts` (`category`);
CREATE INDEX IF NOT EXISTS `index_prompts_is_favorite` ON `prompts` (`is_favorite`);
CREATE INDEX IF NOT EXISTS `index_prompts_is_local` ON `prompts` (`is_local`);
CREATE INDEX IF NOT EXISTS `index_prompts_status` ON `prompts` (`status`);
CREATE INDEX IF NOT EXISTS `index_prompts_title` ON `prompts` (`title`);
CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL, `systemPrompt` TEXT, PRIMARY KEY(`id`));
CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE);
CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages` (`conversationId`);
