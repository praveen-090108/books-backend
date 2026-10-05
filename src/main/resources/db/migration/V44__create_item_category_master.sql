CREATE TABLE item_category_master (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL DEFAULT 1,
    category_name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BIT NOT NULL DEFAULT 1,
    display_order INT NOT NULL DEFAULT 0,
    created_by VARCHAR(120) NOT NULL DEFAULT 'System',
    updated_by VARCHAR(120) NOT NULL DEFAULT 'System',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_category_org_name (organization_id, category_name)
);

INSERT INTO item_category_master
(organization_id, category_name, description, active, display_order, created_by, updated_by, created_at, updated_at) VALUES
(1,'Software Development','Custom software and application development services',1,10,'System','System',NOW(6),NOW(6)),
(1,'Web Development','Website and web application development',1,20,'System','System',NOW(6),NOW(6)),
(1,'Mobile App Development','Native and cross-platform mobile development',1,30,'System','System',NOW(6),NOW(6)),
(1,'UI/UX Design','Product design, user experience and interface services',1,40,'System','System',NOW(6),NOW(6)),
(1,'Cloud Services','Cloud architecture, migration and operations',1,50,'System','System',NOW(6),NOW(6)),
(1,'DevOps Services','CI/CD, automation and platform engineering',1,60,'System','System',NOW(6),NOW(6)),
(1,'Cyber Security','Security assessment, implementation and monitoring',1,70,'System','System',NOW(6),NOW(6)),
(1,'IT Consulting','Technology strategy and consulting services',1,80,'System','System',NOW(6),NOW(6)),
(1,'Managed IT Services','Ongoing managed technology services',1,90,'System','System',NOW(6),NOW(6)),
(1,'Quality Assurance & Testing','Manual and automated quality engineering',1,100,'System','System',NOW(6),NOW(6)),
(1,'Data Analytics','Business intelligence and data analytics services',1,110,'System','System',NOW(6),NOW(6)),
(1,'Artificial Intelligence & Machine Learning','AI and machine-learning solutions',1,120,'System','System',NOW(6),NOW(6)),
(1,'ERP/CRM Implementation','Enterprise platform implementation services',1,130,'System','System',NOW(6),NOW(6)),
(1,'Technical Support','Application and infrastructure support',1,140,'System','System',NOW(6),NOW(6)),
(1,'Staff Augmentation','Technology staffing and resource services',1,150,'System','System',NOW(6),NOW(6)),
(1,'SaaS Subscription','Recurring software-as-a-service offerings',1,160,'System','System',NOW(6),NOW(6)),
(1,'Software Licensing','Software product and license sales',1,170,'System','System',NOW(6),NOW(6)),
(1,'Hosting & Infrastructure','Hosting, compute and infrastructure services',1,180,'System','System',NOW(6),NOW(6)),
(1,'Maintenance & Support','Software maintenance and annual support services',1,190,'System','System',NOW(6),NOW(6)),
(1,'Training & Knowledge Transfer','Technical training and enablement services',1,200,'System','System',NOW(6),NOW(6));

INSERT INTO item_category_master
(organization_id, category_name, description, active, display_order, created_by, updated_by, created_at, updated_at)
SELECT 1, legacy.category, 'Preserved legacy item category', 0, 1000, 'Migration', 'Migration', NOW(6), NOW(6)
FROM (SELECT DISTINCT TRIM(category) category FROM business_records WHERE module='purchases' AND type='items' AND category IS NOT NULL AND TRIM(category)<>'') legacy
WHERE NOT EXISTS (SELECT 1 FROM item_category_master current_category WHERE current_category.organization_id=1 AND LOWER(current_category.category_name)=LOWER(legacy.category));
