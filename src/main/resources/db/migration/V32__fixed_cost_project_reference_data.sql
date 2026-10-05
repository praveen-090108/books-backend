CREATE TABLE domain_industry_master (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    updated_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_domain_industry_org_name UNIQUE (organization_id, name)
);

INSERT IGNORE INTO domain_industry_master (organization_id, name, display_order)
VALUES
(1,'Banking',10),(1,'Financial Services',20),(1,'FinTech',30),(1,'Insurance',40),
(1,'Healthcare',50),(1,'HealthTech',60),(1,'Pharmaceutical',70),(1,'Education',80),
(1,'EdTech',90),(1,'E-Commerce',100),(1,'Retail',110),(1,'FMCG',120),
(1,'Food & Beverage',130),(1,'Restaurant / FoodTech',140),(1,'Hospitality',150),
(1,'Travel & Tourism',160),(1,'Real Estate',170),(1,'PropTech',180),(1,'Construction',190),
(1,'Manufacturing',200),(1,'Automotive',210),(1,'Transportation',220),
(1,'Logistics & Supply Chain',230),(1,'Telecommunications',240),(1,'Media & Entertainment',250),
(1,'Gaming',260),(1,'Sports & Fitness',270),(1,'Beauty & Wellness',280),
(1,'Government / Public Sector',290),(1,'Legal / LegalTech',300),(1,'HR / HRTech',310),
(1,'Recruitment',320),(1,'Accounting & Finance',330),(1,'Enterprise Software',340),
(1,'SaaS',350),(1,'Information Technology',360),(1,'IT Services',370),
(1,'Cyber Security',380),(1,'Cloud Computing',390),(1,'Data & Analytics',400),
(1,'Artificial Intelligence / Machine Learning',410),(1,'IoT',420),(1,'Blockchain / Web3',430),
(1,'Energy',440),(1,'Utilities',450),(1,'Renewable Energy',460),(1,'Agriculture / AgriTech',470),
(1,'Consumer Services',480),(1,'Professional Services',490),(1,'Marketing / AdTech',500),
(1,'Social Networking',510),(1,'Non-Profit / NGO',520),(1,'Technology',530),(1,'Other',999);

INSERT IGNORE INTO domain_industry_master (organization_id, name, display_order)
SELECT 1, TRIM(JSON_UNQUOTE(JSON_EXTRACT(notes, '$.domain'))), 900
FROM business_records
WHERE module='projects' AND type='fixedCost'
  AND JSON_VALID(notes) AND NULLIF(TRIM(JSON_UNQUOTE(JSON_EXTRACT(notes, '$.domain'))), '') IS NOT NULL;

CREATE TABLE fixed_cost_project_team_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_fixed_project_employee UNIQUE (project_id, employee_id),
    CONSTRAINT fk_fixed_project_team_project FOREIGN KEY (project_id) REFERENCES business_records(id) ON DELETE CASCADE,
    CONSTRAINT fk_fixed_project_team_employee FOREIGN KEY (employee_id) REFERENCES business_records(id)
);
