
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `app_notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipient_user_id` bigint NOT NULL,
  `notification_type` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_key` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lead_id` bigint DEFAULT NULL,
  `project_record_id` bigint DEFAULT NULL,
  `assigned_by_user_id` bigint DEFAULT NULL,
  `title` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_notification_reference` (`recipient_user_id`,`reference_key`),
  KEY `fk_app_notification_lead` (`lead_id`),
  KEY `fk_app_notification_actor` (`assigned_by_user_id`),
  KEY `idx_app_notification_recipient` (`recipient_user_id`,`is_read`,`created_at`),
  KEY `idx_app_notification_project_record` (`project_record_id`),
  CONSTRAINT `fk_app_notification_actor` FOREIGN KEY (`assigned_by_user_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_app_notification_lead` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`),
  CONSTRAINT `fk_app_notification_project_record` FOREIGN KEY (`project_record_id`) REFERENCES `business_records` (`id`),
  CONSTRAINT `fk_app_notification_recipient` FOREIGN KEY (`recipient_user_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `app_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `app_users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `legacy_record_id` bigint DEFAULT NULL,
  `resource_id` bigint DEFAULT NULL,
  `manager_user_id` bigint DEFAULT NULL,
  `name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `designation` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Active',
  `module_access` text COLLATE utf8mb4_unicode_ci,
  `reset_token_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reset_token_expires_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_users_email` (`email`),
  UNIQUE KEY `uk_app_users_legacy_record` (`legacy_record_id`),
  UNIQUE KEY `uk_app_users_resource_id` (`resource_id`),
  KEY `idx_app_users_role_status` (`role_name`,`status`),
  KEY `idx_app_users_reset_token` (`reset_token_hash`),
  KEY `idx_app_users_manager` (`manager_user_id`),
  CONSTRAINT `fk_app_users_legacy_record` FOREIGN KEY (`legacy_record_id`) REFERENCES `business_records` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_app_users_manager` FOREIGN KEY (`manager_user_id`) REFERENCES `app_users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_app_users_resource` FOREIGN KEY (`resource_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `asset_id` bigint NOT NULL,
  `resource_id` bigint NOT NULL,
  `resource_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_email` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignment_date` date NOT NULL,
  `expected_return_date` date DEFAULT NULL,
  `actual_return_date` date DEFAULT NULL,
  `purpose` text COLLATE utf8mb4_unicode_ci,
  `cost_center` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_asset_assignment_asset` (`asset_id`),
  KEY `idx_assignment_asset_active` (`organization_id`,`asset_id`,`status`),
  KEY `idx_assignment_resource` (`organization_id`,`resource_id`,`status`),
  CONSTRAINT `fk_asset_assignment_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `attachment_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ASSET',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `uploaded_by` bigint DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_asset_attachment` (`asset_id`,`attachment_type`),
  KEY `idx_asset_attachment_active` (`asset_id`,`active`,`created_at`),
  KEY `fk_asset_attachment_uploaded_by` (`uploaded_by`),
  KEY `fk_asset_attachment_deleted_by` (`deleted_by`),
  CONSTRAINT `fk_asset_attachment_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_asset_attachment_deleted_by` FOREIGN KEY (`deleted_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_asset_attachment_uploaded_by` FOREIGN KEY (`uploaded_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `category_code` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `default_depreciation_method` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STRAIGHT_LINE',
  `useful_life_years` int NOT NULL DEFAULT '3',
  `residual_value_percentage` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `depreciation_frequency` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MONTHLY',
  `default_asset_condition` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GOOD',
  `display_color` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#ef1f2c',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_category_code` (`organization_id`,`category_code`),
  UNIQUE KEY `uk_asset_category_name` (`organization_id`,`category_name`),
  KEY `idx_asset_category_status` (`organization_id`,`status`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_depreciation_entries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_depreciation_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `schedule_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `financial_year` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_number` int NOT NULL,
  `period_label` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `opening_book_value` decimal(18,2) NOT NULL,
  `depreciation_amount` decimal(18,2) NOT NULL,
  `accumulated_depreciation` decimal(18,2) NOT NULL,
  `closing_book_value` decimal(18,2) NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `posted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_depreciation_period` (`schedule_id`,`period_number`),
  KEY `idx_depreciation_entry_asset` (`asset_id`,`start_date`,`status`),
  CONSTRAINT `fk_depreciation_entry_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`),
  CONSTRAINT `fk_depreciation_entry_schedule` FOREIGN KEY (`schedule_id`) REFERENCES `asset_depreciation_schedules` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_depreciation_schedules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_depreciation_schedules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `asset_id` bigint NOT NULL,
  `method` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `financial_year` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `frequency` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `number_of_periods` int NOT NULL,
  `residual_value_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FIXED_AMOUNT',
  `residual_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `depreciable_amount` decimal(18,2) NOT NULL,
  `depreciation_per_period` decimal(18,2) NOT NULL,
  `depreciation_expense_account` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `accumulated_depreciation_account` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pro_rata_convention` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FULL_MONTH',
  `depreciate_in_purchase_month` tinyint(1) NOT NULL DEFAULT '1',
  `include_in_run` tinyint(1) NOT NULL DEFAULT '1',
  `description` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `last_run_at` datetime(6) DEFAULT NULL,
  `next_run_date` date DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_depreciation_schedule` (`organization_id`,`status`,`next_run_date`,`deleted`),
  KEY `idx_depreciation_asset` (`asset_id`,`start_date`),
  CONSTRAINT `fk_depreciation_schedule_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_disposals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_disposals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `disposal_number` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_id` bigint NOT NULL,
  `disposal_date` date NOT NULL,
  `disposal_method` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `buyer_vendor_id` bigint DEFAULT NULL,
  `buyer_vendor_name` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gain_loss_account` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `depreciation_method` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `accumulated_depreciation` decimal(18,2) NOT NULL DEFAULT '0.00',
  `net_book_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `disposal_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `gain_loss_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `gain_loss_percentage` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `remarks` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `approved_by` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `approval_notes` text COLLATE utf8mb4_unicode_ci,
  `completed_at` datetime(6) DEFAULT NULL,
  `reversed_at` datetime(6) DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_disposal_number` (`organization_id`,`disposal_number`),
  KEY `fk_disposal_buyer` (`buyer_vendor_id`),
  KEY `idx_disposal_status` (`organization_id`,`status`,`disposal_date`,`deleted`),
  KEY `idx_disposal_asset` (`asset_id`,`status`),
  CONSTRAINT `fk_disposal_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`),
  CONSTRAINT `fk_disposal_buyer` FOREIGN KEY (`buyer_vendor_id`) REFERENCES `vendors` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `asset_id` bigint NOT NULL,
  `event_type` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `from_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `to_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_type` varchar(48) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_asset_history` (`asset_id`,`created_at`),
  KEY `idx_asset_history_org` (`organization_id`,`event_type`,`created_at`),
  CONSTRAINT `fk_asset_history_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `asset_maintenance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_maintenance` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `work_order_number` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_id` bigint NOT NULL,
  `maintenance_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `checklist_json` longtext COLLATE utf8mb4_unicode_ci,
  `technician_resource_id` bigint DEFAULT NULL,
  `technician_name` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assistant_resource_id` bigint DEFAULT NULL,
  `assistant_name` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scheduled_date` date NOT NULL,
  `due_date` date NOT NULL,
  `completed_date` date DEFAULT NULL,
  `estimated_duration_hours` decimal(10,2) DEFAULT NULL,
  `actual_duration_hours` decimal(10,2) DEFAULT NULL,
  `estimated_cost` decimal(18,2) NOT NULL DEFAULT '0.00',
  `actual_cost` decimal(18,2) NOT NULL DEFAULT '0.00',
  `parts_cost` decimal(18,2) NOT NULL DEFAULT '0.00',
  `labour_cost` decimal(18,2) NOT NULL DEFAULT '0.00',
  `repeat_frequency` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `next_due_date` date DEFAULT NULL,
  `priority` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_maintenance_number` (`organization_id`,`work_order_number`),
  KEY `idx_maintenance_due` (`organization_id`,`status`,`due_date`,`deleted`),
  KEY `idx_maintenance_asset` (`asset_id`,`scheduled_date`),
  CONSTRAINT `fk_maintenance_asset` FOREIGN KEY (`asset_id`) REFERENCES `assets` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `assets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `asset_number` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category_id` bigint NOT NULL,
  `sub_category` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `asset_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TANGIBLE',
  `asset_owner` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `brand` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `serial_number` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `barcode` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` decimal(14,3) NOT NULL DEFAULT '1.000',
  `unit` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `asset_condition` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GOOD',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `vendor_id` bigint DEFAULT NULL,
  `vendor_name` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchase_date` date DEFAULT NULL,
  `invoice_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `po_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchase_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `payment_method` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `warranty_expiry` date DEFAULT NULL,
  `location_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `floor_room` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cost_center` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_resource_id` bigint DEFAULT NULL,
  `assigned_resource_name` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ownership_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OWNED',
  `lease_start_date` date DEFAULT NULL,
  `lease_end_date` date DEFAULT NULL,
  `manufacturer` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manufacture_year` int DEFAULT NULL,
  `country_of_origin` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hsn_sac_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `useful_life_months` int DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `depreciation_method` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `depreciation_start_date` date DEFAULT NULL,
  `depreciation_frequency` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `residual_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `scrap_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `capitalization_date` date DEFAULT NULL,
  `current_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `accumulated_depreciation` decimal(18,2) NOT NULL DEFAULT '0.00',
  `net_book_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `image_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_draft` tinyint(1) NOT NULL DEFAULT '0',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_number` (`organization_id`,`asset_number`),
  KEY `fk_asset_category` (`category_id`),
  KEY `fk_asset_vendor` (`vendor_id`),
  KEY `idx_asset_search` (`organization_id`,`status`,`category_id`,`deleted`),
  KEY `idx_asset_purchase_date` (`organization_id`,`purchase_date`),
  KEY `idx_asset_assignment` (`organization_id`,`assigned_resource_id`,`status`),
  KEY `idx_asset_serial` (`organization_id`,`serial_number`),
  KEY `idx_assets_owner` (`organization_id`,`asset_owner`,`deleted`),
  CONSTRAINT `fk_asset_category` FOREIGN KEY (`category_id`) REFERENCES `asset_categories` (`id`),
  CONSTRAINT `fk_asset_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bank_account_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bank_account_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `account_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bank_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_holder_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_number` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_type` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ifsc_code` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `branch_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency_code` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR',
  `opening_balance` decimal(19,2) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `notes` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'System',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'System',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bank_account_org_name` (`organization_id`,`account_name`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bill_activities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bill_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bill_id` bigint NOT NULL,
  `action` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `details` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `performed_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_bill_activities_bill` (`bill_id`,`created_at`),
  CONSTRAINT `fk_bill_activities_bill` FOREIGN KEY (`bill_id`) REFERENCES `bills` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bill_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bill_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bill_id` bigint NOT NULL,
  `item_id` bigint NOT NULL,
  `item_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_sku` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `item_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hsn_code` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sac_code` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` decimal(18,4) NOT NULL,
  `unit` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rate` decimal(18,2) NOT NULL,
  `discount_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `discount_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `tax_id` bigint DEFAULT NULL,
  `tax_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tax_rate` decimal(10,4) NOT NULL DEFAULT '0.0000',
  `taxable_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `sgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `igst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cess_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `line_total` decimal(18,2) NOT NULL DEFAULT '0.00',
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_bill_items_bill` (`bill_id`,`sort_order`),
  KEY `idx_bill_items_master` (`item_id`),
  KEY `fk_bill_items_tax` (`tax_id`),
  CONSTRAINT `fk_bill_items_bill` FOREIGN KEY (`bill_id`) REFERENCES `bills` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_bill_items_tax` FOREIGN KEY (`tax_id`) REFERENCES `tax_rates` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bill_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bill_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bill_id` bigint NOT NULL,
  `idempotency_key` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_date` date NOT NULL,
  `payment_mode` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `paid_through` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bank_account_id` bigint DEFAULT NULL,
  `to_account` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_number` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` decimal(18,2) NOT NULL,
  `notes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PAID',
  `reversed_at` datetime DEFAULT NULL,
  `reversed_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reversal_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bill_payments_number` (`payment_number`),
  UNIQUE KEY `uk_bill_payments_idempotency` (`idempotency_key`),
  KEY `idx_bill_payments_bill` (`bill_id`,`payment_date`),
  KEY `idx_bill_payment_bank_account` (`bank_account_id`),
  CONSTRAINT `fk_bill_payment_bank_account` FOREIGN KEY (`bank_account_id`) REFERENCES `bank_account_master` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_bill_payments_bill` FOREIGN KEY (`bill_id`) REFERENCES `bills` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bills`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bills` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `bill_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vendor_id` bigint DEFAULT NULL,
  `vendor_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bill_date` date NOT NULL,
  `due_date` date NOT NULL,
  `reference_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchase_order_id` bigint DEFAULT NULL,
  `purchase_order_number` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_terms` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `place_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `destination_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gst_treatment` varchar(48) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR',
  `exchange_rate` decimal(18,6) NOT NULL DEFAULT '1.000000',
  `subject` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TAX_EXCLUSIVE',
  `subtotal` decimal(18,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `taxable_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `sgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `igst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cess_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `shipping_charge` decimal(18,2) NOT NULL DEFAULT '0.00',
  `adjustment_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `round_off_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `amount_paid` decimal(18,2) NOT NULL DEFAULT '0.00',
  `balance_due` decimal(18,2) NOT NULL DEFAULT '0.00',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `terms_and_conditions` text COLLATE utf8mb4_unicode_ci,
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `expected_payment_date` date DEFAULT NULL,
  `opened_at` datetime DEFAULT NULL,
  `opened_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `voided_at` datetime DEFAULT NULL,
  `voided_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `void_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bills_org_number` (`organization_id`,`bill_number`),
  KEY `idx_bills_vendor` (`organization_id`,`vendor_id`),
  KEY `idx_bills_dates` (`organization_id`,`bill_date`,`due_date`),
  KEY `idx_bills_status` (`organization_id`,`status`,`deleted`),
  KEY `idx_bills_reference` (`organization_id`,`reference_number`),
  KEY `fk_bills_vendor` (`vendor_id`),
  KEY `fk_bills_po` (`purchase_order_id`),
  KEY `fk_bills_place` (`place_of_supply`),
  KEY `fk_bills_source` (`source_of_supply`),
  KEY `fk_bills_destination` (`destination_of_supply`),
  CONSTRAINT `fk_bills_destination` FOREIGN KEY (`destination_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_bills_place` FOREIGN KEY (`place_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_bills_po` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`),
  CONSTRAINT `fk_bills_source` FOREIGN KEY (`source_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_bills_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `business_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `business_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `party_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `party_email` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `party_phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `party_city` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `secondary_status` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `balance_amount` decimal(14,2) NOT NULL DEFAULT '0.00',
  `record_date` date NOT NULL,
  `due_date` date DEFAULT NULL,
  `closed_date` date DEFAULT NULL,
  `reference_number` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_mode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `owner_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `department` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `designation` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reporting_manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business_records_record_number` (`record_number`),
  KEY `idx_business_records_module_type` (`module`,`type`),
  KEY `idx_business_records_status` (`status`),
  KEY `idx_business_records_record_date` (`record_date`),
  KEY `idx_business_records_party_name` (`party_name`),
  KEY `idx_business_records_created_by` (`created_by`),
  KEY `idx_business_records_module_type_creator` (`module`,`type`,`created_by`),
  KEY `fk_business_records_updated_by` (`updated_by`),
  KEY `idx_business_records_resource_department` (`module`,`type`,`department`,`status`),
  KEY `idx_business_records_reporting_manager` (`reporting_manager_id`),
  KEY `idx_business_records_project_closed_date` (`module`,`type`,`closed_date`),
  CONSTRAINT `fk_business_records_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_business_records_reporting_manager` FOREIGN KEY (`reporting_manager_id`) REFERENCES `business_records` (`id`),
  CONSTRAINT `fk_business_records_updated_by` FOREIGN KEY (`updated_by`) REFERENCES `app_users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=97 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `company_contact_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `company_contact_mapping` (
  `company_id` bigint NOT NULL,
  `contact_id` bigint NOT NULL,
  `primary_contact` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`company_id`,`contact_id`),
  KEY `fk_ccm_contact` (`contact_id`),
  CONSTRAINT `fk_ccm_company` FOREIGN KEY (`company_id`) REFERENCES `lead_companies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ccm_contact` FOREIGN KEY (`contact_id`) REFERENCES `lead_contacts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `currency_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `currency_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `symbol` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decimal_places` int NOT NULL DEFAULT '2',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency_master_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `document_number_preferences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_number_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `auto_generate` bit(1) NOT NULL DEFAULT b'1',
  `prefix` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `suffix` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number_separator` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number_format` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `starting_number` bigint NOT NULL,
  `next_number` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_number_preferences_document_type` (`document_type`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `domain_industry_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `domain_industry_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int NOT NULL DEFAULT '0',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_domain_industry_org_name` (`organization_id`,`name`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `exchange_rate_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_rate_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_currency` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_currency` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rate` decimal(20,10) NOT NULL,
  `effective_date` date NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exchange_rate_effective` (`from_currency`,`to_currency`,`effective_date`),
  KEY `fk_exchange_rate_to_currency` (`to_currency`),
  KEY `idx_exchange_rate_lookup` (`from_currency`,`to_currency`,`active`,`effective_date`),
  CONSTRAINT `fk_exchange_rate_from_currency` FOREIGN KEY (`from_currency`) REFERENCES `currency_master` (`code`),
  CONSTRAINT `fk_exchange_rate_to_currency` FOREIGN KEY (`to_currency`) REFERENCES `currency_master` (`code`),
  CONSTRAINT `chk_exchange_rate_positive` CHECK ((`rate` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `expense_account_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expense_account_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `account_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'System',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'System',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expense_account_org_name` (`organization_id`,`account_name`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `expenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expenses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `expense_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expense_date` date NOT NULL,
  `expense_account` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expense_account_id` bigint DEFAULT NULL,
  `expense_title` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expense_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vendor_id` bigint DEFAULT NULL,
  `invoice_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hsn_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sac_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gst_treatment` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `destination_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tax_id` bigint DEFAULT NULL,
  `tax_rate` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `tax_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `amount_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entered_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `taxable_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `cgst_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `sgst_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `igst_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `cess_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `total_tax_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `total_amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `tds_deducted` decimal(16,2) NOT NULL DEFAULT '0.00',
  `currency` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR - Indian Rupee',
  `reference_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_mode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paid_through` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_account_id` bigint DEFAULT NULL,
  `project_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PAID',
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_expenses_organization_number` (`organization_id`,`expense_number`),
  UNIQUE KEY `uk_expenses_vendor_invoice` (`organization_id`,`vendor_id`,`invoice_number`),
  KEY `idx_expenses_organization_date` (`organization_id`,`expense_date`),
  KEY `idx_expenses_organization_vendor` (`organization_id`,`vendor_id`),
  KEY `idx_expenses_organization_status` (`organization_id`,`status`),
  KEY `idx_expenses_gst_treatment` (`gst_treatment`),
  KEY `idx_expenses_invoice_number` (`invoice_number`),
  KEY `fk_expenses_vendor` (`vendor_id`),
  KEY `fk_expenses_tax_rate` (`tax_id`),
  KEY `fk_expenses_source_state` (`source_of_supply`),
  KEY `fk_expenses_destination_state` (`destination_of_supply`),
  KEY `idx_expense_account_master` (`expense_account_id`),
  KEY `idx_expense_bank_account` (`bank_account_id`),
  CONSTRAINT `fk_expense_account_master` FOREIGN KEY (`expense_account_id`) REFERENCES `expense_account_master` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expense_bank_account` FOREIGN KEY (`bank_account_id`) REFERENCES `bank_account_master` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_expenses_destination_state` FOREIGN KEY (`destination_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_expenses_source_state` FOREIGN KEY (`source_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_expenses_tax_rate` FOREIGN KEY (`tax_id`) REFERENCES `tax_rates` (`id`),
  CONSTRAINT `fk_expenses_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fixed_cost_project_milestone`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fixed_cost_project_milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `milestone_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `due_date` date NOT NULL,
  `amount` decimal(16,2) NOT NULL DEFAULT '0.00',
  `weightage` decimal(7,2) NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLANNED',
  `progress` decimal(7,2) NOT NULL DEFAULT '0.00',
  `notes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `invoice_id` bigint DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_milestone_name` (`project_id`,`milestone_name`),
  KEY `fk_milestone_invoice` (`invoice_id`),
  KEY `idx_milestone_project_active` (`project_id`,`active`),
  KEY `idx_milestone_due_date` (`due_date`),
  CONSTRAINT `fk_milestone_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`),
  CONSTRAINT `fk_milestone_project` FOREIGN KEY (`project_id`) REFERENCES `business_records` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fixed_cost_project_team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fixed_cost_project_team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `role_designation` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `added_date` date NOT NULL,
  `notes` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignment_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `removed_date` date DEFAULT NULL,
  `removed_by` bigint DEFAULT NULL,
  `removal_note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_fixed_project_team_employee` (`employee_id`),
  KEY `idx_project_team_status` (`project_id`,`assignment_status`),
  KEY `idx_project_team_employee_history` (`project_id`,`employee_id`,`added_date`),
  CONSTRAINT `fk_fixed_project_team_employee` FOREIGN KEY (`employee_id`) REFERENCES `business_records` (`id`),
  CONSTRAINT `fk_fixed_project_team_project` FOREIGN KEY (`project_id`) REFERENCES `business_records` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_communication_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_communication_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,
  `communication_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient` varchar(320) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cc` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bcc` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `subject` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body` text COLLATE utf8mb4_unicode_ci,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `delivery_status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_invoice_communication_invoice` (`invoice_id`),
  KEY `idx_invoice_communication_schedule` (`delivery_status`,`scheduled_at`),
  CONSTRAINT `fk_invoice_communication_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_counters`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_counters` (
  `counter_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `next_value` bigint NOT NULL,
  PRIMARY KEY (`counter_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_credit_allocations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_credit_allocations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_reference` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `applied_on` date NOT NULL,
  `reversed` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_credit_allocation_source` (`invoice_id`,`source_type`,`source_reference`),
  KEY `idx_credit_allocation_invoice` (`invoice_id`),
  CONSTRAINT `fk_credit_allocation_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_credit_note_links`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_credit_note_links` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,
  `credit_note_id` bigint NOT NULL,
  `amount_applied` decimal(19,2) NOT NULL DEFAULT '0.00',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_credit_note_link` (`invoice_id`,`credit_note_id`),
  KEY `fk_credit_note_link_credit_note` (`credit_note_id`),
  CONSTRAINT `fk_credit_note_link_credit_note` FOREIGN KEY (`credit_note_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_credit_note_link_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_lifecycles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_lifecycles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,
  `lifecycle_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `sent_at` datetime(6) DEFAULT NULL,
  `sent_recipient` varchar(320) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `first_viewed_at` datetime(6) DEFAULT NULL,
  `last_viewed_at` datetime(6) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `voided_at` datetime(6) DEFAULT NULL,
  `void_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cash_amount_paid` decimal(19,2) NOT NULL DEFAULT '0.00',
  `tds_settled` decimal(19,2) NOT NULL DEFAULT '0.00',
  `credit_applied` decimal(19,2) NOT NULL DEFAULT '0.00',
  `credit_note_applied` decimal(19,2) NOT NULL DEFAULT '0.00',
  `balance_due` decimal(19,2) NOT NULL DEFAULT '0.00',
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_lifecycle_invoice` (`invoice_id`),
  KEY `idx_invoice_lifecycle_status` (`lifecycle_status`),
  CONSTRAINT `fk_invoice_lifecycle_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_payment_allocations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_payment_allocations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  `cash_amount_applied` decimal(19,2) NOT NULL DEFAULT '0.00',
  `tds_amount_applied` decimal(19,2) NOT NULL DEFAULT '0.00',
  `credit_amount_applied` decimal(19,2) NOT NULL DEFAULT '0.00',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_payment_allocation` (`payment_id`,`invoice_id`),
  KEY `idx_payment_allocation_invoice` (`invoice_id`),
  CONSTRAINT `fk_payment_allocation_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_payment_allocation_payment` FOREIGN KEY (`payment_id`) REFERENCES `invoice_payments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `primary_invoice_id` bigint NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `payment_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_code` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR',
  `payment_date` date NOT NULL,
  `payment_mode` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deposit_account` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_account_id` bigint DEFAULT NULL,
  `bank_account_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `masked_account_number` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cheque_number` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cheque_date` date DEFAULT NULL,
  `gross_amount_received` decimal(19,2) NOT NULL DEFAULT '0.00',
  `bank_charges` decimal(19,2) NOT NULL DEFAULT '0.00',
  `net_bank_credit` decimal(19,2) NOT NULL DEFAULT '0.00',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `attachment_url` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `send_thank_you_email` tinyint(1) NOT NULL DEFAULT '0',
  `reconciled` tinyint(1) NOT NULL DEFAULT '0',
  `reversed` tinyint(1) NOT NULL DEFAULT '0',
  `reversed_at` datetime(6) DEFAULT NULL,
  `reversal_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_payment_number` (`payment_number`),
  UNIQUE KEY `uk_invoice_payment_idempotency` (`idempotency_key`),
  KEY `idx_invoice_payment_invoice` (`primary_invoice_id`),
  KEY `idx_invoice_payment_customer_date` (`customer_id`,`payment_date`),
  KEY `idx_invoice_payment_bank_account` (`bank_account_id`),
  CONSTRAINT `fk_invoice_payment_bank_account` FOREIGN KEY (`bank_account_id`) REFERENCES `bank_account_master` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_payment_invoice` FOREIGN KEY (`primary_invoice_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_project_links`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_project_links` (
  `invoice_id` bigint NOT NULL,
  `project_id` bigint NOT NULL,
  `project_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`invoice_id`),
  KEY `fk_invoice_project_link_created_by` (`created_by`),
  KEY `idx_invoice_project_link_project` (`project_id`,`project_type`),
  CONSTRAINT `fk_invoice_project_link_created_by` FOREIGN KEY (`created_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_invoice_project_link_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_invoice_project_link_project` FOREIGN KEY (`project_id`) REFERENCES `business_records` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_reminder_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_reminder_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_id` bigint NOT NULL,
  `channel` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient` varchar(320) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `scheduled_at` datetime(6) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `delivery_status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_invoice_reminder_invoice` (`invoice_id`),
  KEY `idx_invoice_reminder_schedule` (`delivery_status`,`scheduled_at`),
  CONSTRAINT `fk_invoice_reminder_invoice` FOREIGN KEY (`invoice_id`) REFERENCES `business_records` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `invoice_tds_deductions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invoice_tds_deductions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_id` bigint NOT NULL,
  `base_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `base_amount` decimal(19,2) NOT NULL DEFAULT '0.00',
  `percentage` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `amount` decimal(19,2) NOT NULL DEFAULT '0.00',
  `section_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `certificate_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `certificate_date` date DEFAULT NULL,
  `remarks` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_tds_payment` (`payment_id`),
  CONSTRAINT `fk_invoice_tds_payment` FOREIGN KEY (`payment_id`) REFERENCES `invoice_payments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `item_category_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `item_category_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `category_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active` bit(1) NOT NULL DEFAULT b'1',
  `display_order` int NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'System',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'System',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_item_category_org_name` (`organization_id`,`category_name`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_activities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `entity_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `activity_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `vendor_profile` tinyint(1) NOT NULL DEFAULT '0',
  `vendor_id` bigint DEFAULT NULL,
  `occurred_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lead_activity_entity` (`organization_id`,`entity_type`,`entity_id`,`occurred_at`),
  KEY `idx_lead_activity_report` (`organization_id`,`activity_type`,`occurred_at`,`entity_type`,`entity_id`),
  KEY `idx_lead_activity_vendor` (`vendor_id`),
  CONSTRAINT `fk_lead_activity_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_assignment_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_assignment_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lead_id` bigint NOT NULL,
  `previous_assignee_id` bigint DEFAULT NULL,
  `new_assignee_id` bigint NOT NULL,
  `assigned_by_user_id` bigint NOT NULL,
  `assignment_note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_lead_assignment_history_previous_user` (`previous_assignee_id`),
  KEY `fk_lead_assignment_history_new_user` (`new_assignee_id`),
  KEY `fk_lead_assignment_history_actor` (`assigned_by_user_id`),
  KEY `idx_lead_assignment_history_lead` (`lead_id`,`assigned_at`),
  CONSTRAINT `fk_lead_assignment_history_actor` FOREIGN KEY (`assigned_by_user_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_lead_assignment_history_lead` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`),
  CONSTRAINT `fk_lead_assignment_history_new_user` FOREIGN KEY (`new_assignee_id`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_lead_assignment_history_previous_user` FOREIGN KEY (`previous_assignee_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `entity_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `activity_id` bigint DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lead_attachment_entity` (`organization_id`,`entity_type`,`entity_id`,`deleted`),
  KEY `idx_lead_attachment_activity` (`activity_id`,`deleted`),
  CONSTRAINT `fk_lead_attachment_activity` FOREIGN KEY (`activity_id`) REFERENCES `lead_activities` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `entity_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `action` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `old_values_json` longtext COLLATE utf8mb4_unicode_ci,
  `new_values_json` longtext COLLATE utf8mb4_unicode_ci,
  `performed_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `performed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lead_audit_entity` (`organization_id`,`entity_type`,`entity_id`,`performed_at`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_companies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `company_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `website` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `other_phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fax` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gstin` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pan` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency_code` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR',
  `ownership` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `employee_range` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `annual_revenue` decimal(18,2) DEFAULT NULL,
  `lead_source` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `description` text COLLATE utf8mb4_unicode_ci,
  `primary_contact_id` bigint DEFAULT NULL,
  `street_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_line2` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `country` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `postal_code` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `linkedin_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `facebook_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `twitter_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `owner_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_company_name` (`organization_id`,`company_name`),
  KEY `idx_lead_company_search` (`organization_id`,`status`,`owner_id`,`deleted`),
  KEY `idx_lead_company_city` (`organization_id`,`country`,`city`),
  KEY `fk_lead_company_primary_contact` (`primary_contact_id`),
  CONSTRAINT `fk_lead_company_primary_contact` FOREIGN KEY (`primary_contact_id`) REFERENCES `lead_contacts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_contact_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_contact_mapping` (
  `lead_id` bigint NOT NULL,
  `contact_id` bigint NOT NULL,
  `primary_contact` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`lead_id`,`contact_id`),
  KEY `fk_lcm_contact` (`contact_id`),
  CONSTRAINT `fk_lcm_contact` FOREIGN KEY (`contact_id`) REFERENCES `lead_contacts` (`id`),
  CONSTRAINT `fk_lcm_lead` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_contacts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_contacts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `first_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `job_title` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `other_phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `date_of_birth` date DEFAULT NULL,
  `assistant_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reports_to_id` bigint DEFAULT NULL,
  `owner_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lead_source` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER',
  `company_id` bigint DEFAULT NULL,
  `tags` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `street_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address_line2` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `city` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `country` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `postal_code` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `skype_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `linkedin_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `twitter_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_contact_email` (`organization_id`,`email`),
  KEY `fk_lead_contact_reports_to` (`reports_to_id`),
  KEY `idx_lead_contact_search` (`organization_id`,`owner_id`,`status`,`deleted`),
  KEY `idx_lead_contact_company` (`company_id`,`deleted`),
  CONSTRAINT `fk_lead_contact_company` FOREIGN KEY (`company_id`) REFERENCES `lead_companies` (`id`),
  CONSTRAINT `fk_lead_contact_reports_to` FOREIGN KEY (`reports_to_id`) REFERENCES `lead_contacts` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_deals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_deals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `lead_id` bigint NOT NULL,
  `deal_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `pipeline_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `probability` int NOT NULL DEFAULT '0',
  `expected_close_date` date DEFAULT NULL,
  `owner_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_deal_lead` (`lead_id`),
  KEY `fk_deal_pipeline` (`pipeline_id`),
  KEY `fk_deal_stage` (`stage_id`),
  KEY `idx_deal_access` (`organization_id`,`owner_id`,`status`,`deleted`),
  CONSTRAINT `fk_deal_lead` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`),
  CONSTRAINT `fk_deal_pipeline` FOREIGN KEY (`pipeline_id`) REFERENCES `lead_pipelines` (`id`),
  CONSTRAINT `fk_deal_stage` FOREIGN KEY (`stage_id`) REFERENCES `pipeline_stages` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `entity_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lead_note_entity` (`organization_id`,`entity_type`,`entity_id`,`deleted`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_pipelines`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_pipelines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `name` varchar(140) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `owner_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_code` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR',
  `expected_close_date` date DEFAULT NULL,
  `pipeline_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SALES',
  `visibility` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL_USERS',
  `default_probability` int NOT NULL DEFAULT '10',
  `color` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#ef1f2c',
  `allow_duplicate_stages` tinyint(1) NOT NULL DEFAULT '0',
  `auto_probability` tinyint(1) NOT NULL DEFAULT '1',
  `require_stage_age` tinyint(1) NOT NULL DEFAULT '0',
  `require_lost_reason` tinyint(1) NOT NULL DEFAULT '1',
  `allow_closed_edit` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `archived` tinyint(1) NOT NULL DEFAULT '0',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_pipeline_name` (`organization_id`,`name`),
  KEY `idx_lead_pipeline_access` (`organization_id`,`owner_id`,`active`,`deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_stage_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_stage_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lead_id` bigint NOT NULL,
  `from_stage_id` bigint DEFAULT NULL,
  `to_stage_id` bigint NOT NULL,
  `previous_probability` int DEFAULT NULL,
  `new_probability` int DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `changed_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `changed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `fk_lsh_from` (`from_stage_id`),
  KEY `fk_lsh_to` (`to_stage_id`),
  KEY `idx_lsh_lead` (`lead_id`,`changed_at`),
  CONSTRAINT `fk_lsh_from` FOREIGN KEY (`from_stage_id`) REFERENCES `pipeline_stages` (`id`),
  CONSTRAINT `fk_lsh_lead` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`),
  CONSTRAINT `fk_lsh_to` FOREIGN KEY (`to_stage_id`) REFERENCES `pipeline_stages` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `name` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `color` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#2563eb',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_tag` (`organization_id`,`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `entity_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` bigint NOT NULL,
  `title` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `due_date` datetime(6) DEFAULT NULL,
  `priority` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `assigned_user` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `reminder_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_lead_task_entity` (`organization_id`,`entity_type`,`entity_id`,`status`,`due_date`),
  KEY `idx_lead_task_report` (`organization_id`,`status`,`due_date`,`entity_type`,`entity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lead_user_share`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lead_user_share` (
  `lead_id` bigint NOT NULL,
  `user_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shared_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`lead_id`,`user_id`),
  CONSTRAINT `fk_lead_share` FOREIGN KEY (`lead_id`) REFERENCES `leads` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `leads`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `leads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `lead_number` varchar(48) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lead_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lead_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `primary_skill_id` bigint DEFAULT NULL,
  `secondary_skill` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pipeline_id` bigint NOT NULL,
  `stage_id` bigint NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `source` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `assigned_user_id` bigint DEFAULT NULL,
  `expected_value` decimal(18,2) NOT NULL DEFAULT '0.00',
  `expected_close_date` date DEFAULT NULL,
  `probability` int NOT NULL DEFAULT '0',
  `lead_score` int NOT NULL DEFAULT '0',
  `next_step` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `priority` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `description` text COLLATE utf8mb4_unicode_ci,
  `company_id` bigint DEFAULT NULL,
  `primary_contact_id` bigint DEFAULT NULL,
  `type_details_json` longtext COLLATE utf8mb4_unicode_ci,
  `tags` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `converted_deal_id` bigint DEFAULT NULL,
  `converted_at` datetime(6) DEFAULT NULL,
  `lost_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `draft` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_number` (`organization_id`,`lead_number`),
  KEY `fk_lead_stage` (`stage_id`),
  KEY `fk_lead_primary_contact` (`primary_contact_id`),
  KEY `idx_lead_access` (`organization_id`,`owner_id`,`status`,`deleted`),
  KEY `idx_lead_pipeline_stage` (`pipeline_id`,`stage_id`,`deleted`),
  KEY `idx_lead_created` (`organization_id`,`created_at`),
  KEY `idx_lead_company_contact` (`company_id`,`primary_contact_id`),
  KEY `fk_lead_converted_deal` (`converted_deal_id`),
  KEY `idx_lead_primary_skill` (`organization_id`,`primary_skill_id`,`deleted`),
  KEY `fk_lead_primary_skill` (`primary_skill_id`),
  KEY `idx_lead_report_filters` (`organization_id`,`created_at`,`pipeline_id`,`owner_id`,`lead_type`,`status`,`source`,`deleted`),
  KEY `idx_lead_report_skill` (`organization_id`,`primary_skill_id`,`created_at`,`deleted`),
  KEY `idx_leads_assigned_user` (`assigned_user_id`),
  CONSTRAINT `fk_lead_company` FOREIGN KEY (`company_id`) REFERENCES `lead_companies` (`id`),
  CONSTRAINT `fk_lead_converted_deal` FOREIGN KEY (`converted_deal_id`) REFERENCES `lead_deals` (`id`),
  CONSTRAINT `fk_lead_pipeline` FOREIGN KEY (`pipeline_id`) REFERENCES `lead_pipelines` (`id`),
  CONSTRAINT `fk_lead_primary_contact` FOREIGN KEY (`primary_contact_id`) REFERENCES `lead_contacts` (`id`),
  CONSTRAINT `fk_lead_primary_skill` FOREIGN KEY (`primary_skill_id`) REFERENCES `skill_master` (`id`),
  CONSTRAINT `fk_lead_stage` FOREIGN KEY (`stage_id`) REFERENCES `pipeline_stages` (`id`),
  CONSTRAINT `fk_leads_assigned_user` FOREIGN KEY (`assigned_user_id`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lost_reasons`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lost_reasons` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `pipeline_id` bigint DEFAULT NULL,
  `reason` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `fk_lost_reason_pipeline` (`pipeline_id`),
  KEY `idx_lost_reason` (`organization_id`,`pipeline_id`,`active`),
  CONSTRAINT `fk_lost_reason_pipeline` FOREIGN KEY (`pipeline_id`) REFERENCES `lead_pipelines` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `payment_receipts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_receipts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_id` bigint NOT NULL,
  `receipt_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remaining_balance` decimal(19,2) NOT NULL DEFAULT '0.00',
  `generated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_receipt_payment` (`payment_id`),
  UNIQUE KEY `uk_payment_receipt_number` (`receipt_number`),
  CONSTRAINT `fk_payment_receipt_payment` FOREIGN KEY (`payment_id`) REFERENCES `invoice_payments` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pipeline_stages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pipeline_stages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pipeline_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `probability` int NOT NULL DEFAULT '0',
  `expected_duration_days` int NOT NULL DEFAULT '0',
  `color` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '#2563eb',
  `stage_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `sort_order` int NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pipeline_stage_name` (`pipeline_id`,`name`),
  KEY `idx_pipeline_stage_order` (`pipeline_id`,`active`,`sort_order`),
  CONSTRAINT `fk_pipeline_stage_pipeline` FOREIGN KEY (`pipeline_id`) REFERENCES `lead_pipelines` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pipeline_user_visibility`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pipeline_user_visibility` (
  `pipeline_id` bigint NOT NULL,
  `user_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`pipeline_id`,`user_id`),
  CONSTRAINT `fk_pipeline_visibility` FOREIGN KEY (`pipeline_id`) REFERENCES `lead_pipelines` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `project_documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `project_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `document_type` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PROJECT',
  `sow_version` int DEFAULT NULL,
  `original_file_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_extension` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mime_type` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint NOT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `uploaded_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted_by` bigint DEFAULT NULL,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_project_document_uploaded_by` (`uploaded_by`),
  KEY `fk_project_document_deleted_by` (`deleted_by`),
  KEY `idx_project_documents_lookup` (`project_id`,`project_type`,`active`),
  KEY `idx_project_documents_uploaded_at` (`uploaded_at`),
  CONSTRAINT `fk_project_document_deleted_by` FOREIGN KEY (`deleted_by`) REFERENCES `app_users` (`id`),
  CONSTRAINT `fk_project_document_project` FOREIGN KEY (`project_id`) REFERENCES `business_records` (`id`),
  CONSTRAINT `fk_project_document_uploaded_by` FOREIGN KEY (`uploaded_by`) REFERENCES `app_users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_order_activities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_activities` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_order_id` bigint NOT NULL,
  `action` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `details` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `performed_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_activities_order_date` (`purchase_order_id`,`created_at`),
  CONSTRAINT `fk_purchase_order_activities_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `purchase_order_id` bigint NOT NULL,
  `item_id` bigint DEFAULT NULL,
  `item_name` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_sku` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `item_type` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hsn_code` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sac_code` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` decimal(18,4) NOT NULL,
  `received_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `billed_quantity` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `unit` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rate` decimal(18,4) NOT NULL,
  `discount_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `discount_value` decimal(18,4) NOT NULL DEFAULT '0.0000',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `tax_id` bigint DEFAULT NULL,
  `tax_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tax_rate` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `taxable_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `sgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `igst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cess_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `line_total` decimal(18,2) NOT NULL DEFAULT '0.00',
  `warehouse_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_purchase_order_items_order` (`purchase_order_id`,`sort_order`),
  KEY `idx_purchase_order_items_item` (`item_id`),
  KEY `fk_purchase_order_items_tax` (`tax_id`),
  CONSTRAINT `fk_purchase_order_items_order` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_purchase_order_items_tax` FOREIGN KEY (`tax_id`) REFERENCES `tax_rates` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `purchase_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `purchase_order_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purchase_order_date` date NOT NULL,
  `expected_delivery_date` date DEFAULT NULL,
  `vendor_id` bigint DEFAULT NULL,
  `vendor_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vendor_address_json` text COLLATE utf8mb4_unicode_ci,
  `delivery_address_json` text COLLATE utf8mb4_unicode_ci,
  `delivery_address_source` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_number` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipment_preference` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_terms` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR',
  `exchange_rate` decimal(18,6) NOT NULL DEFAULT '1.000000',
  `gst_treatment` varchar(48) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `destination_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `place_of_supply` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `branch_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `warehouse_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attention` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT',
  `amount_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TAX_EXCLUSIVE',
  `subtotal` decimal(18,2) NOT NULL DEFAULT '0.00',
  `discount_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `taxable_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `sgst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `igst_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `cess_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `shipping_charge` decimal(18,2) NOT NULL DEFAULT '0.00',
  `adjustment_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `round_off_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_tax_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `total_amount` decimal(18,2) NOT NULL DEFAULT '0.00',
  `notes` text COLLATE utf8mb4_unicode_ci,
  `terms_and_conditions` text COLLATE utf8mb4_unicode_ci,
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `linked_bill_id` bigint DEFAULT NULL,
  `linked_bill_number` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issued_at` datetime(6) DEFAULT NULL,
  `issued_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `received_at` datetime(6) DEFAULT NULL,
  `received_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `updated_by` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Admin',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `cancelled_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cancellation_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `closed_by` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `closing_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_purchase_orders_organization_number` (`organization_id`,`purchase_order_number`),
  KEY `idx_purchase_orders_organization_date` (`organization_id`,`purchase_order_date`),
  KEY `idx_purchase_orders_organization_vendor` (`organization_id`,`vendor_id`),
  KEY `idx_purchase_orders_organization_status` (`organization_id`,`status`),
  KEY `idx_purchase_orders_expected_delivery` (`expected_delivery_date`),
  KEY `idx_purchase_orders_reference` (`reference_number`),
  KEY `idx_purchase_orders_created_by` (`created_by`),
  KEY `fk_purchase_orders_vendor` (`vendor_id`),
  KEY `fk_purchase_orders_source_state` (`source_of_supply`),
  KEY `fk_purchase_orders_destination_state` (`destination_of_supply`),
  KEY `fk_purchase_orders_place_state` (`place_of_supply`),
  KEY `idx_purchase_orders_cancelled_at` (`cancelled_at`),
  KEY `idx_purchase_orders_closed_at` (`closed_at`),
  CONSTRAINT `fk_purchase_orders_destination_state` FOREIGN KEY (`destination_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_purchase_orders_place_state` FOREIGN KEY (`place_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_purchase_orders_source_state` FOREIGN KEY (`source_of_supply`) REFERENCES `supply_states` (`code`),
  CONSTRAINT `fk_purchase_orders_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `skill_master`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skill_master` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `skill_name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_skill_master_name` (`organization_id`,`skill_name`),
  KEY `idx_skill_master_lookup` (`organization_id`,`active`,`deleted`,`skill_name`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `staffing_project_sow`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `staffing_project_sow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `staffing_project_id` bigint NOT NULL,
  `sow_version` int NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `document_id` bigint DEFAULT NULL,
  `notes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `current_sow` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staffing_sow_project_version` (`staffing_project_id`,`sow_version`),
  KEY `idx_staffing_sow_current` (`staffing_project_id`,`current_sow`),
  KEY `idx_staffing_sow_end_date` (`end_date`,`current_sow`),
  KEY `fk_staffing_sow_document` (`document_id`),
  CONSTRAINT `fk_staffing_sow_document` FOREIGN KEY (`document_id`) REFERENCES `project_documents` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_staffing_sow_project` FOREIGN KEY (`staffing_project_id`) REFERENCES `business_records` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_staffing_sow_dates` CHECK ((`end_date` >= `start_date`))
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `supply_states`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supply_states` (
  `code` varchar(2) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `territory_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STATE',
  PRIMARY KEY (`code`),
  UNIQUE KEY `uk_supply_states_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `tax_rates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tax_rates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rate` decimal(7,4) NOT NULL DEFAULT '0.0000',
  `tax_category` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TAXABLE',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tax_rates_code` (`code`),
  KEY `idx_tax_rates_active_order` (`active`,`display_order`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `vendor_bank_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vendor_bank_details` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `vendor_id` bigint NOT NULL,
  `account_holder_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `beneficiary_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_number` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ifsc_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `branch_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_type` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `swift_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `iban` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_country` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `upi_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vendor_bank_details_vendor` (`vendor_id`),
  CONSTRAINT `fk_vendor_bank_details_vendor` FOREIGN KEY (`vendor_id`) REFERENCES `vendors` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `vendors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vendors` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `organization_id` bigint NOT NULL DEFAULT '1',
  `vendor_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `vendor_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `company_name` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vendor_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_of_supply` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INR - Indian Rupee',
  `payment_terms` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tax_treatment` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gstin` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pan` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `primary_contact` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(180) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `mobile` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `website` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_address_line1` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_address_line2` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_city` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_state` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_pincode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `billing_country` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_address_line1` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_address_line2` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_city` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_state` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_pincode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_country` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vendors_organization_number` (`organization_id`,`vendor_number`),
  KEY `idx_vendors_organization_status` (`organization_id`,`status`),
  KEY `idx_vendors_organization_name` (`organization_id`,`vendor_name`),
  KEY `idx_vendors_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

