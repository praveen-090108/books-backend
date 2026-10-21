-- V50 preferred workingResource, causing an allocation to disappear from the
-- selected employee's project history when the two names differed.
UPDATE project_resource_allocation allocation
JOIN business_records project
  ON project.id = allocation.project_id
 AND project.module = 'projects'
 AND project.type = 'staffing'
JOIN business_records resource
  ON resource.module = 'resources'
 AND resource.type = 'resources'
 AND LOWER(resource.party_name) = LOWER(COALESCE(
       NULLIF(JSON_UNQUOTE(JSON_EXTRACT(project.notes, '$.selectedResource')), ''),
       NULLIF(JSON_UNQUOTE(JSON_EXTRACT(project.notes, '$.workingResource')), ''),
       project.owner_name))
SET allocation.resource_id = resource.id
WHERE allocation.project_type = 'STAFFING'
  AND JSON_VALID(project.notes)
  AND allocation.resource_id <> resource.id;
